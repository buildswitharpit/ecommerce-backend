# Dummy / Test Data

Realistic, reusable fixtures for end-to-end testing of the ecommerce backend + frontend,
organized by entity:

```
dummy-data/
├── users/users.json                       6 accounts: 1 admin + 5 customers (incl. one with an empty cart)
├── products/products.json                 15 products across 5 categories, incl. out-of-stock,
│                                           low-stock, and missing-description/category edge cases
├── categories/categories.json              the category values used across products.json
│                                           (category is a free-text field, not a separate entity)
├── carts/carts.json                        sample cart contents per demo user
├── orders/orders.json                      checkout scenarios (a success + a decline) to replay
├── inventory/inventory-adjustments.json    sample admin stock adjustments
├── payments/payment-tokens.json            mock gateway tokens (success + the decline token)
└── seed.js                                 seeds a running backend from all of the above
```

## Seeding the database

The backend has no built-in data loader, so `seed.js` drives the seeding entirely through
the public REST API (register/login/create-product/add-to-cart/checkout/adjust-stock) --
no direct database writes except one: promoting the admin account, since there is no API
endpoint that can assign the `ADMIN` role (by design -- see the backend README).

1. Start the stack (either `mvn spring-boot:run` from `backend/` for local H2, or
   `docker compose up --build -d` from the repo root for Postgres).
2. Run the seed script:
   ```bash
   cd dummy-data
   node seed.js
   ```
3. If you started via Docker Compose, the script automatically promotes
   `admin@example.com` to `ADMIN` by running one `UPDATE users SET role='ADMIN' ...`
   through `docker compose exec db psql`. Everything downstream (product creation,
   inventory adjustments) depends on that step succeeding.
4. If you're running the local H2 profile instead, the script can't reach a `db`
   container -- it prints the equivalent `UPDATE` statement to run yourself via the H2
   console at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:ecommercedb`,
   user `sa`, empty password), then re-run `node seed.js` to pick up from there.

The script is idempotent-ish: registering an already-registered user or creating an
already-existing SKU both fail with `409`, which it logs and skips rather than treating
as fatal.

## Using the data without the seed script

Every JSON file is plain, dependency-free data -- read it directly in a test, a Postman/
Insomnia collection, or another script if you'd rather drive the API yourself. Field
names match the request bodies documented in Swagger UI (`/swagger-ui.html`) exactly,
e.g. `products/products.json` entries map 1:1 onto `ProductCreateRequest`.
