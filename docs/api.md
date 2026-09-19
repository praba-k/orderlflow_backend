# API guide

Base path: /api. JSON requests and responses. UUID IDs. ISO-8601 UTC instants. Money uses decimal JSON numbers in INR.

Authenticate using `Authorization: Bearer <accessToken>`. Swagger documents request/response schemas at /v3/api-docs; the prod profile disables documentation endpoints.

## Endpoints

| Method | Path | Access / behaviour |
| --- | --- | --- |
| POST | /auth/register | Public; creates CUSTOMER, returns 201 token |
| POST | /auth/login | Public; returns token, 401 for bad/inactive account |
| GET / PUT | /customers/me | Own profile; PUT updates name |
| GET | /customers | Admin, paginated safe profiles |
| PATCH | /customers/{id}/active | Admin; body active boolean |
| GET / POST | /customers/me/addresses | Own addresses; creation returns 201 |
| PUT / DELETE | /customers/me/addresses/{id} | Own address only; deletion returns 204 |
| GET | /categories | Public category list |
| POST | /categories | Admin; name; 201 |
| GET | /products | Public, active only; search/category/minPrice/maxPrice/page/size/sort |
| GET | /products/{id} | Public product detail, including status |
| POST / PUT | /products, /products/{id} | Admin; creation 201 |
| DELETE | /products/{id} | Admin; deactivates, 204 |
| GET | /inventory/{productId} | Admin or inventory manager |
| POST | /inventory/{productId}/adjustments | Same roles; signed delta and mandatory reason |
| GET | /inventory/{productId}/history | Same roles; paginated audit |
| POST / GET | /cart | Customer; lazily creates own cart |
| POST | /cart/items | Customer; productId, quantity; adds to existing quantity |
| PUT | /cart/items/{productId} | Customer; quantity replaces existing quantity |
| DELETE | /cart/items/{productId} | Customer; removes line, 204 |
| DELETE | /cart | Customer; clears cart, 204 |
| POST | /orders | Customer; Idempotency-Key required; asynchronous checkout |
| GET | /orders | Own paginated order history |
| GET | /orders/all | Admin, all orders |
| GET | /orders/{id} | Owner or admin; items and status history |
| POST | /orders/{id}/cancel | Owner or admin; idempotent cancellation |
| POST | /orders/{id}/status | Admin; PROCESSING, SHIPPED, DELIVERED only |
| GET | /orders/{id}/payment | Owner or admin |
| POST | /orders/{id}/payment/retry | Owner or admin; schedules existing unresolved work, 202 |
| POST / DELETE | /coupons, /coupons/{id} | Admin; create / deactivate |
| GET | /notifications | Own paginated inbox |
| PATCH | /notifications/{id}/read | Own notification; 204 |
| GET | /reports/revenue | Admin; from/to |
| GET | /reports/orders | Admin; from/to; counts per status |
| GET | /reports/top-products | Admin; from/to/limit |
| GET | /reports/low-stock | Admin; threshold/limit |
| GET | /reports/failed-payments | Admin; from/to/limit |
| GET | /reports/cancelled-orders | Admin; from/to/limit |

Raw reserve/release operations are intentionally internal service operations bound to an order. Exposing arbitrary reservation mutations would permit releasing another order's stock or creating orphan reservations. Operational stock adjustments are exposed and audited.

Cart quantities are 1–1000 per product and at most 100 distinct products. Page size is capped at 100. Catalog sorting supports name, price, createdAt, id, e.g. `?sort=price,asc&size=20`. Report limits are 1–100.

## Error contract

```json
{
  "timestamp": "2026-09-17T16:00:00Z",
  "status": 409,
  "error": "INSUFFICIENT_INVENTORY",
  "message": "Requested quantity exceeds available inventory",
  "path": "/api/orders"
}
```

Common codes: VALIDATION_ERROR (400), UNAUTHORIZED/INVALID_CREDENTIALS (401), FORBIDDEN (403), PRODUCT_NOT_FOUND/ADDRESS_NOT_FOUND/ORDER_NOT_FOUND (404), EMAIL_EXISTS/DATA_CONFLICT/INSUFFICIENT_INVENTORY/INVALID_ORDER_STATE/IDEMPOTENCY_KEY_REUSED/CONCURRENT_CHANGE (409), INVALID_COUPON/EMPTY_CART (422), RATE_LIMITED (429), AUTH_LIMITER_UNAVAILABLE (503).

Payment decline is an asynchronous business outcome: checkout was accepted, so inspect payment status rather than expecting an HTTP error after the original checkout response. TIMEOUT remains INITIATED until resolved or expired. Retrying payment never allocates a new payment ID.

## Retry guidance

- Lost checkout response: resend exactly the same key and body.
- 409 CONCURRENT_CHANGE: retry with the same key after brief backoff.
- 409 IDEMPOTENCY_KEY_REUSED: fix the client; do not silently assign a new key.
- 422 coupon / empty cart: correct the request/cart; no checkout was committed.
- INITIATED payment: poll GET; optional POST retry reschedules the same work.
- REFUND_PENDING: cancellation is committed; money reversal is still being processed.


## Frontend support endpoints

The UI consumes these additional server capabilities; client calculations do not replace their business rules.

| Method and path | Access | Semantics |
| --- | --- | --- |
| GET /api/products/{id}/availability | Public | Available units and backend purchase eligibility |
| GET /api/manage/products | Admin / inventory manager | Paginated catalog including inactive products; search, status and sort |
| GET /api/checkout/quote?couponCode=... | Customer | Nonbinding current-cart quote; validates without redeeming coupon or reserving stock |
| PATCH /api/customers/me/addresses/{id}/default | Address owner | Serializes changes and enforces one default address per customer |
| GET /api/coupons | Admin | Paginated coupon list |
| PUT /api/coupons/{id} | Admin | Validated coupon edit; usage limit cannot fall below redemptions |
| PATCH /api/coupons/{id}/active | Admin | Body: {"active":true} |
| GET /api/orders/{id}/actions | Order owner / admin | Allowed cancellation and fulfilment actions derived from backend state |
| GET /api/orders/all?status=CONFIRMED&search=abc&page=0&size=12 | Admin | Partial order UUID and status filtering before pagination |

Frontend payment integration and order submission are paused by request. Existing backend payment endpoints are unchanged and remain covered by backend tests.
