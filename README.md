# URL Shortener Service

A production-grade, highly scalable URL shortener service built with **Java 17** and **Spring Boot 3**. Designed for low-latency redirects, high availability, robust analytics, and multi-tier security.

---

## 🚀 Key Features & Architecture

* **High Performance Redirects**: Ultra-fast URL resolution utilizing a **Redis** cache-aside layer in front of **PostgreSQL**.
* **Resilient Architecture**: Automatic fallback to PostgreSQL canonical storage in the event of cache misses or Redis outages.
* **Asynchronous Analytics**: Click events (timestamps, referrers, user agents) captured asynchronously via Spring `ApplicationEvent` listeners without adding latency to redirect execution.
* **Security & SSRF Defense**: Input validation chain preventing SSRF (blocking localhost, private IP ranges, non-HTTP/HTTPS schemes) and IP privacy protection via salted cryptographic hashing.
* **Extensible Short-Code Generation**: Pluggable code generation strategy using Base62 encoding with automated collision resolution.

---

## 🛠️ Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Runtime** | Java 17 |
| **Framework** | Spring Boot 3.3.4 (Spring MVC, Data JPA, Actuator) |
| **Primary Database** | PostgreSQL |
| **Caching & Buffering** | Redis |
| **Database Migrations** | `schema.sql` 
| **Build & Tooling** | Maven |
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
| `DELETE` | `/api/urls/{shortCode}` | *Phase 2* — deactivate a short URL (requires auth) |
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

---

## 📖 Further Documentation

For detailed architectural decisions, requirements breakdown, and engineering roadmap, consult the [docs/](./docs) directory:

* [Q&A — Start Here](./docs/qa/00-overview.md)
* [Functionality & API Reference](./docs/functionality.md)
* [AI-Assisted Engineering Process](./docs/process/README.md)
* [Requirements & Phase Plan](./docs/requirements.md)
* [Implementation Plan & Milestones](./docs/plan.md)
* [Architectural Decision Records (ADRs)](./docs/decisions.md)
