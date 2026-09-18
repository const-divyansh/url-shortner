# URL Shortener Service

A production-grade, highly scalable URL shortener service built with **Java 17** and **Spring Boot 3**. Designed for low-latency redirects, high availability, robust analytics, and multi-tier security.

---

## 🚀 Key Features & Architecture

* **PostgreSQL as the sole source of truth**: every redirect reads through to Postgres. A Redis read-through cache in front of it is designed but deliberately deferred — see [Phase 2 scope](./docs/requirements.md) and `docs/plan.md`.
* **Redis-backed rate limiting**: per-IP fixed-window limits on link creation and redirects (`RATELIMIT_*` env vars), fail-open if Redis is unavailable so an outage degrades to unlimited rather than an outage.
* **Asynchronous Analytics**: Click events (timestamps, referrers, user agents) are buffered in Redis and drained to Postgres asynchronously via Spring `ApplicationEvent` listeners, without adding latency to the redirect itself.
* **Security & SSRF Defense**: Input validation chain preventing SSRF (blocking localhost, private IP ranges, non-HTTP/HTTPS schemes) and IP privacy protection via salted cryptographic hashing — raw client IPs are never persisted.
* **Extensible Short-Code Generation**: Pluggable code generation strategy (Base62 random or sequential) with automated collision resolution, switchable at runtime.
* **API contract**: OpenAPI 3 spec generated from the live code — `/v3/api-docs` (JSON) and `/swagger-ui.html` (interactive UI), always in sync with the implementation.

---

## 🛠️ Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Runtime** | Java 17 |
| **Backend Framework** | Spring Boot 3.3.4 (Spring MVC, Data JPA, Validation, Actuator, WebSocket/STOMP) |
| **Primary Database** | PostgreSQL |
| **Caching & Buffering** | Redis (rate-limit counters, click-event buffer) |
| **Live Updates** | STOMP over WebSocket — dashboard click counts push in real time |
| **API Contract** | springdoc-openapi — OpenAPI 3 spec + Swagger UI generated from the live code |
| **Database Migrations** | `schema.sql` |
| **Backend Testing** | JUnit 5, Testcontainers (Postgres + Redis in integration tests) |
| **Frontend** | React 19 + TypeScript + Vite |
| **Frontend Testing** | Playwright (E2E), oxlint (lint) |
| **Build & Tooling** | Maven (backend), npm (frontend), Make (orchestration) |
| **Containerization** | Docker & Docker Compose |

---

## 📁 Repository Layout

A monorepo: the two deployables sit side by side, with shared documentation and a single
orchestration entry point at the root.

```text
.
├── backend/                         # Java 17 + Spring Boot 3 API
│   ├── src/main/java/com/urlshortener/
│   ├── src/main/resources/          # application.yml, schema.sql
│   ├── src/test/                    # unit tests
│   ├── .env.example
│   └── pom.xml
├── frontend/                        # React 19 + Vite + TypeScript
│   ├── src/
│   │   ├── components/              # the two screens
│   │   ├── api.ts                   # every endpoint path lives here
│   │   ├── config.ts                # every env-driven value lives here
│   │   ├── strings.ts               # every user-facing string lives here
│   │   └── types.ts                 # API response contracts
│   ├── .env.example
│   └── package.json
├── docs/
│   ├── requirements.md              # functional & non-functional requirements
│   ├── plan.md                      # living milestone tracker
│   └── decisions.md                 # architecture decision records
├── .github/copilot-instructions.md  # AI-assisted execution protocol
├── docker-compose.yml               # Postgres + Redis
├── Makefile                         # runs the whole stack
└── README.md
```

---

## 🚦 Getting Started

### Prerequisites

* **Java 17**, **Maven 3.8+**, **Node 20+**, **Docker & Docker Compose**

### Run everything

```bash
make setup    # install frontend deps, create .env files from the examples
make dev      # Postgres + Redis + API + frontend; Ctrl-C stops all of it
```

| | |
| :--- | :--- |
| API | http://localhost:8080 |
| Frontend | http://localhost:5173 |
| Health | http://localhost:8080/actuator/health |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |

`make help` lists every target. The useful ones:

```bash
make up          # only Postgres + Redis
make api         # only the API
make web         # only the frontend
make test        # backend suite + frontend typecheck/lint
make build       # API jar + frontend bundle
make psql        # shell into the dev database
make down        # stop containers, keep data
make reset       # stop containers AND delete data
make clean       # remove build output (backend target/, frontend dist/)
```

