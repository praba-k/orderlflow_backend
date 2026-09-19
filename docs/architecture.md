# Engineering decisions

## Modular monolith

**Why:** checkout touches customers, product availability, inventory, coupons, orders, and payments. A single process and relational transaction make these invariants straightforward to reason about.

**Problem solved:** avoids distributed consistency and deployment complexity before there is a business need.

**Trade-off:** modules share one database and release lifecycle. Boundaries are Java packages and explicit service calls, not separate deployable services. Read reporting uses SQL joins intentionally.

## Concurrency and transaction boundaries

### Checkout lock order

1. Customer row (serializes cart changes, account deactivation, and idempotency for that customer).
2. Product rows in PostgreSQL UUID ascending order.
3. Coupon row when applicable.
4. Inventory rows, while their product locks are already held.

Product locks also serialize price/deactivation changes with purchase snapshots. Inventory row creation is safe because its parent product is locked first. Database constraints provide a final safety net.

**Trade-off:** a popular product or coupon becomes a serialization point. This prioritizes correctness. Do not replace these locks with in-memory synchronization; that would fail across replicas.

### Cancellation and settlement

Order row is locked before payment/inventory work. Items are processed in the same database product-ID order. Customer checks do not acquire customer write locks in this path, avoiding an order/customer lock inversion.

- Pending cancellation releases reserved inventory.
- Confirmed cancellation restocks previously consumed inventory.
- Payment success consumes reservations exactly once.
- A terminal state cannot be advanced again.
- A late success after expiry is voided/refunded, never fulfilled.

JPA version columns detect stale writes as a second defence. Lock/optimistic conflicts return 409, allowing callers to retry the same key. Do not turn arbitrary failures into success.

### Transactional notifications

An in-process event inserts the durable notification within the same transaction as the order/payment change. The inbox row is the simulated delivery. Uniqueness on (order,event) prevents duplicates.

**Why:** listeners that run only after commit and directly send email can lose notifications if the process crashes between commit and send.

**Trade-off:** the current inbox has no external delivery state. Add an outbox publisher with delivery attempts, leases, and event-ID deduplication when attaching a broker/email provider. The business event producer can remain unchanged.

## Payment boundary and failure recovery

Checkout creates a payment row in the order transaction. The scheduled worker reads due work, calls the provider without holding application transaction locks, then settles in a new transaction.

The provider contract requires:

- Stable payment UUID as charge idempotency key.
- A durable record of the result across retries/restarts.
- Idempotent void/refund.
- A cancellation fence: a voided operation can never later be charged.

The mock implements these with its own ledger and independent transaction. The ledger deliberately has no FK to application payments: it models an external system, not a transactionally coupled payment table.

| Failure point | Recovery |
| --- | --- |
| Before checkout commit | Everything rolls back; retry can create the order |
| After commit, before provider | INITIATED row remains due |
| After provider capture, before local settlement | Retry sees the same provider result, no new charge |
| Timeout / unknown result | Keep INITIATED and reserved stock; backoff, then expire |
| Explicit decline | FAILED payment, cancelled order, released reservation |
| Cancellation before provider | REFUND_PENDING work voids and fences the payment |
| Cancellation after capture | Same work refunds; no duplicate stock release |
| Provider unavailable during refund | Persist retry metadata; never mark refund complete prematurely |

States are INITIATED, SUCCESS, FAILED, REFUND_PENDING, REFUNDED. A voided uncharged payment ends FAILED with reason VOIDED. This distinguishes it from a refund of actual captured money.

**Trade-off:** the order response is asynchronous and clients must poll. A future real provider with different cancellation semantics needs an adapter and possibly reconciliation workflows, not a claim of magical exactly-once remote execution.

## Idempotency

Idempotency records live in PostgreSQL and commit atomically with the order. A customer row lock serializes simultaneous requests for the same account; a database unique constraint independently protects (customer,key).

The SHA-256 fingerprint covers the normalized address UUID, coupon code and mock scenario. The cart is server-side state, not part of the request body. Replaying an existing key deliberately does not inspect the current cart. The original response is stored and replayed; current state comes from GET.

**Trade-off:** all checkouts/cart mutations for one customer serialize, and responses take storage. This avoids Redis expiry/eviction causing duplicate financial operations. Keys currently have no automatic retention expiry.

## Pricing and discount policy

