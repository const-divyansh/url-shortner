# Requirements — Functional & Non-Functional

One line: submit a long URL, get a short code back, be redirected when that code is
visited, and see click statistics per code.

**How to read this document**: every requirement below is tagged with its phase
(**Phase 1** = built and live-verified; **Phase 2** = scoped, not yet built — see
[functionality.md §11](./functionality.md#11-not-built) for the current "not built"
boundary) and its current status. Full "why this and not that" reasoning lives in
[decisions.md](./decisions.md); milestone-by-milestone build history lives in
[plan.md](./plan.md). This page only answers "what was required, and where does it
stand."

## Functional Requirements

| # | Requirement | Phase | Status |
|---|---|---|---|
| FR1 | **Shorten** — accept a long URL, return a unique short code, or accept a caller-supplied custom alias. Uniqueness is absolute: a code is never issued twice, and an internal generation collision must never surface to the caller as a failure. | 1 | ✅ Done |
| FR2 | **Redirect** — `GET /{shortCode}` redirects to the original URL (302). Unknown code → 404. | 1 | ✅ Done |
| FR3 | **Validate** — reject malformed/unsafe URLs (non-http/https schemes, loopback/private/link-local hosts — SSRF-style protection) with 400. Reject aliases outside the permitted charset/length or on a reserved list. A taken alias → 409. | 1 | ✅ Done |
| FR4 | **Expire** — accept an optional expiry timestamp at creation. Past that instant, the code stops resolving → 410 Gone. Omitted means the link never expires. Expiry is **immutable once set** (see Design constraints below — this is what keeps Phase 2 caching additive). | 1 | ✅ Done |
| FR5 | **Analytics** — record every redirect and expose per-code statistics: total clicks, plus timestamp, referrer, and user-agent per click, updated live in the UI via WebSocket. | 1 | ✅ Done |
| FR6 | **Identity** — authenticate via Google OAuth or an explicit guest session; every create/list/analytics/delete action runs against a real ownership boundary, not an open endpoint. | 1 | ✅ Done |
| FR7 | **Deletion** — an owner may soft-delete their own link. A deleted alias can be reclaimed by the *same* owner (delete-and-recreate, click history discarded); a different caller remains blocked from ever reusing it (anti-hijack). Guests specifically cannot delete (button hidden client-side, 403 if the API is called directly). | 1 | ✅ Done |

## Non-Functional Requirements

| # | Requirement | Phase | Status |
|---|---|---|---|
| NFR1 | **Performance** — the redirect path is cached, not just correct. | 2 | ⏸ Not built. Redis cache-aside for the redirect lookup is scoped (M3) but deferred; the redirect works correctly today, it just always hits Postgres. |
| NFR2 | **Scalability** — the app layer is stateless so more than one instance can run behind a load balancer. | 1 (design) / 2 (proof) | 🟡 Partial. Sessions/links/clicks/rate-limit counters already live only in Postgres/Redis, never in-process — so the *design* is stateless today. Running >1 instance is **unproven**: the scheduled click-drain and WebSocket click notifications are both currently per-instance concerns that need verifying before "scalable" is a claim rather than an intent. |
| NFR3 | **Reliability** — degrade gracefully, not fail closed, when a dependency is down. | 1 | ✅ Done. Rate limiter fails **open** on Redis outage (a broken limiter must not become a full outage); redirect currently has no cache to fail at all, so it is Postgres-or-nothing today, which is trivially "graceful" but only because Phase 2 hasn't added the failure surface yet — this must not regress once caching lands. |
| NFR4 | **Security** — rate limiting, parameterized queries only, secrets via env vars, no raw client IPs stored. | 1 | ✅ Done. Rate limiting (fixed-window, Redis-backed, IP-normalized to unify loopback addresses and truncate IPv6 to /64); JPA/parameterized queries throughout, no string-concatenated SQL; all secrets via env vars/`.env` (never committed); client IPs are hashed for analytics, never stored raw. |
| NFR5 | **Observability** — structured logs, metrics, health checks. | 1 (partial) | 🟡 Partial. `/actuator/health` (with component detail for Postgres/Redis) and `/actuator/metrics` are live. Logs are **not yet structured** (plain Spring Boot console format, no JSON/logback config). |
| NFR6 | **Maintainability** — SOLID principles and documented patterns; a versioned API contract. | 1 (principles) / 2 (contract doc) | ✅ Done. SOLID-driven design throughout — see [decisions.md](./decisions.md) and [functionality.md](./functionality.md) for the specific patterns in use (Strategy, Repository, Cache-Aside, Observer, Chain of Responsibility, Facade, Factory Method) and the authorization-policy extraction that keeps ownership rules in one place. The live OpenAPI contract is served at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`; [functionality.md](./functionality.md)'s API table remains the human-readable companion for behaviour and rationale. |
| NFR7 | **Testability** — unit and integration tests. | 1 | ✅ Done. 237 backend tests total: unit coverage plus Testcontainers-based integration tests (real Postgres + Redis, not mocks) covering DB-constraint behavior, alias reclaim, and Redis fail-open under an actual paused container. Playwright E2E tests (browser-level, against the running frontend+backend) are done as well - see [plan.md](./plan.md) M7. |
| NFR8 | **Data privacy** — no raw client IPs persisted. | 1 | ✅ Done. Client IPs are hashed before storage for analytics; rate-limiting keys are normalized (loopback unification, IPv6 /64 truncation) and hashed separately — analytics and rate-limiting deliberately use different address granularity for different reasons (see [decisions.md](./decisions.md)). |
| NFR9 | **Durability** — Postgres is the system of record; Redis is disposable. | 1 | ✅ Done. Nothing is only in Redis today: rate-limit counters are inherently ephemeral by design, and there is no cache yet to lose. This constraint is written down now specifically so Phase 2 caching cannot quietly make Redis load-bearing. |

## Phase boundary, in one paragraph

**Phase 1** delivered every functional requirement (FR1–FR7) plus the security and
reliability non-functionals that protect them (NFR3, NFR4, NFR7, NFR8, NFR9) and a
partial slice of observability/maintainability (NFR5). **Phase 2** is performance
and scale on top of an already-correct system: caching (NFR1), proving
multi-instance scalability (NFR2), finishing the remaining observability work
(structured logs — NFR5), browser-level E2E coverage, and an nginx/edge layer.
Nothing in Phase 2 is a correctness gap; everything in Phase 1 is independently
true without Phase 2 ever landing.

### Phase 2 scope, in detail

#### Caching (M3) — the main remaining item
- Redis cache-aside in front of the redirect lookup, with graceful fallback to
  Postgres if Redis is unavailable (NFR1, NFR3, NFR9). See M3 in
  [plan.md](./plan.md).
- **Why it was deferred past rate limiting and deletion**: a cache introduces a second
  source of truth for data that can already be deleted and expired, and its failure
  modes are silent and correctness-level — a deleted link that keeps redirecting.
  Rate limiting only ever *rejects*, so it cannot corrupt a redirect or lose a click.
  Lower-risk work shipped first.
- **Cost deletion already created for this**: an owner deleting a link must evict its
  cache entry immediately, not wait for a TTL, or revocation becomes "revocation,
  eventually." The cache-aside repopulation race applies too — a concurrent reader can
  write a stale entry just after eviction — with a bounded TTL as the backstop.

#### Scalability (NFR2)
- The application layer is already stateless: sessions, links, clicks, and rate-limit
  counters all live in Postgres or Redis, so a second instance needs no sticky
  sessions and no shared filesystem.
- **Not yet proven.** Two things need checking before "scalable" is a verified claim:
  the scheduled click drain runs per instance (the Redis queue must remain the
  arbiter — `LPOP` already provides that), and WebSocket click notifications are
  delivered by the instance holding the connection, so a click served by instance A
  must still reach a browser connected to instance B.

#### nginx / edge support
- **Rate limiting belongs at the edge too.** `limit_req` rejects a flood before it
  costs a servlet thread, a database connection, or a Redis round trip; the in-app
  limiter still pays the full request cost to say "no." The two are layered, not
  alternatives — nginx cannot read a bearer token and enforce a per-account quota,
  which is where the app-level limiter earns its place.
- **TLS termination and static asset serving** for the built frontend.
- **Required change when added**: the real client address moves into
  `X-Forwarded-For`, which this service deliberately ignores unless the peer is a
  configured trusted proxy. Deploying behind nginx without setting
  `ANALYTICS_TRUSTED_PROXIES` collapses every visitor onto the proxy's address —
  analytics silently reports one visitor, and the per-IP rate limit becomes a single
  global budget.
- Lands with M8 (containerisation / production overlay) in [plan.md](./plan.md).

#### Observability, maintainability — remaining slice
- Structured (JSON) logging — not yet configured; current logs are plain Spring Boot
  console output.
- Browser-level (Playwright) E2E tests — complementing the backend-level unit +
  Testcontainers integration suite (M7).

## Design constraints - From Day 1

So Phase 2 stays additive rather than a rewrite:

1. **Expiry is immutable once set.** This is what keeps expiry a *property* travelling
   inside a future cached value rather than an external event — so it needs no cache
   invalidation, unlike deletion. Allowing edits later would forfeit that.
2. **Postgres decides expiry, never Redis TTL.** Redis is disposable (NFR9) and may be
   down (NFR3); if TTL were the authority, a flushed or unavailable cache would
   resurrect dead links. TTL only bounds how long a value may be held:
   `ttl = min(configured, untilExpiry)`.
3. **Cache a structured record, not a bare URL string** — later fields must not break
   deserialization of entries already in Redis.
4. **Version cache keys** (`url:v1:{code}`) so a value-shape change can orphan old
   entries cleanly.
5. **Reach Redis through an interface**, not `RedisTemplate` in the service — adding
   `invalidate(code)` must be a two-line change.
6. **Keep cache TTL bounded and short** — the backstop for the repopulation race above.
7. **The service returns a record, not a `String`** — "gone" must be a new branch, not
   a signature change.

## Status
See [plan.md](./plan.md) for milestone-by-milestone status, and
[functionality.md](./functionality.md) for live-verified current behavior.