Starting completely from scratch (fresh database, fresh dependencies, no leftover
build output):

```bash
make reset    # stop containers, delete Postgres/Redis volumes
make clean    # remove backend target/ and frontend dist/
rm -rf frontend/node_modules backend/.env frontend/.env
make setup    # reinstall frontend deps, recreate .env files
make dev
```

### Configuration

Both components read their configuration from the environment; nothing is hardcoded.
Copy each `.env.example` to `.env` (done for you by `make setup`) and edit as needed.

> Frontend variables are prefixed `VITE_` and are compiled into the browser bundle,
> which makes them **public**. Never put a secret in `frontend/.env`.

---

## 📡 API Endpoints Overview

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/guest` | Continue as guest: mint a new anonymous session |
| `GET` | `/api/auth/google/start` | Begin "Sign in with Google" (redirects to Google) |
| `GET` | `/api/auth/google/callback` | Google OAuth callback (redirects back to the frontend with a session) |
| `POST` | `/api/urls` | Shorten a target URL (requires a session) |
| `GET` | `/api/urls` | List links owned by the current session |
| `GET` | `/{shortCode}` | Redirect to the original URL (302; 410 if expired) |
| `GET` | `/api/urls/{shortCode}/analytics` | Retrieve analytics & click statistics for an owned short link |
| `GET` | `/api/shortcode/strategy` | Report the active short-code algorithm (requires a session) |
| `PUT` | `/api/shortcode/strategy` | Switch the active algorithm **server-wide** (requires a session) |
| `DELETE` | `/api/urls/{shortCode}` | Deactivate a short URL (requires auth; guests can only delete their own) |
| `GET` | `/v3/api-docs` | Machine-readable OpenAPI 3 contract |
| `GET` | `/swagger-ui.html` | Interactive API documentation |
| `GET` | `/actuator/health` | Service health check status |

---

## 🔑 Authentication

Login is explicit — there is no silent, automatic session creation. Two paths:

* **Sign in with Google** — a full-page redirect (Authorization Code flow). The
  backend exchanges the code and verifies identity server-side, so the Google client
  secret never reaches the browser. Requires `GOOGLE_OAUTH_CLIENT_ID` /
  `GOOGLE_OAUTH_CLIENT_SECRET` / `GOOGLE_OAUTH_REDIRECT_URI` to be set (see
  `.env.example`) and a matching OAuth client configured in Google Cloud Console.
* **Continue as guest** — mints an anonymous session with no external identity.

Both mint the same kind of opaque, DB-backed session token, sent as
`Authorization: Bearer <token>` on protected requests. The frontend stores it and
attaches it automatically once a user has chosen one of the two options above.

Scripts or other callers can do the same explicitly:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/guest | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"targetUrl":"https://example.com"}'
```

`GET /{shortCode}` remains public. The per-request auth boundary is pluggable
(`RequestAuthenticator`, selected via `app.auth.provider`) and only ever validates
this session token — adding Google as a second *login* method required no change to
that boundary, since it is a second way to obtain a session, not a second way to
authenticate each request.

---

## 🔒 Security & Privacy

* **IP Privacy**: Raw client IPs are never persisted. Analytics store salted hashes/truncated representations to preserve privacy (NFR8).
* **SSRF Prevention**: URL validation blocks private IP ranges (`127.0.0.1`, `10.0.0.0/8`, `169.254.169.254`), non-routable hosts, and unsafe URI schemes.
* **Injection Defense**: Strictly parameterized JPA queries preventing SQL injection.
* **Rate Limiting**: Per-IP fixed-window limits on `POST /api/urls` and `GET /{shortCode}` (`RATELIMIT_CREATE_LIMIT`/`RATELIMIT_REDIRECT_LIMIT` in `.env`), returning `429` with a `Retry-After` hint once exhausted.

---

## 📖 Further Documentation

For detailed architectural decisions, requirements breakdown, and engineering roadmap, consult the [docs/](./docs) directory:

* [Project Overview — Start Here](./docs/overview.md)
* [Functionality & API Reference](./docs/functionality.md)
* [High-Level Design (diagrams)](./docs/HighLevelDesign.md)
* [AI-Assisted Engineering Process](./docs/process/README.md)
* [Requirements & Phase Plan](./docs/requirements.md)
* [Implementation Plan & Milestones](./docs/plan.md)
* [Architectural Decision Records (ADRs)](./docs/decisions.md)