BigDecimal values are validated and persisted as NUMERIC. Tax is calculated on the discounted subtotal and rounded once to two decimals. Tax rate, totals, address and item prices are snapshots.

Coupon usage is reserved/consumed at accepted checkout, under a coupon lock. Cancellation does not restore eligibility. A checkout rollback does restore usage because all changes share the transaction.

**Trade-off:** this is a simple explicit retail policy, not every merchant's policy. Refundable coupon allocation would need a separate reservation/release model and tests.

## Security

- BCrypt cost 12; registration accepts 12–72 characters and enforces BCrypt's 72-byte UTF-8 bound.
- HS256 JWT signatures and issuer/expiry validation use Spring Security/Nimbus.
- Active account and role are fetched from PostgreSQL on every authenticated request.
- Public registration always assigns CUSTOMER.
- Method-level role guards protect administrative controllers; services enforce ownership.
- Bearer-token APIs have no server sessions. CSRF is disabled because credentials are not automatically sent as cookies.
- Generated correlation IDs avoid accepting arbitrary log text from callers.
- Parameterized SQL is used for reports, notifications, and the mock ledger.

**Trade-off:** an account lookup costs a database query but immediately applies role changes and deactivation. No token blacklist or Redis session state is needed. JWT key rotation and full identity recovery are explicitly future work.

## Database and indexes

PostgreSQL is authoritative. Migrations use constraints rather than relying only on annotation validation.

| Index / constraint | Query or invariant |
| --- | --- |
| customer email, product SKU, category name unique | Fast identity lookup and uniqueness |
| addresses(customer_id) | Customer address collection |
| products(category_id,status) | Active category browsing |
| inventory primary key(product_id) | Constant-key row lock and stock lookup |
| inventory_transactions(product_id,created_at DESC) | Paginated product audit trail |
| carts(customer_id) unique | One cart per customer |
| cart_items(cart_id,product_id) unique | No duplicate cart line; cart lookup |
| orders(customer_id,created_at DESC) | Customer order history |
| orders(created_at) | Date-range reports |
| pending_expiry_idx partial | Reservation expiry scan without terminal orders |
| order_items(order_id,product_id) unique | Order lines and deterministic inventory work |
| order_status_history(order_id,created_at) | Status timeline |
| coupon_usage(coupon_id,customer_id) | Per-customer usage counting |
| coupon_usage(order_id) unique | One coupon redemption per order |
| payments(order_id) unique | Exactly one logical payment per order |
| payments_due_idx partial | Pending/retry/refund work only |
| idempotency_records(customer_id,request_key) unique | Atomic deduplication |
| notifications(order_id,event_type) unique | Duplicate event suppression |
| notifications(customer_id,created_at DESC) | Customer inbox |

Coupon usage's order FK is deferred until commit so pricing can validate/redeem the coupon before saving the fully calculated order. Other FKs remain immediate. No broad text-search index is added without measurement of catalog workload.

## Reporting definitions

Dates are UTC instants, from inclusive and to exclusive, maximum 366 days. Revenue is the total of orders created in the range whose payment is currently SUCCESS and whose order is not cancelled. It includes tax, and the response separately reports tax and discounts.

**Trade-off:** this is a current-state sales report grouped by order creation date, not an immutable accounting cash-flow ledger. Refunds change the historical net report. A finance-grade payment-event ledger is a separate future requirement.

Top-product sales are gross item value before allocated order discounts/tax. Low stock is a bounded current snapshot, not a historical stock reconstruction. Failed payments include voided uncaptured payments and identify their reason.

## Redis and operational dependencies

Only authentication rate limiting uses Redis. Its atomic fixed window is simple to audit and shared across replicas. It is optional locally; when enabled it fails closed for auth and contributes to readiness.

The application emits structured logs, Micrometer metrics, and separate liveness/readiness probes. Migration permissions currently belong to the application database user; a production release should split a migration role from the runtime DML role.

Database connections use a five-second lock timeout and a thirty-second statement timeout. These bounds prevent a blocked writer from occupying a request thread indefinitely; large future reports may need separately managed limits.

## Dependency references

The project stays on the requested Spring Boot 3.x line. See the [Spring Boot 3.5 reference](https://docs.spring.io/spring-boot/3.5/reference/index.html) and [springdoc's Boot compatibility matrix](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot). Reassess patch versions and support policy before a real production release.
