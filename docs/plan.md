# Plan: URL Shortener Service (Java 17 + Spring Boot 3)

## Stack & core decisions
- Java 17, Spring Boot 3, Maven
- PostgreSQL (source of truth) + Redis (cache-aside for redirects, async click-event buffer)
- Docker Compose: `docker-compose.yml` (dev) + `docker-compose.prod.yml` (prod overlay)
- Patterns: Strategy (short-code generation), Repository (persistence), Cache-Aside (Redis),
  Observer (click analytics via Spring events), Chain of Responsibility (validation),
  Facade (service layer), Factory Method (generator selection) — see
  [copilot-instructions.md](../.github/copilot-instructions.md) for the enforced conventions.

Full FR/NFR list: [requirements.md](./requirements.md). Below, each milestone states only the
requirements it closes, the concrete decisions for that slice, and how it's verified.

**This file is a living document.** `Status` is updated (Not Started → In Progress → Done)
as each milestone is worked, per the working-mode rule in
[copilot-instructions.md](../.github/copilot-instructions.md) — it is not written once and left stale.

## Status legend
`Not Started` · `In Progress` · `Done` · `Deferred` (scoped but intentionally postponed, see [decisions.md](./decisions.md))

## Milestones

### M1 — Foundation
**Status: Done** *(no deps)* — completed 2026-09-17
- **Closes**: NFR5 (health check), NFR6 (structure)
- **Decisions**: Spring Boot 3.3.4 + Java 17 Maven scaffold; one package per architectural layer, each with a
  `package-info.java` recording its responsibility, pattern, and allowed dependencies; Docker Compose (dev)
  with Postgres + Redis; Actuator health exposed; all config env-driven with Compose-matching defaults.
- **Acceptance Criteria**:
  1. `backend/pom.xml` provides Web, Validation, Data JPA, Actuator, Redis, Postgres driver.
  2. Packages established under `com.urlshortener`: `controller, service, generator, repository, cache, event,
     validation, entity, dto, exception, config, util`.
  3. `docker-compose.yml` runs Postgres + Redis with healthchecks and named volumes.
  4. `application.yml` connects to both via env vars (`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`,
     `REDIS_HOST/REDIS_PORT`), no hardcoded secrets.
  5. `/actuator/health` returns 200 with `db` and `redis` both `UP`.
  6. `mvn spring-boot:run` boots with zero WARN/ERROR lines.
  7. `docker compose up -d` leaves both containers `(healthy)`.
- **Outcome**: all 7 met — `mvn clean verify` exit 0, both containers `(healthy)`, health 200, 0 WARN/ERROR.
 
