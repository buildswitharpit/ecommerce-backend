# E-commerce Backend

A production-quality REST API for a small e-commerce backend, built with Spring Boot 3.
This is Project 6 in a series of portfolio projects (see `../../CONTEXT.md`) — the first
"advanced" one, and the first to need real authentication (Spring Security + JWT) and a
third-party payment integration (Stripe, behind a pluggable abstraction).

## What it does

- **JWT authentication** — register (always as `CUSTOMER`; there is no way to
  self-assign `ADMIN` through the public API), login (issues a short-lived access token
  + a long-lived, database-backed refresh token), refresh (rotates the refresh token —
  each one is single-use), and logout (revokes a specific refresh token). This gives
  real logout/revocation semantics instead of stateless-forever tokens: a revoked
  refresh token can never mint a new access token again, even though the access tokens
  it already produced remain valid until their own short expiry.
- **Product catalog** — admin-managed CRUD, public browsing/search (paginated, filter
  by `category`/`search`). Deleting a product is a **soft delete**
  (`active=false`) — past `OrderItem` rows reference a product for traceability and
  must never break. Stock uses JPA **optimistic locking** (`@Version`): a concurrent
  checkout/restock racing on the same product fails fast with a 409 asking the caller
  to retry, instead of silently corrupting stock.
- **Cart** — one per user, created lazily on first access. Add/update/remove line
  items, clear the whole cart. Always scoped to the caller's own JWT identity.
- **Checkout** — the core business logic (see the "Checkout flow" diagram below):
  validates every cart line (product still active, sufficient stock), creates the
  order, charges the active `PaymentGateway`, and only *after* a successful charge
  decrements stock (writing one `InventoryTransaction` audit row per product), marks
  the order `PAID`, and clears the cart. On a decline the order is marked
  `PAYMENT_FAILED`, stock is left completely untouched, and the cart survives so the
  customer can retry with a different payment method.
- **Order lifecycle / admin management** — admins transition orders through
  `PAID -> SHIPPED -> DELIVERED`, or cancel a `PAID`/`SHIPPED` order (which **restocks**
  every line item and writes an `ORDER_CANCELLED` audit row per product). Cancelling a
  `DELIVERED` order is rejected with 409 — see "Known limitations" below.
- **Inventory audit trail** — every stock change (checkout, cancellation restock, or a
  manual admin adjustment via `PATCH /api/products/{id}/stock`) writes an immutable
  `InventoryTransaction` row, so stock history is always reconstructable from the audit
  log, not just trusted from the current `stockQuantity` number.

Request validation (`jakarta.validation`) on all write endpoints, with a consistent
structured JSON error body for every failure case (validation, not-found, conflicts —
insufficient stock, optimistic-lock retry, duplicate email/sku, invalid status
transition — auth failures, forbidden role, malformed JSON, and a generic 500 fallback
— no stack traces or password hashes ever leak to the client).

Full OpenAPI/Swagger documentation for every endpoint, with an actually-usable "Authorize"
button in Swagger UI. Runs against H2 (in-memory, no setup) locally, or PostgreSQL via
Docker Compose.

## Auth model

Stateless JWT (HS256, signed via `io.jsonwebtoken`/jjwt), no server-side sessions:

- **Access token** — short-lived (15 min by default, `app.jwt.access-token-expiration-ms`),
  carries the user's id/email/role as claims, sent as `Authorization: Bearer <token>` on
  every protected request. Validated on each request by a `JwtAuthenticationFilter`
  (`OncePerRequestFilter`) that populates the Spring Security context — it never hits
  the database, so validation is fast and stateless.
- **Refresh token** — long-lived (7 days by default, `app.jwt.refresh-token-expiration-ms`),
  an opaque random value (*not* a JWT) persisted server-side as a `RefreshToken` row
  storing only a SHA-256 hash of it (mirroring how `User.passwordHash` never stores a
  plaintext password). This is what makes real revocation possible: a JWT can't be
  "un-signed" before its own expiry, but a database row can be flagged `revoked` at any
  time. Every refresh rotates the token (the old one is revoked, a new one issued), so a
  stolen-and-replayed old refresh token stops working the instant the legitimate client
  refreshes.
