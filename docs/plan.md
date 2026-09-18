# Plan: URL Shortener Service (Java 17 + Spring Boot 3)

## Stack & core decisions
- Java 17, Spring Boot 3, Maven
- PostgreSQL (source of truth) + Redis (rate-limit counters and the async click-event
  buffer today; cache-aside for redirects is Phase 2 — see M3 below)
- Docker Compose: `docker-compose.yml` (dev). A prod overlay (`docker-compose.prod.yml`)
  is scoped but not built — see M8.
- Patterns: Strategy (short-code generation), Repository (persistence), Cache-Aside
  (Redis, Phase 2), Observer (click analytics via Spring events), Chain of
  Responsibility (validation), Facade (service layer), Factory Method (generator
  selection) — see [copilot-instructions.md](../.github/copilot-instructions.md) for
  the enforced conventions.

Full FR/NFR list: [requirements.md](./requirements.md). Below, each milestone states
only the requirements it closes, the concrete decisions for that slice, and how it's
verified.

**This file is a living document.** `Status` is updated (Not Started → In Progress →
Done) as each milestone is worked, per the working-mode rule in
[copilot-instructions.md](../.github/copilot-instructions.md) — it is not written once
and left stale.

## Status legend
`Not Started` · `In Progress` · `Done` · `Deferred` (scoped but intentionally
postponed, see [decisions.md](./decisions.md))

## Phase 1 — core service (delivered)

### M1 — Foundation
**Status: Done** *(no deps)*
- **Closes**: NFR5 (health check), NFR6 (structure)
- **Decisions**: Spring Boot 3.3.4 + Java 17 Maven scaffold; one package per
  architectural layer, each with a `package-info.java` recording its responsibility,
  pattern, and allowed dependencies; Docker Compose (dev) with Postgres + Redis;
  Actuator health exposed; all config env-driven with Compose-matching defaults.
- **Verified**: `mvn clean verify` exit 0; both containers `(healthy)`;
  `/actuator/health` returns 200 with `db` and `redis` both `UP`; zero WARN/ERROR on
  boot.

