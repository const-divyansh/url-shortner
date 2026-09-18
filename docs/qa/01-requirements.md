# Requirements — Q&A

Full detail: [`../requirements.md`](../requirements.md). This is the condensed version.

## What was the ask?
Submit a long URL, get a short code back, be redirected when that code is visited, and
see click statistics per code.
([ByteByteGo — Design a URL Shortener](https://bytebytego.com/courses/system-design-interview/design-a-url-shortener)
was the reference used throughout).

## What are the functional requirements?
| # | Requirement |
|---|---|
| FR1 | Shorten a URL, optionally with a caller-supplied alias; codes are never reused |
| FR2 | `GET /{code}` redirects (302); unknown code → 404 |
| FR3 | Reject malformed/unsafe URLs (SSRF-style hosts, bad schemes) → 400; taken alias → 409 |
| FR4 | Optional expiry; past it → 410 Gone; immutable once set |
| FR5 | Record every redirect; expose per-code click stats (count, referrer, user-agent, timestamp) |

## What are the non-functional requirements?
Performance (cached redirects), scalability (stateless app layer), reliability
(graceful Redis-outage degrade), security (rate limiting, parameterized queries, env-var
secrets), observability (structured logs, health checks), maintainability (SOLID +
documented patterns), testability (unit + integration), privacy (no raw IPs stored),
durability (Postgres is the source of truth, Redis is disposable).

## What was deliberately cut vs. deferred?
**Cut** (not planned at all): CI/CD pipeline, Kubernetes, any PII beyond a hashed IP.

**Deferred, not cut** — currently:
- **Caching** (Redis cache-aside for redirects) — functionally complete without it;
  this is a performance layer, not a correctness gap.
- **Link deletion** — soft-delete, now unblocked since the ownership boundary
  (authentication) it depends on is done.

## Why were auth and deletion originally bundled, then split?
Deletion without an ownership check lets anyone delete anyone's link — so deletion was
scoped to ship only after auth existed. Auth was then pulled forward and delivered
(Google OAuth + guest login) ahead of schedule, which is why it's no longer grouped
with deletion under "Phase 2" — see [`../requirements.md`](../requirements.md#phase-2-scope--caching--link-deletion).

## What constraints were carried from day one to avoid a Phase 2 rewrite?
- Expiry is immutable once set (keeps it a cached *property*, not an event needing
  cache invalidation).
- Postgres decides expiry, never Redis TTL (Redis is disposable and may be down).
- Cache a structured record, not a bare string; version cache keys.
- Redis is reached through an interface, never `RedisTemplate` directly in a service.