- **Route rules** (`SecurityConfig`): `/api/auth/register|login|refresh`,
  `GET /api/products/**`, Swagger UI/OpenAPI docs, and `/actuator/health` are public;
  `POST /api/auth/logout`, `/api/cart/**`, and `/api/orders/**` require any authenticated
  user; product writes/stock adjustment, `/api/admin/**`, and
  `PATCH /api/orders/{id}/status` require the `ADMIN` role. A missing/invalid token on a
  protected route returns 401; an authenticated-but-wrong-role caller gets 403 — both
  via the same structured `ErrorResponse` body used everywhere else in the API.

**The committed JWT secret is a placeholder for local/demo use only.** Override it with
a freshly generated secret (env var `APP_JWT_SECRET`) for any real deployment — see
`application.yml` for the exact default value and where it's used.

## Payment gateway design — pluggable, mock by default

Checkout charges through a `PaymentGateway` interface with two implementations, wired
by Spring `@ConditionalOnProperty`. Both are shown here because the mock/real split is
the point of the design, not something to hide:

| | `MockPaymentGateway` (default) | `StripePaymentGateway` |
|---|---|---|
| Activated by | `payment.gateway=mock` or unset (`matchIfMissing = true`) | `payment.gateway=stripe` |
| External dependency | None | Real Stripe test-mode API call (`com.stripe:stripe-java`) |
| Behavior | Any `paymentMethodToken` succeeds and returns a fake reference id (`"mock_" + UUID`), **except** the literal string `tok_chargeDeclined`, which simulates a decline | Creates a real Stripe `Charge` via the classic Charges API, passing `paymentMethodToken` through as the Stripe `source` token |
| Used in this project's own Docker/verification run | **Yes — exclusively** | No (no Stripe account/credentials available in that environment) |

`tok_chargeDeclined` deliberately mirrors Stripe's own test-mode magic decline tokens
(Stripe ships `tok_chargeDeclined`, `4000000000000002`, etc. for exercising decline
paths in test mode) — the abstraction reads naturally and the exact same
`paymentMethodToken` value exercises the decline path identically against either
gateway.

`StripePaymentGateway` is written completely and correctly — a real Stripe integration,
not a stub — but is not exercised live in this project's verification, since doing so
would require a real Stripe test-mode account. It compiles, is wired correctly, and is
simply never the active bean unless explicitly configured. **To switch to live Stripe
test-mode charges:**

```bash
PAYMENT_GATEWAY=stripe
STRIPE_API_KEY=sk_test_...   # a real Stripe secret test key
```

Both `OrderServiceImpl` and the checkout flow are completely unaware of which gateway is
active — they depend only on the `PaymentGateway` interface.

## Known limitations

- **Checkout charges before decrementing stock** (matching the spec for this project):
  if a rare optimistic-lock stock conflict occurs *after* a successful charge, the
  whole checkout transaction (including the order and payment rows) rolls back. This is
  safe with the mock gateway (no real money moves), but a production integration with a
  real gateway would need a compensating refund in that branch — out of scope here.
- **No returns/refund flow.** Cancelling a `DELIVERED` order is rejected with 409 by
  design; once an order is delivered, this API has no mechanism to reverse it.
- **Logout revokes one specific refresh token**, not every session for the account —
  the caller must pass the exact refresh token to revoke in the request body. This
  models per-device/per-session logout rather than a global "sign out everywhere."

## Tech stack

- Java 21, Spring Boot 3.5.16
- Spring Web, Spring Data JPA, Spring Security, Bean Validation
- `io.jsonwebtoken` (jjwt) 0.12.6 for JWT signing/validation (HS256)
- `com.stripe:stripe-java` for the real (non-default) Stripe integration
- H2 (default/local profile) / PostgreSQL 16 (`docker` profile)
- springdoc-openapi 2.8.17 (Swagger UI / OpenAPI 3), with a Bearer `SecurityScheme` so
  Swagger UI's "Authorize" button actually works
- JUnit 5 + Mockito (service-layer unit tests), Spring Boot Test + MockMvc (full-stack
  integration tests using real JWTs obtained from the actual login endpoint)
- Docker multi-stage build + Docker Compose (app + Postgres, healthcheck-gated startup)