### M2 — Core Shorten + Redirect — *Greenfield scenario*
**Status: Done** *(depends on M1)*
- **Closes**: FR1, FR2, FR3, FR4 (shorten, redirect, validate, expire)
- **Decisions**: recorded as ADRs —
  [007](./decisions.md#adr-007-short-code-generation--random-base62-resolved-by-the-databases-unique-constraint)
  (generation, insert-and-catch, switchable strategies),
  [009](./decisions.md#adr-009-no-url-deduplication--every-request-mints-a-new-code)
  (no dedupe),
  [010](./decisions.md#adr-010-one-urls-table-for-both-generated-codes-and-custom-aliases)
  (single table),
  [011](./decisions.md#adr-011-the-collision-retry-loop-sits-outside-the-transaction-boundary)
  (transaction boundary),
  [012](./decisions.md#adr-012-validation-posture--one-chain-allow-lists-fail-closed)
  (validation posture).
- **Verified**: 7-char Base62 codes with a bounded retry (5); SSRF/scheme validation
  rejects private/loopback/link-local hosts and non-http(s) schemes → 400; reserved
  aliases rejected; `POST /api/urls` → 201; duplicate targets mint distinct codes;
  `GET /{shortCode}` → 404 unknown, 410 expired, 302 otherwise; past-dated expiry at
  creation → 400.
- **Known gap**: SSRF is checked at creation only, so DNS rebinding after creation is
  not caught (see [ADR-012](./decisions.md#adr-012-validation-posture--one-chain-allow-lists-fail-closed)).

### M4 — Analytics — *Brownfield scenario (extends M2)*
**Status: Done** *(depends on M2)*
- **Closes**: FR5 (analytics)
- **Decisions**: Observer via Spring events; the redirect publishes and returns,
  holding no reference to analytics. An `@Async` listener buffers to Redis and a
  `@Scheduled` drainer batch-inserts to Postgres. Client addresses are hashed with a
  secret pepper before the record is built, so no raw IP ever reaches Redis or the
  database (NFR8).
- **Verified**: every successful redirect records a click, 404/410 do not; recording
  is contractually non-throwing so it cannot fail a redirect; `X-Forwarded-For` is
  honoured only from configured proxies; `GET /api/urls/{code}/analytics` returns
  total clicks, last-accessed, and a capped, paginated history; mutation testing
  confirmed the pepper and proxy-trust checks are load-bearing, not decorative.
- **Open risk**: `ANALYTICS_IP_PEPPER`'s dev default (`dev-only-not-a-secret`) fails
  silently in production — the app starts and analytics work, but every hash is
  reversible. M8 must reject that value at startup outside dev.

### M5 — Reliability & Security — *Ambiguous scenario ("make it more reliable")*
**Status: Done** *(depends on M2, M4)*
- **Closes**: NFR4, NFR8
- Scope for "reliable" was undefined by the brief, so the acceptance criteria were
  self-defined: per-IP rate limiting on create + redirect (429 on overflow), IP
  hashing, a consistent JSON error format, and a redirect that stays correct under a
  Redis outage.
- **Decisions**: fixed-window per-IP budgets in Redis (`RateLimiter` interface,
  `RedisRateLimiter` the live implementation, `NoOpRateLimiter` the kill-switch
  default when disabled); fails open on a Redis outage rather than blocking traffic
  (NFR3 — a limiter outage must never become a service outage); budgets keyed by
  `RateLimitScope` (`CREATE`, `REDIRECT`) through a `Map<RateLimitScope,Budget>`, so
  adding a new scope is a config change, not a code change (OCP).
- **Verified**: burst requests past the configured budget return 429 with a
  `rate_limit.exceeded` body and a `Retry-After`-style message; no raw IP reaches the
  database; stopping Redis leaves rate limiting open (fail-open) and redirects still
  work.

### M6 — Auth + Link Deletion — *Phase 2 originally, pulled forward*
**Status: Done** *(depends on M2, M4 — see [ADR-005](./decisions.md#adr-005--authentication))*
- **Closes**: NFR4 (ownership boundary), plus link deletion — see
  [Phase 2 scope](./requirements.md#phase-2-scope--caching--link-deletion)
- **Scoped as one unit**: deletion without an ownership boundary lets anyone remove
  anyone's link, so auth and delete shipped together rather than as separate
  milestones.
- **Decisions**: two login paths minting the same opaque, DB-backed session
  token — **Sign in with Google** (Authorization Code flow, backend-mediated so the
  client secret never reaches the browser) and **Continue as guest** (explicit user
  choice, not a silent default). Identity (`owners`: provider, external subject,
  email) and credential (`owner_sessions`: token hash) are separate tables.
  `RequestAuthenticator` only ever validates the session token, never a provider's
  token directly, so adding Google required no per-request-auth change — only a new
  way to obtain a session. Deletion is **soft** (`urls.is_active = false`): the short
  code can never be reissued (hijack prevention), and `click_events` keeps a valid
  historical owner reference. `UrlService.resolve()` checks `isActive()` before
  expiry and returns 410 (`code.deleted`), distinct from 410 (`code.expired`) so logs
  can tell the two apart. Deleting an unowned or already-deleted link responds 404 —
  the same response a stranger gets for a code that never existed, so probing cannot
  distinguish the two states. Mismatched owner responds 403. Guest sessions cannot
  delete (only an identity Google vouches for can revoke a link).
- **Also delivered**: `GET`/`PUT /api/shortcode/strategy` to inspect/switch the active
  short-code generation strategy server-wide (both require a session); live click
  updates over WebSocket/STOMP (`ClickBroadcaster`, fed by a synchronous listener
  alongside the existing async click-recording listener, so the redirect path is
  unchanged) instead of polling; "Your links" lists and lets the owner copy each
  short URL.
- **Verified**: resolve-of-deleted → 410; successful delete; forbidden (wrong/no
  owner) → 403; not-found (unknown or already-deleted code) → 404; guest delete
  attempt → 403; `mvn -o test` green; manual create → delete → 410-on-redirect →
  absent from "Your links" confirmed end-to-end.
- **Open risk carried forward**: the short-code strategy switch is global and any
  session (including guest) can flip it, so "requires a session" is a weaker gate
  than a role check. No role/scope column exists yet on `owners`; flagged, not fixed,
  since no abuse has been reported and Google login already raised the bar for who
  can obtain a session that matters.

### M7 — Tests
**Status: Done** *(depends on M2–M6)*
- **Closes**: NFR7
- **Delivered**: unit tests across codegen collision retry, URL validation, rate
  limiting, auth, and service logic (237 tests); Testcontainers-based integration
  tests against real Postgres + Redis covering create → redirect → analytics and the
  4xx/429 edge cases; a Playwright end-to-end suite against the live dev stack
  covering generated codes, custom aliases, duplicate-alias 409s, invalid-URL 400s,
  guest-session delete gating, live analytics updates, real expiry → 410, and the
  create rate limit → 429.
- **Verified**: `mvn -o verify` green (237 tests, Docker available); `make e2e` green
  (6/6 Playwright tests); `frontend npm run check` clean.

### API contract (OpenAPI)
**Status: Done** *(depends on M2, M4, M5, M6)*
- **Closes**: NFR6 (versioned API contract)
- **Decisions**: `springdoc-openapi` generates the spec directly from the live
  controllers and DTOs rather than a hand-maintained document, so the contract cannot
  drift silently from the code. A single `OpenApiConfig` bean declares the bearer
  session-token security scheme once, applied globally.
- **Verified**: `/v3/api-docs` and `/swagger-ui.html` serve live content; spot-checked
  documented status codes against real responses (401 unauthenticated, 409 duplicate
  alias, 403 guest delete attempt, 410 expired link) and all matched.

### M9 — Minimal Frontend
**Status: Done** *(depends on M2, M4 — see [ADR-006](./decisions.md#adr-006--frontend--react--vite))*
- **Closes**: end-to-end usability of the prototype (no FR/NFR of its own)
- **Decisions**: React 19 + Vite + TypeScript in the same monorepo; two screens
  (create + analytics); every API path lives in one module, every user-facing string
  in one module, every env-driven value in one config module; separate dev server
  calling the API cross-origin via `CorsConfig`; no hardcoded URLs, endpoint paths,
  strings, or magic numbers in components. `react-i18next` was explicitly dropped as
  disproportionate for two screens — see ADR-006.
- **Verified**: create flow calls `POST /api/urls`, renders the short URL, supports
  copy; analytics flow calls `GET /api/urls/{code}/analytics` and shows clicks/paging;
  `tsc -b --force` (real project-reference check, not a no-op root config) and
  `oxlint` both clean; `make dev` runs infra + backend + frontend together.

### M10 — Docs & Summary
**Status: Done** *(depends on all)*
- **Delivered**: [`overview.md`](./overview.md) (single entry point),
  [`HighLevelDesign.md`](./HighLevelDesign.md) (component + sequence diagrams),
  [`requirements.md`](./requirements.md) (FR/NFR by phase),
  [`decisions.md`](./decisions.md) (ADRs), [`functionality.md`](./functionality.md)
  (verified behaviour + API reference), `docs/process/` (the three required
  scenarios — greenfield/brownfield/ambiguous — each showing decomposition,
  execution, validation), and the root `README.md` (setup, run, test).

## Phase 2 — scoped, not built

### M3 — Caching
**Status: Deferred** *(depends on M2)*
- **Closes** (once built): NFR1, NFR3, NFR9
- **Decisions**: Redis cache-aside in front of the redirect lookup; on cache miss or
  Redis outage, fall back to Postgres — a redirect must never fail because Redis is
  down. Must honour the
  [design constraints](./requirements.md#design-constraints): a structured cached
  record (not a bare URL string), versioned keys (`url:v1:{code}`), access behind an
  interface (never `RedisTemplate` in the service), and a bounded short TTL. Postgres
  `expires_at` stays the sole authority — `ttl = min(configured, untilExpiry)` — since
  Redis is disposable (NFR9) and may be down (NFR3); a flushed cache must never
  resurrect a dead link. Because the cached record carries `expiresAt`, a cache hit
  can return 410 without consulting the database, and **a cache hit must still record
  a click** — the redirect gets faster, not silently unmeasured.
- **Why deferred**: caching is an optimisation with no measured problem; the
  functional gaps (analytics, reliability, auth) were stronger claims on scarce time.
  Redis already runs in `docker-compose.yml` and reports healthy while unused — it
  stays because this milestone is scheduled, not cancelled.
- **Risk to design around when built**: unknown codes miss the cache every time, so a
  flood of random codes bypasses caching entirely. If negative caching is added,
  creating an alias must invalidate its negative entry, or probing an alias before
  registering it pins a false 404 for the whole TTL.

### M8 — Containerization
**Status: Not Started** *(depends on M1–M7)*
- **Closes**: NFR2
- **Scope**: multi-stage Dockerfile, `docker-compose.prod.yml` overlay, env-var driven
  config with no secrets baked into any layer, non-root image user.
- **Must reject at startup, not just omit a default**: `DB_PASSWORD` (no default —
  missing value fails startup rather than silently reusing the Compose dev password);
  `ANALYTICS_IP_PEPPER` (must reject the known dev value `dev-only-not-a-secret`,
  since it currently fails silently — see M4's open risk);
  `MANAGEMENT_HEALTH_SHOW_DETAILS` set to `never`/`when-authorized` so component
  versions and topology aren't publicly exposed.
- **Verify** (once built): `docker compose -f docker-compose.yml -f
  docker-compose.prod.yml up` runs the full stack; unsetting `DB_PASSWORD` fails fast;
  `/actuator/health` returns status only, no component detail.

## Repo-wide verification checklist
1. `mvn clean verify`
2. Manual curl smoke test: create → redirect → analytics
3. Security probes: `http://127.0.0.1`, `http://169.254.169.254`, `file://` scheme →
   all 400
4. Rate-limit burst test → 429 on overflow
5. Expired-link test → 410 Gone
6. Alias collision test → 409 Conflict

## Scope exclusions (explicit)
- No CI/CD pipeline (local Maven + Docker Compose only)
- No PII beyond hashed IP (raw IP never persisted)
- No roles/scopes on `owners` yet — the short-code strategy switch and guest
  self-service both carry that as an open, flagged risk (see M6)
