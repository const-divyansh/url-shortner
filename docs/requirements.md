# Requirements

Submit a long URL, get a short code back, be redirected when that code is visited,
and see click statistics per code.

Scope is deliberately narrow. See [Deferred scope](#deferred-scope) for what was cut
and why; [plan.md](./plan.md) owns milestones and delivery status.

## Functional Requirements
- **FR1 — Shorten**: accept a long URL and return a unique short code, or an optional
  caller-supplied custom alias. Uniqueness is absolute — a code is never issued twice,
  and an internal collision must never surface to the caller as a failure.
- **FR2 — Redirect**: `GET /{shortCode}` redirects to the original URL (302).
  Unknown code → 404.
- **FR3 — Validate**: reject malformed or unsafe URLs (non-http/https schemes,
  loopback/private/link-local hosts) with 400. Reject aliases that are reserved or use
  characters outside the permitted set. A taken alias → 409.
- **FR4 — Expire**: accept an optional expiry timestamp at creation. Past that instant
  the code stops resolving → 410 Gone. Omitted means the link never expires.
  Expiry is **immutable once set** — see [Design constraints](#design-constraints).
- **FR5 — Analytics**: record every redirect and expose per-code statistics — total
  clicks plus timestamp, referrer, and user-agent per click.

## Non-Functional Requirements
- NFR1: Performance - redirect served from cache, low-latency
- NFR2: Scalability - stateless app layer, state in Postgres/Redis
- NFR3: Reliability - degrade gracefully if Redis down (fallback to Postgres)
- NFR4: Security - rate limiting, parameterized queries only, secrets via env vars
- NFR5: Observability - structured logs, metrics, health checks (Actuator)
- NFR6: Maintainability - SOLID + agreed patterns, OpenAPI-documented contract
- NFR7: Testability - unit + integration tests (Testcontainers)
- NFR8: Data privacy - no raw client IPs persisted, hash/truncate
- NFR9: Durability - Postgres source of truth, Redis disposable cache

## Phase 1 — delivered
Core service plus security. **Authentication is done, not deferred**: every
create/manage/analytics endpoint now has a real ownership boundary — Google OAuth
("Sign in with Google") or guest login, both minting the same DB-backed session token
([ADR-005](./decisions.md#adr-005--authentication), M6 in [plan.md](./plan.md)). Auth
was originally scoped as Phase 2 alongside deletion, then pulled forward and delivered
early because deletion needs the ownership boundary auth provides, not the reverse.

## Phase 2 scope — caching + link deletion
What remains, now that auth exists to gate it. Deliberately sequenced after the core,
not cut.

### Caching
- Redis cache-aside in front of the redirect lookup, with graceful fallback to
  Postgres if Redis is unavailable (NFR1, NFR3, NFR9). See M3 in
  [plan.md](./plan.md).

### Link deletion
- **Deletion**: soft delete (`is_active = false`) so a link stops resolving → 410 Gone.
- **Now unblocked**: deletion without an ownership boundary would let anyone remove
  anyone's link — that boundary now exists (Phase 1, above), so deletion can proceed
  safely.
- **Constraint when added**: soft delete only. Retaining the row permanently retires the
  short code — reusing it would let an attacker hijack links already in circulation and
  would corrupt historical click attribution.
- **Known cost**: deletion is an external *event*, so it requires cache invalidation and
  inherits the cache-aside repopulation race (a concurrent reader can write a stale
  entry after the eviction). Bounded TTL from caching is the backstop.

## Design constraints
Carried from day one so Phase 2 stays additive rather than a rewrite.

1. **Expiry is immutable once set.** This is what keeps expiry a *property* travelling
   inside the cached value rather than an external event — so it needs no cache
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
7. **The service returns a record, not a `String`** — "gone" must be a new branch, not a
   signature change.

## Status
See [plan.md](./plan.md) for milestone status and per-requirement coverage.