No Lombok: getters/setters/constructors/builders are hand-written, following the same
approach as every sibling project in this series (see their READMEs for the
annotation-processor incompatibility that motivated this, which reproduces identically
on this project's toolchain).

## Project structure

```
backend/                           this directory -- a standalone Maven project
├── Dockerfile                     multi-stage build (Maven -> JRE runtime)
├── pom.xml
├── src/main/java/com/ecommerce/
│   ├── EcommerceBackendApplication.java
│   ├── config/
│   │   ├── OpenApiConfig.java                  Swagger/OpenAPI metadata + bearerAuth scheme
│   │   └── SecurityConfig.java                 route rules, PasswordEncoder, filter chain
│   ├── security/
│   │   ├── JwtService.java                     sign/validate access tokens (jjwt, HS256)
│   │   ├── JwtAuthenticationFilter.java         OncePerRequestFilter: Bearer -> SecurityContext
│   │   ├── UserPrincipal.java                   authenticated-caller record (id/email/role)
│   │   ├── TokenHasher.java                     opaque refresh-token generation + SHA-256 hashing
│   │   ├── RestAuthenticationEntryPoint.java     401 JSON body (missing/invalid token)
│   │   └── RestAccessDeniedHandler.java          403 JSON body (wrong role)
│   ├── controller/
│   │   ├── AuthController.java                  /api/auth (register/login/refresh/logout)
│   │   ├── ProductController.java                /api/products
│   │   ├── CartController.java                   /api/cart
│   │   ├── OrderController.java                  /api/orders (checkout, list, get, status)
│   │   └── AdminOrderController.java             /api/admin/orders (cross-customer listing)
│   ├── service/ + service/impl/                 business logic interfaces + implementations
│   │                                             (OrderServiceImpl.checkout() is the core
│   │                                              business logic of this project)
│   ├── payment/
│   │   ├── PaymentGateway.java                  the abstraction OrderServiceImpl depends on
│   │   ├── PaymentResult.java                   success/failure + gateway reference id
│   │   ├── MockPaymentGateway.java               default: no external dependency
│   │   └── StripePaymentGateway.java             real Stripe integration (opt-in)
│   ├── repository/
│   │   ├── UserRepository / RefreshTokenRepository / ProductRepository / CartRepository /
│   │   │   CartItemRepository / OrderRepository / PaymentRepository / InventoryTransactionRepository
│   │   └── spec/ProductSpecifications.java      composable filters (category/search/active)
│   ├── entity/                                  JPA entities (never exposed over the wire)
│   │   ├── User / Role / RefreshToken
│   │   ├── Product (optimistic-locked via @Version)
│   │   ├── Cart / CartItem
│   │   ├── Order / OrderItem / OrderStatus
│   │   ├── Payment / PaymentStatus
│   │   └── InventoryTransaction / InventoryReason
│   ├── dto/request/                             validated request DTOs
│   ├── dto/response/                             response DTOs (incl. ErrorResponse) — never
│   │                                             include passwordHash
│   ├── mapper/                                  explicit entity <-> DTO mapping
│   └── exception/                               NotFoundException/ConflictException/
│                                                 UnauthorizedException hierarchy +
│                                                 @RestControllerAdvice
├── src/main/resources/
│   ├── application.yml                          default profile: H2 in-memory, mock gateway
│   └── application-docker.yml                   docker profile: PostgreSQL, env-driven overrides
└── src/test/
    ├── java/.../service/*Test.java               Mockito unit tests: auth, product CRUD +
    │                                              soft delete, cart, and the checkout flow
    │                                              (success, insufficient stock, decline,
    │                                              optimistic-lock conflict) + cancel-with-restock
    └── java/.../controller/*IntegrationTest.java  MockMvc + real H2, real JWTs from the actual
                                                   login endpoint: role enforcement (401/403/
                                                   allowed), the full register->login->create
                                                   -product(ADMIN)->add-to-cart->checkout->
                                                   verify-stock happy path, and the decline path
```

## Code flow diagrams

### 1. Register -> login -> JWT-validated request

```mermaid
sequenceDiagram
    participant Main as EcommerceBackendApplication.main()
    participant Spring as Spring Boot (ApplicationContext)
    participant DS as DispatcherServlet
    participant Filter as JwtAuthenticationFilter
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant JwtSvc as JwtService
    participant UserRepo as UserRepository
    participant RTRepo as RefreshTokenRepository
    participant CartCtrl as CartController
    participant DB as Database (H2 / PostgreSQL)

    Main->>Spring: SpringApplication.run(...)
    Spring->>Spring: component scan com.ecommerce.*<br/>build SecurityFilterChain (SecurityConfig),<br/>register JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
    Spring-->>Main: application context ready, embedded Tomcat listening on :8080

    rect rgb(235, 245, 255)
    note over AuthCtrl,DB: POST /api/auth/register (public)
    DS->>AuthCtrl: register(RegisterRequest)
    AuthCtrl->>AuthSvc: register(request)
    AuthSvc->>UserRepo: existsByEmail(email)
    UserRepo->>DB: SELECT ...
    alt email already registered
        DB-->>AuthSvc: true
        AuthSvc-->>AuthCtrl: throws DuplicateEmailException
        AuthCtrl-->>DS: GlobalExceptionHandler -> 409 + ErrorResponse
    else email free
        AuthSvc->>AuthSvc: passwordEncoder.encode(password)<br/>role hardcoded to CUSTOMER (never from request)
        AuthSvc->>UserRepo: save(user)
        UserRepo->>DB: INSERT INTO users ...
        AuthSvc-->>AuthCtrl: UserResponse (no passwordHash)
        AuthCtrl-->>DS: 201 Created
    end
    end

    rect rgb(235, 250, 235)
    note over AuthCtrl,DB: POST /api/auth/login (public)
    DS->>AuthCtrl: login(LoginRequest)
    AuthCtrl->>AuthSvc: login(request)
    AuthSvc->>UserRepo: findByEmail(email)
    UserRepo->>DB: SELECT ...
    alt user not found OR passwordEncoder.matches() fails
        AuthSvc-->>AuthCtrl: throws UnauthorizedException
        AuthCtrl-->>DS: GlobalExceptionHandler -> 401 + ErrorResponse
    else credentials valid
        AuthSvc->>JwtSvc: generateAccessToken(user)
        JwtSvc-->>AuthSvc: signed JWT (HS256, 15 min expiry)
        AuthSvc->>AuthSvc: TokenHasher.newOpaqueToken() + sha256(...)
        AuthSvc->>RTRepo: save(RefreshToken{tokenHash, expiresAt, revoked=false})
        RTRepo->>DB: INSERT INTO refresh_tokens ...
        AuthSvc-->>AuthCtrl: AuthResponse(accessToken, refreshToken, "Bearer", expiresIn)
        AuthCtrl-->>DS: 200 OK
    end
    end

    rect rgb(255, 245, 235)
    note over Filter,CartCtrl: GET /api/cart, header "Authorization: Bearer <accessToken>"
    DS->>Filter: doFilterInternal(request)
    Filter->>JwtSvc: parseAndValidate(token)
    alt token missing / invalid / expired
        JwtSvc-->>Filter: null
        Filter->>DS: continue filter chain (SecurityContext stays empty)
        DS-->>DS: route is protected -> Spring Security rejects
        DS-->>CartCtrl: (never reached)
        note right of DS: RestAuthenticationEntryPoint -> 401 + ErrorResponse
    else token valid
        JwtSvc-->>Filter: UserPrincipal(id, email, role)
        Filter->>Filter: SecurityContextHolder.setAuthentication(<br/>UserPrincipal, authorities=[ROLE_&lt;role&gt;])
        Filter->>DS: continue filter chain
        DS->>CartCtrl: getCart(@AuthenticationPrincipal UserPrincipal)
        CartCtrl-->>DS: 200 OK + CartResponse (scoped to principal.id())
    end
    end
```

### 2. Checkout flow

```mermaid
sequenceDiagram
    participant Client
    participant OrderCtrl as OrderController
    participant OrderSvc as OrderServiceImpl
    participant CartSvc as CartServiceImpl
    participant ProdRepo as ProductRepository
    participant OrderRepo as OrderRepository
    participant Gateway as PaymentGateway<br/>(MockPaymentGateway by default)
    participant PayRepo as PaymentRepository
    participant InvRepo as InventoryTransactionRepository
    participant DB as Database

    Client->>OrderCtrl: POST /api/orders/checkout<br/>{paymentMethodToken}
    OrderCtrl->>OrderSvc: checkout(userId, request)
    OrderSvc->>CartSvc: getOrCreateCartEntity(userId)
    CartSvc-->>OrderSvc: Cart (with items + Product refs)

    alt cart is empty
        OrderSvc-->>OrderCtrl: throws ConflictException
        OrderCtrl-->>Client: 409 + ErrorResponse
    else cart has items
        loop each cart item
            OrderSvc->>OrderSvc: check product.active && stockQuantity >= quantity
        end
        alt any item fails validation
            OrderSvc-->>OrderCtrl: throws InsufficientStockException / ConflictException
            OrderCtrl-->>Client: 409 + ErrorResponse (nothing charged or created)
        else every item valid
            OrderSvc->>OrderSvc: compute totalAmount, build Order (PENDING) + OrderItem snapshots
            OrderSvc->>OrderRepo: save(order)
            OrderRepo->>DB: INSERT INTO orders / order_items ...

            OrderSvc->>Gateway: charge(totalAmount, paymentMethodToken)

            alt paymentMethodToken == "tok_chargeDeclined" (mock) / real decline (Stripe)
                Gateway-->>OrderSvc: PaymentResult.failure(...)
                OrderSvc->>OrderSvc: order.status = PAYMENT_FAILED
                note right of OrderSvc: stock NOT touched, cart left intact
                OrderSvc->>OrderRepo: save(order)
                OrderSvc->>PayRepo: save(Payment{status=FAILED})
                OrderRepo->>DB: UPDATE orders ...
                PayRepo->>DB: INSERT INTO payments ...
                OrderSvc-->>OrderCtrl: OrderResponse (status=PAYMENT_FAILED)
                OrderCtrl-->>Client: 200 OK
            else charge succeeds
                Gateway-->>OrderSvc: PaymentResult.success(gatewayReferenceId)
                loop each order item
                    OrderSvc->>ProdRepo: product.stockQuantity -= quantity;<br/>saveAndFlush(product)
                    alt concurrent update raced (stale @Version)
                        ProdRepo-->>OrderSvc: throws OptimisticLockingFailureException
                        OrderSvc-->>OrderCtrl: throws ConflictException<br/>(whole transaction rolls back)
                        OrderCtrl-->>Client: 409 + ErrorResponse ("please retry")
                    else stock decrement succeeds
                        ProdRepo->>DB: UPDATE products SET stock_quantity = ?, version = version + 1
                        OrderSvc->>InvRepo: save(InventoryTransaction{ORDER_PLACED, -quantity})
                        InvRepo->>DB: INSERT INTO inventory_transactions ...
                    end
                end
                OrderSvc->>OrderSvc: order.status = PAID
                OrderSvc->>CartSvc: clearCartEntity(cart)
                CartSvc->>DB: DELETE FROM cart_items WHERE cart_id = ?
                OrderSvc->>OrderRepo: save(order)
                OrderSvc->>PayRepo: save(Payment{status=SUCCEEDED})
                OrderRepo->>DB: UPDATE orders ...
                PayRepo->>DB: INSERT INTO payments ...
                OrderSvc-->>OrderCtrl: OrderResponse (status=PAID)
                OrderCtrl-->>Client: 200 OK
            end
        end
    end
```

## Running locally (no Docker, H2 in-memory)

This directory (`backend/`) is a standalone Maven project — from inside it:

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with an in-memory H2 database (data is wiped
on restart) and the mock payment gateway. The H2 console is available at
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:ecommercedb`, user `sa`,
empty password).

## Running via Docker Compose (PostgreSQL)

The `docker-compose.yml` that builds this service lives at the **repo root**, alongside
the frontend's, since one compose file runs the full stack (db + backend + frontend).
From the repo root:

```bash
docker compose up --build -d
```

This starts three containers: `ecommerce-db` (Postgres 16), `ecommerce-backend` (this
service, waiting for the database to report healthy before starting), and
`ecommerce-frontend`. The mock payment gateway is used by default
(`PAYMENT_GATEWAY=mock` in `docker-compose.yml`) — no Stripe account or credentials are
required to run the full stack. The API alone is reachable at `http://localhost:8080`;
see the [repo-root README](../README.md) for the full stack (including the frontend at
`http://localhost:5173`).

```bash
docker compose logs -f backend   # tail backend logs
docker compose down               # stop and remove containers (add -v to also drop volumes)
```

To switch the `backend` service to real Stripe test-mode charges, set
`PAYMENT_GATEWAY=stripe` and `STRIPE_API_KEY=sk_test_...` in `docker-compose.yml` (or
override at `docker compose up` time) before starting the stack.

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Click "Authorize" in Swagger UI and paste an `accessToken` from `POST /api/auth/login`
(no `Bearer ` prefix needed — Swagger UI adds it) to try out protected endpoints
directly from the docs. Every operation's description explicitly notes whether it's
public, requires any authenticated user, or requires `ADMIN`, and the checkout
operation documents the `tok_chargeDeclined` mock decline token.

## Example requests

Assumes the app is running (locally or via Docker Compose) at `http://localhost:8080`.

### Register

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jane.doe@example.com","password":"SecurePass123!","fullName":"Jane Doe"}'
```

```json
{"id":1,"email":"jane.doe@example.com","fullName":"Jane Doe","role":"CUSTOMER","createdAt":"2026-07-21T10:15:30"}
```

### Login (capture the access token)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jane.doe@example.com","password":"SecurePass123!"}' | jq -r .accessToken)
```

### Unauthenticated cart access -> 401

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/cart
# 401
```

### Create a product as ADMIN

There's no public "become admin" endpoint by design (registration always creates a
`CUSTOMER`); seed an admin user directly (e.g. via the H2 console, or a one-off insert
against Postgres) for local testing. Once you have an admin's `accessToken`:

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"sku":"SKU-WIDGET-001","name":"Deluxe Widget","description":"A widget, but deluxe.","price":29.99,"stockQuantity":100,"category":"Widgets"}'
```

```json
{"id":1,"sku":"SKU-WIDGET-001","name":"Deluxe Widget","description":"A widget, but deluxe.","price":29.99,"stockQuantity":100,"category":"Widgets","active":true}
```

### The same request as a CUSTOMER -> 403

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"sku":"SKU-X","name":"X","price":1.00,"stockQuantity":1}'
# 403
```

### Add to cart

```bash
curl -s -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":2}'
```

### Checkout successfully -> stock decrements

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"paymentMethodToken":"tok_visa"}'
```

