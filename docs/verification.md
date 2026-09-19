# Verification record

Original backend verification: 17 September 2026. Current application verification: 18 September 2026 in the provided Windows workspace.
## Frontend completion and Java 25 verification — 18 September 2026

The project owner confirmed Java 25 as the backend target. The Maven target, backend Docker stages and current setup instructions now agree. Earlier Java 21 results below are retained as historical evidence.

- Java 25.0.3; Maven verify passed with **26 unit + 36 PostgreSQL integration tests = 62**, no failures/errors/skips.
- V5 default-address migration and the storefront support APIs passed integration checks. Admin order ID/status search is tested against PostgreSQL with role restrictions and pagination.
- Frontend production build, ESLint and Prettier checks passed.
- Frontend behavior suite: **13 tests**, including a lost-response inventory safeguard.
- Demo Chromium scenarios: **10 tests**, covering the storefront, account, admin, inventory and responsive navigation.
- Live Chromium scenarios: **2 tests** against the actual Spring Boot API and isolated PostgreSQL schema. Repeated against the production Vite bundle and Java 25 packaged application. Registration, addresses, cart, quote, reporting and inventory reads passed.
- Viewports checked: 320, 375, 768, 1024 and 1440 pixels. Desktop/mobile screenshots inspected; table overflow and keyboard-focus styling corrected.
- Payment integration remains deliberately on hold. Frontend checkout stops at review; it does not POST an order, collect payment details or invoke payment retries. Existing backend mock-payment tests remain intact.
- No Docker execution, cross-browser certification, load testing, real Redis verification or cloud deployment is claimed.

See [frontend setup and architecture](../orderflow_ui/README.md) for reproducible commands. For production-bundle live checks, build first and set UI_PRODUCTION_PREVIEW=true alongside the documented live-test environment variables.

Java 25 container tag names were checked against the Docker official image metadata for [Maven](https://github.com/docker-library/official-images/blob/master/library/maven) and [Eclipse Temurin](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin). Images were not built locally because Docker is unavailable.

## Original backend verification (17 September)

- Java 21, Maven 3.9.15, Spring Boot 3.5.16.
- PostgreSQL 17.6 running as an isolated workspace-local test instance.
- `mvn -B -ntp verify`: **BUILD SUCCESS**.
- **26 unit tests + 31 PostgreSQL integration tests = 57 tests**.
- Zero failures, zero errors, zero skipped tests.
- Flyway applied all four migrations to a fresh random schema; Hibernate schema validation passed.
- Executable Spring Boot jar packaged successfully.
- Java sources formatted consistently with Google Java Format.

## Incremental gates

| Phase | Verified scope | Result at that gate |
| --- | --- | --- |
| 1 | Customer/authentication/catalog compilation and validation tests | 4 passing unit tests |
| 2 | Inventory rules and cart integration into application | 8 passing unit tests |
| 3 | Pricing, coupons, state machine, order transactions | 20 passing unit tests |
| 4 | Payment abstraction, durable work, idempotency, notifications | 22 passing unit tests |
| 5 | Reports, optional Redis limiter, structured logging and health | 23 passing unit tests |
| 6 | Expanded unit/API/database/concurrency suite, packaging and documentation | 57 passing tests |

Phases 1–2 compiled with the installed JDK 25 targeting Java release 21. A workspace-local JDK 21 was then obtained, and Phase 3 onward, including the complete final suite and packaged runtime smoke test, ran on Java 21.

The first full database run caught an Instant-to-JDBC timestamp binding defect in reports. It was corrected and all report endpoints were subsequently exercised successfully.

## Database-backed business scenarios

- HTTP checkout through authentication, controller, services, repositories and PostgreSQL.
- Immutable historical price snapshots after catalog edits.
- Successful payment, decline, timeout retries and expiry.
- Duplicate checkout, conflicting key reuse and simultaneous duplicate requests.
- Two customers competing for the final inventory unit.
- Atomic global coupon usage limits, invalid and expired coupons.
- Full rollback when a second order item runs out of stock.
- Concurrent mock-provider calls produce one durable charge.
- Cancellation before charge fences delayed charge attempts.
- Recovery from capture before local settlement; cancellation refunds that capture.
- Confirmed cancellation restocks once even when repeated.
- Expired reservations receiving successful capture are refunded.
- Concurrent payment settlement and expiry preserve stock/money consistency.
- Fulfilment transitions and status/notification history.
- Ownership, customer/admin/inventory-manager role boundaries and immediate deactivation.
- Password hashing, login, JWT tamper rejection, duplicate email, and safe profile responses.
- Consistent validation and missing-header errors.
- Revenue/refund semantics and all reporting SQL.
- Health probes, OpenAPI generation and separate schemas for similarly named module DTOs.

Concurrency tests use separate executor threads, a start barrier and independent committed transactions. These tests exercise PostgreSQL row locks, not an in-memory database or a mocked inventory repository.

## Packaged application smoke test

The packaged jar was started with the **prod** profile, generated test-only secrets, and the isolated PostgreSQL database. Actual HTTP requests verified:

| Check | Result |
| --- | --- |
| GET /actuator/health/liveness | 200, UP |
| GET /actuator/health/readiness | 200, UP |
| GET /api/orders without authentication | 401 |
| GET /v3/api-docs in prod | 404 |

The temporary application and PostgreSQL processes were stopped after verification.

## Explicitly unverified here

- Docker image build/run: Docker is not installed in this environment.
- Testcontainers container startup: the same database integration suite used its external PostgreSQL mode here. Its default path starts a PostgreSQL Testcontainer and fails if Docker is unavailable.
- Live Redis server behavior: rate-limit threshold, disabled mode and dependency failure are unit tested; a real Redis run is still required.
- Load, soak, penetration, infrastructure, CI/CD, backup/restore and disaster recovery testing.

No claims of production deployment, AWS infrastructure, external email delivery or real payment processing are made.

## Reproduce

Use JDK 25 and run `mvn verify` with Docker available, or set TEST_DB_URL, TEST_DB_USERNAME and TEST_DB_PASSWORD for a dedicated PostgreSQL test instance as described in the README.

Maven writes detailed results to target/surefire-reports and target/failsafe-reports. Temporary downloaded tools and local verification logs are in ignored .tools/. No runtime test secrets belong in source control or the Docker build context.

