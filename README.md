# Ecommerce Backend + Frontend

A full-stack e-commerce demo: a production-quality Spring Boot REST API (JWT auth,
product catalog, cart, checkout with inventory management and a pluggable mock/Stripe
payment gateway) paired with a new React 19 + TypeScript frontend that provides a
complete UI for every endpoint the backend exposes.

## Project overview

This started as a standalone Spring Boot backend (see `backend/README.md` for the full
write-up of its design, auth model, and business logic) and was restructured into a
monorepo containing:

- **`backend/`** — the original Spring Boot API, unchanged in behavior, just relocated.
- **`frontend/`** — a brand-new React 19 SPA that consumes the backend's REST API:
  auth (register/login/refresh/logout), product browsing + admin catalog management,
  cart, checkout, order history, and admin order management.
- **`dummy-data/`** — realistic, reusable JSON fixtures for every entity, plus a seed
  script that populates a running backend through its public API.
- **`docker-compose.yml`** — builds and runs the whole stack (Postgres + backend +
  frontend) with one command.

Every existing backend API is untouched — the frontend was built to consume it as-is,
and the restructuring only moved files and updated build/Docker paths.

### Folder structure

```
.
├── backend/              Spring Boot 3 API (Java 21) -- see backend/README.md
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/              React 19 + TypeScript + Vite SPA
│   ├── src/
│   │   ├── api/           axios client + one module per resource (auth/products/cart/orders)
│   │   ├── components/    shared UI (shadcn primitives in ui/, plus app components)
│   │   ├── context/        AuthContext (token storage, decoded-JWT user info)
│   │   ├── hooks/           TanStack Query hooks per resource
│   │   ├── layouts/         RootLayout (header/nav/footer)
│   │   ├── pages/            one folder per feature area (auth/products/cart/checkout/orders/admin)
│   │   ├── routes/           ProtectedRoute / AdminRoute guards
│   │   ├── types/             TypeScript mirrors of every backend DTO
│   │   └── utils/             formatting, JWT decoding, error extraction
│   ├── package.json
│   └── Dockerfile
├── dummy-data/            seed fixtures + seed.js (see dummy-data/README.md)
├── docker-compose.yml      db + backend + frontend, one `docker compose up --build`
└── README.md               this file
```

### Architecture overview

```
Browser
  │
  ▼
frontend (nginx, :80 in Docker / Vite dev server, :5173 locally)
  │  reverse-proxies /api/** to the backend -- the browser only ever talks to one
  │  origin, so no CORS configuration was needed on the Spring Boot side
  ▼
backend (Spring Boot, :8080)
  │  stateless JWT auth, REST API, business logic
  ▼
PostgreSQL (Docker) / H2 in-memory (local dev)
```

The frontend never calls the backend's origin directly — in dev, Vite's dev-server proxy
forwards `/api/*` to `http://localhost:8080`; in Docker, nginx does the same, proxying to
the `backend` service by its Compose network hostname. This means the backend's
`SecurityConfig` required zero changes to support a separate frontend.

### Technology stack

**Backend:** Java 21, Spring Boot 3.5, Spring Security (JWT via `jjwt`), Spring Data JPA,
Bean Validation, springdoc-openapi (Swagger UI), H2 / PostgreSQL 16, JUnit 5 + Mockito.
Full details in `backend/README.md`.

**Frontend:** React 19, TypeScript (strict mode), Vite 8, Tailwind CSS v4, shadcn/ui
(Radix primitives), React Router 7, TanStack Query 5, Axios, React Hook Form + Zod,
Lucide icons, Sonner (toasts).

## Installation

### Prerequisites

- Java 21+ and Maven (for running the backend outside Docker)
- Node.js 22+ and npm (for running the frontend outside Docker)
- Docker + Docker Compose (for running the full stack in containers)

### Clone and install dependencies

```bash
git clone <this-repo-url>
cd ecommerce-backend

# Backend: dependencies are resolved by Maven on first build/run, nothing to install upfront
# Frontend:
cd frontend && npm install && cd ..
```

### Environment variables

Nothing is required to run locally — every default is dev-safe (H2 in-memory, mock
payment gateway, a committed-but-clearly-labeled JWT secret). For anything beyond local
demo use, override these (see `backend/README.md` for the full list and rationale):

| Variable | Where | Purpose |
|---|---|---|
| `APP_JWT_SECRET` | backend | HS256 signing secret for access tokens |
| `PAYMENT_GATEWAY` | backend | `mock` (default) or `stripe` |
| `STRIPE_API_KEY` | backend | only read when `PAYMENT_GATEWAY=stripe` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | backend | Postgres connection (docker profile only) |
| `VITE_BACKEND_URL` | frontend (dev only) | where the Vite dev-server proxy forwards `/api` (default `http://localhost:8080`) |

## Running the Backend

From `backend/`:

```bash
# Development (H2 in-memory, auto-reset on restart)
mvn spring-boot:run

# Production-style (build a jar, run it)
mvn clean package
java -jar target/ecommerce-backend.jar
```

Runs on `http://localhost:8080`. Full details, auth model, and business-logic
walkthrough: [`backend/README.md`](backend/README.md).

## Running the Frontend

From `frontend/`:

```bash
# Development (hot reload, proxies /api to http://localhost:8080)
npm run dev

# Production build
npm run build      # outputs to frontend/dist/
npm run preview    # serve the production build locally for a sanity check
```