```json
{"id":1,"status":"PAID","totalAmount":59.98,"items":[...],"payment":{"gateway":"MOCK","status":"SUCCEEDED", ...}}
```

`GET /api/products/1` now shows `stockQuantity: 98` (100 - 2).

### Checkout with the mock decline token -> PAYMENT_FAILED, stock untouched

```bash
curl -s -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":3}'

curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"paymentMethodToken":"tok_chargeDeclined"}'
```

```json
{"id":2,"status":"PAYMENT_FAILED","totalAmount":89.97,"items":[...],"payment":{"gateway":"MOCK","status":"FAILED", ...}}
```

`stockQuantity` is still `98` (unchanged), and the cart still has the 3 items — retry
with a different token to complete the purchase.

### Admin cancels a paid order -> restocks

```bash
curl -s -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"status":"CANCELLED"}'
```

```json
{"id":1,"status":"CANCELLED","totalAmount":59.98, ...}
```

`stockQuantity` goes back up by the cancelled order's quantity (98 -> 100 for the
2-unit order above), and an `ORDER_CANCELLED` `InventoryTransaction` row is written per
line item.

## Testing

```bash
mvn clean test
```

Runs the full suite: Mockito unit tests for `AuthServiceImpl` (register/login/refresh/
logout, bad credentials, token rotation), `ProductServiceImpl` (CRUD, soft delete,
active-vs-admin visibility, stock adjustment incl. optimistic-lock conflict),
`CartServiceImpl` (add/update/remove/clear, quantity-merge-on-add), and — the most
important — `OrderServiceImpl` (checkout success with stock decrement + audit row,
insufficient-stock 409, the decline path with stock/cart left untouched, an
optimistic-lock conflict mid-checkout, and cancel-with-restock vs. the rejected
cancel-a-delivered-order transition); plus full-stack `@SpringBootTest` + MockMvc
integration tests against real in-memory H2 using real JWTs obtained from the actual
`/api/auth/login` endpoint, covering role enforcement (401 vs. 403 vs. allowed), the
complete register -> login -> create-product(ADMIN) -> add-to-cart(customer) ->
checkout -> verify-stock-decremented happy path, the decline path via
`tok_chargeDeclined`, admin order cancellation with restock, and the 404-not-403 privacy
behavior for viewing another customer's order.

## License

MIT — see [LICENSE](../LICENSE).