### M2 — Core Shorten + Redirect — *Greenfield scenario*
**Status: Done** *(depends on M1)* — completed 2026-09-18
- **Closes**: FR1, FR2, FR3, FR4 (shorten, redirect, validate, expire)
- **Decisions**: recorded as ADRs rather than restated here —
  [007](./decisions.md#adr-007-short-code-generation--random-base62-resolved-by-the-databases-unique-constraint) (generation, insert-and-catch, switchable strategies),
  [009](./decisions.md#adr-009-no-url-deduplication--every-request-mints-a-new-code) (no dedupe),
  [010](./decisions.md#adr-010-one-urls-table-for-both-generated-codes-and-custom-aliases) (single table),
  [011](./decisions.md#adr-011-the-collision-retry-loop-sits-outside-the-transaction-boundary) (transaction boundary),
  [012](./decisions.md#adr-012-validation-posture--one-chain-allow-lists-fail-closed) (validation posture).
- **Acceptance Criteria**:
  1. `ShortCodeGenerator` strategies with 7-char Base62 codes and a bounded retry (5), failing loudly
     rather than looping.
  2. `schema.sql` creates `urls` with a **named** unique constraint, charset `CHECK`, and nullable
     `expires_at`; idempotent on re-run.
  3. Validation rejects non-http/https schemes, loopback/private/link-local hosts, malformed and
     over-length URLs → 400.
  4. Reserved aliases rejected, so an alias cannot shadow a real root route.
  5. `POST /api/urls` → **201** with the full short URL; duplicate targets mint distinct codes.
  6. `GET /{shortCode}`: unknown → 404, expired → **410**, otherwise **302**.
  7. `expiresAt` in the past at creation → 400.
  8. Unit tests covering collision-retry, validation rules, and the expiry boundary.
- **Outcome**: all 8 met. 126 tests green; `mvn clean verify` exit 0; curl smoke test confirmed
  201 → 302 → 410, 409 on alias conflict, and 400 for every SSRF/scheme probe. `/actuator/health`
  still resolves despite the root-level redirect route.
  - **Found in testing**: Hibernate logs handled collisions at ERROR before our handler classifies them —
    clean under the default strategy, noisy under `hash-url`. Logger deliberately not silenced; see ADR-007.
  - **Corrected in testing**: `javascript:alert(1)` was misdiagnosed as a missing host; the host check moved
    after the scheme rule so the reported cause is accurate.
  - **Known gap**: SSRF is checked at creation only, so DNS rebinding after creation is not caught (ADR-012).
  - **Deferred to M7**: controller-level (MockMvc) and Testcontainers integration tests. M2 covers unit tests
    plus a manual end-to-end pass.

### M3 — Caching
**Status: Deferred** *(depends on M2)* — resequenced after M4, see note below
- **Closes**: NFR1, NFR3, NFR9
- **Decisions**: Redis cache-aside in front of the redirect lookup; on cache miss or Redis outage,
  fall back to Postgres — redirect must never fail because Redis is down. Must honour the
  [design constraints](./requirements.md#design-constraints): structured cached record (not a bare
  URL string), versioned keys (`url:v1:{code}`), access behind an interface (not `RedisTemplate` in
  the service), and bounded short TTL.
- **Expiry interaction**: Postgres `expires_at` is the sole authority; Redis TTL only bounds how long
  a value may be held — `ttl = min(configured, untilExpiry)`. Never let TTL decide expiry: Redis is
  disposable (NFR9) and may be down (NFR3), so a flushed cache would otherwise resurrect dead links.
  Because the cached record carries `expiresAt`, a cache **hit** can return 410 without consulting the DB.
- **Risk flagged**: unknown codes miss the cache every time and hit Postgres, so a flood of random codes
  bypasses caching entirely. Mitigate via negative caching or M5 rate limiting. If negative caching is
  added, **creating an alias must invalidate its negative entry** — otherwise probing `/my-alias` before
  registering it pins a 404 for the whole TTL.
- **Verify**: redirect served from cache on repeat requests; Redis stopped → redirect still works via DB
  fallback; expired link → 410 from both cache hit and DB path
- **Why deferred past M4** *(2026-09-18)*: caching is an optimisation with no measured problem, while FR5
  was still an unmet *functional* requirement — closing the functional gap first is the stronger ordering.
  The deferral is cheap because `UrlService.resolve` already returns a record, so a cache slots in front of
  one method without a signature change. Building it after analytics also keeps a constraint visible that
  is easy to miss in the other order: **a cache hit must still record a click**, otherwise the redirect
  gets faster and the click silently disappears.
- **Consequences while deferred**: NFR1, NFR3 and NFR9 remain open — no milestone currently closes them.
  Redis is also running in `docker-compose.yml` and reported by `/actuator/health` while being entirely
  unused; it stays because M3 is scheduled, not cancelled.

### M4 — Analytics — *Brownfield scenario (extends M2)*
**Status: Done** *(depends on M2)* — completed 2026-09-18. The earlier dependency on M3 was assumed, not
real: analytics publishes an event on redirect and writes asynchronously to Postgres, which needs no cache.
- **Closes**: FR5 (analytics)
- **Decisions**: Observer via Spring events; the redirect publishes and returns, holding no reference to
  analytics. An `@Async` listener buffers to Redis and a `@Scheduled` drainer batch-inserts to Postgres.
  Client addresses are hashed with a secret pepper before the record is built, so no raw IP ever reaches
  Redis or the database (NFR8).
- **Acceptance Criteria**:
  1. Every successful redirect records a click; 404/410 do not (an attempt on a dead link is an abuse
     signal, not link traffic).
  2. Recording never adds latency to, or can fail, a redirect — `enqueue` is contractually non-throwing.
  3. Raw client IPs are never persisted; `X-Forwarded-For` is honoured only from configured proxies.
  4. `GET /api/urls/{code}/analytics` returns total clicks, last-accessed, and a capped, paginated history.
  5. Unit tests covering the privacy guarantee, header-forgery rejection, buffering, draining, and paging.
- **Outcome**: all 5 met. 170 tests green; end-to-end check recorded 3 clicks with correct referrer and
  user-agent, and the database held only 64-character hashes with zero raw addresses. Mutation checks
  confirmed the two security tests fail when the pepper is removed (1 failure) and when the proxy check is
  bypassed (3 failures).
  - **Found in testing**: `ddl-auto: validate` rejected `CHAR(64)` against the entity's `varchar(64)`.
    Fixed in `schema.sql`; the dev table was dropped because `CREATE TABLE IF NOT EXISTS` cannot alter an
    existing one (ADR-008). Safe only because the table was empty and never released.
  - **Corrected in review**: `Math.clamp` is Java 21 and broke the build on 17; `UrlRedirectedEvent`
    needlessly extended `ApplicationEvent`; `ClickDrainer` logged and rethrew where rollback recovered
    nothing.
  - **Open risk — decide before M8**: `ANALYTICS_IP_PEPPER` has the dev default `dev-only-not-a-secret`.
    Unlike the other dev defaults this one **fails silently**: the application starts normally and analytics
    work, but every stored hash is reversible by anyone reading this repository. Dev/prod profile separation
    was raised and deferred to a decision after M4.
  - **Deferred to M7**: MockMvc and Testcontainers integration tests for the analytics pipeline.

### M5 — Reliability & Security — *Ambiguous scenario ("make it more reliable")*
**Status: Not Started** *(depends on M2, M4)*
- **Closes**: NFR4, NFR8
- Scope for "reliable" is undefined by the spec, so I define the acceptance criteria myself:
  1. Per-IP rate limiting on create + redirect, 429 on overflow
  2. IP hashing (never persist raw IP)
  3. Consistent JSON error format via `@ControllerAdvice`
  4. Redirect stays correct under a Redis outage (NFR3 regression check)
- **Verify**: burst requests → 429 past threshold; no raw IP in DB; Redis stopped → redirect still works

### M6 — Auth + Link Deletion — *Phase 2*
**Status: In Progress** *(depends on M2, M4 — see [ADR-005](./decisions.md#adr-005--authentication))*
- **Closes**: NFR4 (ownership boundary), plus link deletion — see
  [Phase 2 scope](./requirements.md#phase-2-scope-planned-not-rejected)
- **Scoped as one unit**: deletion without an ownership boundary lets anyone remove anyone's link, so
  auth and delete ship together rather than as separate milestones.
- **Proposed decisions**: lightweight API-Key auth (`X-Api-Key` header, hashed key storage); redirect
  stays public; create/manage/analytics require a key; ownership check in the service layer, not
  per-controller. Deletion is **soft** (`is_active = false`) so the code is permanently retired.
- **Known cost when picked up**: requires cache invalidation and inherits the cache-aside repopulation
  race (a concurrent reader can write a stale entry after eviction); bounded TTL from M3 is the backstop.
- **Verify** (once picked up): missing/invalid key → 401; mismatched owner → 403; deleted link → 410;
  deleted link no longer served from cache
- **Started early** *(2026-09-18)*: auth was pulled forward before M5 so the API gains a real ownership
  boundary before rate limiting. Implemented now: pluggable auth boundary with `api-key` as the active
  provider, `POST /api/auth/keys` bootstrap, authenticated `POST /api/urls`, authenticated
  `GET /api/urls/{code}/analytics`, and authenticated `GET /api/urls` to list a caller's earlier links so
  old analytics can be reopened without retyping short codes.
- **Still open in M6**: link deletion is not implemented yet, and the current ownership model is not retroactive
  for links created before auth existed.
- **Added alongside** *(2026-09-18)*: `GET`/`PUT /api/shortcode/strategy` exposes and switches the active
  generation strategy (ADR-007), surfaced in the frontend so the algorithm is visible rather than invisible
  server state. Both routes require a key.
  - **Risk flagged**: switching is **global**, and `POST /api/auth/keys` is currently open and unlimited, so
    "requires a key" is only as strong as key issuance. Anyone can mint a key and select the enumerable
    `hash-url` strategy. Closing this needs either the role/scope check below or M5 rate limiting — until then
    the ADR-007 enumeration concern is reduced, not removed.
  - **Follow-up for roles**: add a `role`/`scopes` column on `owners`, expose it on
    `AuthenticatedPrincipal`, and gate this endpoint on an operator role. The same field serves OAuth
    claims directly once roles exist.
- **Replaced with Google OAuth + guest login** *(2026-09-18, see [ADR-005](./decisions.md#adr-005--authentication))*:
  the open, silent API-key bootstrap above is gone. Login is now explicit: **Sign in with Google**
  (Authorization Code flow, backend-mediated so the client secret never reaches the browser) or
  **Continue as guest** (same underlying session mechanism as before, but now a deliberate user
  choice instead of an invisible default). Both mint the same kind of opaque, DB-backed session
  token, sent as `Authorization: Bearer <token>` — the `X-Api-Key` header and `/api/auth/keys`
  endpoint no longer exist.
  - **Data model redone**: identity and credential are now separate tables — `owners`
    (identity: provider, external subject, email) and `owner_sessions` (credential: token hash) —
    replacing a single conflated table from the earlier bootstrap, dropped/recreated directly in
    `schema.sql` rather than migrated in place, acceptable pre-launch since no real user data exists yet.
  - **No change needed** to `RequestAuthenticator`/`ActiveRequestAuthenticator`/any controller or
    service: they only ever validated our own session token, never a provider's token directly, so
    this was a login-path change, not a per-request-auth change.
  - **Still applies from before**: the strategy-switch risk above is essentially unchanged — Google
    login is now the stronger identity path, but guest login is still open/self-service by design, so
    the role/scope or rate-limiting follow-up is still the real fix, not superseded by this work.
  - **Requires operator setup**: a Google Cloud OAuth client (Client ID/Secret), supplied via
    `GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET`/`GOOGLE_OAUTH_REDIRECT_URI` env vars —
    never hardcoded.
  - **Live Google OAuth verified end-to-end** *(2026-09-18)*: fixed two pre-existing bugs that had
    blocked real login — a config prefix mismatch (`app.auth.google` vs. the binder's expected
    `app.oauth.google`, silently producing an empty authorize URL) and `make dev`/`make api` never
    sourcing `backend/.env` (`.env` auto-load is a Docker Compose convention, not a JVM/Maven one).
    Also added `prompt=select_account` to the authorize URL so signing out and back in always shows
    the Google account picker instead of silently reusing the active browser session.
  - **Live click updates and visible short URLs** *(2026-09-18)*: `GET /api/urls/events` streams
    Server-Sent Events (a per-owner `SseEmitter` registry, `ClickBroadcaster`, fed by a second,
    synchronous `@EventListener` alongside the existing async click-recording listener — Observer
    pattern, zero changes to the redirect path). The event carries no click data, only a "click" ping;
    the frontend refetches from the real endpoint on receipt, keeping Postgres as the single source of
    truth. Native `EventSource` cannot send the `Authorization` header, so the frontend uses a
    hand-rolled `fetch()`-based SSE reader instead of a one-time ticket or token-in-URL (both rejected
    as weaker than keeping the bearer token in a header). Separately, "Your links" now shows and lets
    the user copy each row's full short URL, built via a `AppProperties.baseUrl()`-aware
    `OwnedUrlSummaryResponse.from(projection, baseUrl)` factory (mirrors `CreateUrlResponse.from`), since
    the JPQL projection has no access to that runtime config value.

### M7 — Tests
**Status: Not Started** *(depends on M2–M6)*
- **Closes**: NFR7
- **Build**: unit tests (codegen collision retry, URL validator, service logic); integration
  tests via Testcontainers (real Postgres + Redis) covering create → redirect → analytics and
  edge cases (400/404/409/410/429)
- **Verify**: `mvn clean verify` green

### M8 — Containerization
**Status: Not Started** *(depends on M1–M7)*
- **Closes**: NFR2
- **Build**: multi-stage Dockerfile, `docker-compose.prod.yml` overlay, env-var driven config
  (no secrets in code)
- **Acceptance Criteria** *(a–c inherited from earlier milestones — dev-only defaults that must not reach prod)*:
  a. Prod overlay defines **no** default for `DB_PASSWORD`, so a missing value fails startup instead of
     silently using the Compose dev password.
  b. `MANAGEMENT_HEALTH_SHOW_DETAILS` set to `never`/`when-authorized` so component versions and topology
     aren't publicly exposed.
  c. **No default for `ANALYTICS_IP_PEPPER`**, and startup must reject the known dev value
     `dev-only-not-a-secret`. This is the most dangerous of the three because it fails silently: the
     application starts and analytics work normally while every stored IP hash is reversible by anyone
     with this repository. Accepted deliberately for local development (M4); must not survive here.
  d. Image runs as a non-root user; no secrets baked into any layer.
- **Verify**: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up` runs the full stack;
  unsetting `DB_PASSWORD` fails fast; `/actuator/health` returns status only, no component detail

### M9 — Minimal Frontend
**Status: Done** *(depends on M2, M4 — see [ADR-006](./decisions.md#adr-006--frontend--react--vite))*
- **Closes**: end-to-end usability of the prototype (no FR/NFR of its own)
- **Decisions**: React 19 + Vite + TypeScript in a real monorepo (`backend/` + `frontend/`);
  two screens only (create + analytics lookup); all API paths live in one module, all
  UI strings in one module, all env-driven values in one config module; separate dev
  server calling the API cross-origin via `CorsConfig`; no hardcoded URLs, endpoint
  paths, user-facing strings, or magic numbers in components. `react-i18next` was
  explicitly dropped as disproportionate for two screens; see ADR-006.
- **Acceptance Criteria**:
  1. `frontend/` is a Vite React TypeScript app that runs independently of the backend build.
  2. The create flow calls `POST /api/urls`, renders the created short URL, supports copy,
     and hands the short code into the analytics screen without manual re-entry.
  3. The analytics flow calls `GET /api/urls/{code}/analytics`, shows total clicks, last
     click, and paginated click history.
  4. Components contain no hardcoded API URLs, endpoint paths, user-facing strings, or
     scattered magic numbers; those values are centralised in `api.ts`, `strings.ts`,
     and `config.ts`.
  5. The frontend runs against the live backend through CORS from `http://localhost:5173`
     with no wildcard origin and no credentials.
  6. Root `Makefile` orchestrates the monorepo developer flow: setup, infra, backend,
     frontend, test, and build.
  7. Frontend checks are real: TypeScript uses `tsc -b --force`, not a no-op root config check.
- **Outcome**: all 7 met. The frontend is live and usable end-to-end against the API,
  with create and analytics flows working from the browser. `make test` covers backend
  tests plus frontend typecheck/lint, `make build` produces the API jar and frontend
  bundle, and `make dev` runs infra + backend + frontend together.
  - **Found in testing**: Vite's root `tsconfig.json` with project references made
    `tsc --noEmit` a false green; replaced with `tsc -b --force` so the quality gate
    checks the actual app.
  - **Corrected in UI review**: long target URLs were clamped to protect layout, the
    analytics table was tightened for narrow screens, and the expiry field was changed
    to opt-in with a prefilled local default to avoid the browser's ugly empty
    `datetime-local` placeholder.

### M10 — Docs & Summary
**Status: Not Started** *(depends on all)*
- **Build**: README (setup/run/test + curl examples), architecture overview, scenario write-ups
  (greenfield = M2, brownfield = M4, ambiguous = M5), final engineering summary (risks,
  trade-offs, assumptions, limitations, traceability recap)

## Repo-wide verification checklist
1. `mvn clean verify`
2. Manual curl smoke test: create → redirect → analytics
3. Security probes: `http://127.0.0.1`, `http://169.254.169.254`, `file://` scheme → all 400
4. Rate-limit burst test → 429 on overflow
5. Expired-link test → 410 Gone
6. Alias collision test → 409 Conflict

## Scope exclusions (explicit)
- No real authentication/login system in v1
- No CI/CD pipeline (local Maven + Docker Compose only)
- No PII beyond hashed IP (raw IP never persisted)