Dev server runs on `http://localhost:5173`. The dev server only proxies API calls — you
still need the backend running separately (see above) for the frontend to have anything
to talk to.

## Docker

From the repo root:

```bash
docker compose up --build         # build images and start db + backend + frontend
docker compose up --build -d      # same, detached
docker compose logs -f backend    # tail backend logs (or `frontend`, `db`)
docker compose down               # stop and remove containers (add -v to also drop the Postgres volume)
```

Once healthy:

- Frontend: `http://localhost:5173`
- Backend API directly: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (also reachable through the
  frontend's nginx proxy at `http://localhost:5173/swagger-ui.html`)

The `frontend` container waits on the `backend` container's healthcheck, which waits on
`db`'s, so a cold `docker compose up --build` brings everything up in the right order
with no manual waiting.

## Dummy Data

`dummy-data/` contains realistic JSON fixtures for every entity (users, products,
categories, carts, orders, inventory adjustments, payment tokens) and `seed.js`, a
zero-dependency Node script that seeds a running backend entirely through its public
REST API. Full instructions: [`dummy-data/README.md`](dummy-data/README.md).

Quick start (after `docker compose up --build -d`):

```bash
cd dummy-data
node seed.js
```

This registers demo users, promotes one to `ADMIN` (the only step that isn't pure REST —
there's no API endpoint that can assign that role, by design), creates a 15-product
catalog across 5 categories, populates a few carts, replays a successful and a declined
checkout, and applies a couple of manual stock adjustments.

## API Usage

- **Backend URL:** `http://localhost:8080` (direct) or `http://localhost:5173/api/*`
  (through the frontend's proxy — same requests, same responses)
- **Frontend URL:** `http://localhost:5173`
- **Swagger / OpenAPI:** `http://localhost:8080/swagger-ui.html`,
  raw spec at `http://localhost:8080/v3/api-docs`

### Authentication flow

1. `POST /api/auth/register` — always creates a `CUSTOMER` account (no public way to
   self-assign `ADMIN`).
2. `POST /api/auth/login` — returns a short-lived access token (15 min) and a long-lived
   refresh token (7 days).
3. The frontend stores both tokens in `localStorage` and attaches
   `Authorization: Bearer <accessToken>` to every request via an axios interceptor.
4. On a `401`, the same interceptor automatically calls `POST /api/auth/refresh` once,
   retries the original request with the new access token, and queues any other requests
   that failed concurrently so they don't each trigger their own refresh. If the refresh
   token itself is invalid/expired, the user is logged out client-side.
5. `POST /api/auth/logout` revokes the specific refresh token used — one session at a
   time, not "log out everywhere."

The frontend derives the logged-in user's id/email/role by decoding the access token's
JWT payload client-side (display purposes only — every request is still verified
server-side); `fullName` is only known right after registering in the same browser
session, since login doesn't return it, and falls back to showing the email otherwise.

### Testing instructions

```bash
# Backend: unit + integration tests (Mockito, MockMvc + real H2, real JWTs)
cd backend && mvn clean test

# Frontend: type-check + production build (no test suite yet -- see Development below)
cd frontend && npm run build
```

Manually exercising the full stack: bring up Docker Compose, run the seed script, then
open `http://localhost:5173` and log in as `admin@example.com` / `AdminPass123!` (admin)
or `jane.doe@example.com` / `SecurePass123!` (customer) — see `dummy-data/users/users.json`
for the full list.

## Development

### Available scripts

**Backend** (`backend/`): `mvn spring-boot:run`, `mvn clean test`, `mvn clean package`.

**Frontend** (`frontend/`): `npm run dev`, `npm run build`, `npm run preview`,
`npm run lint` (oxlint).

### Coding standards

- **Backend:** no Lombok (hand-written getters/setters — see `backend/README.md` for
  why), explicit DTO/entity mapping, `@RestControllerAdvice`-based structured error
  responses, soft-deletes and optimistic locking where correctness demands it.
- **Frontend:** TypeScript strict mode, functional components + hooks only, one
  TanStack Query hook module per backend resource, Zod schemas co-located with the forms
  that use them, no prop-drilling past `AuthContext` (everything else is server state via
  TanStack Query). Comments are reserved for non-obvious *why*, not restating *what*.

### Build commands

See "Running the Backend" / "Running the Frontend" / "Docker" above — those same
commands are what CI or a deploy pipeline would run.

### Deployment notes

- Override `APP_JWT_SECRET` with a freshly generated secret — the committed default is
  explicitly a local/demo placeholder.
- The frontend's Dockerfile builds static assets and serves them via nginx, which also
  reverse-proxies `/api/**` to the backend — deploying the two containers behind the
  same reverse proxy (or keeping nginx's proxy_pass pointed at wherever the backend
  actually runs) avoids ever needing CORS configuration on the backend.
- Swagger UI and `/v3/api-docs` are enabled by default (springdoc); disable them
  (`springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false`) for a
  production deployment if that surface shouldn't be public.
- To use real Stripe charges instead of the mock gateway, set `PAYMENT_GATEWAY=stripe`
  and a real `STRIPE_API_KEY` — see `backend/README.md`'s payment gateway section.

## License

MIT — see [LICENSE](LICENSE).
