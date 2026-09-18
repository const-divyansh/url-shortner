# Plan & Milestones — Q&A

Full detail (per-milestone decisions, acceptance criteria, outcomes, bugs found in
testing): [`../plan.md`](../plan.md). This is the condensed version.

## What's the current status, at a glance?
| # | Milestone | Status | Scenario tag |
|---|---|---|---|
| M1 | Foundation (Maven scaffold, Docker Compose, health check) | ✅ Done | — |
| M2 | Core shorten + redirect | ✅ Done | Greenfield |
| M3 | Caching (Redis cache-aside) | ⏸ Deferred | Phase 2 |
| M4 | Analytics (click tracking) | ✅ Done | Brownfield (extends M2) |
| M5 | Reliability & security (rate limiting, error format) | ⬜ Not started | Ambiguous |
| M6 | Auth + link deletion | 🟡 Auth done; deletion open | Phase 2 (deletion part) |
| M7 | Tests (integration/Testcontainers) | ⬜ Not started | — |
| M8 | Containerization (prod overlay) | ⬜ Not started | — |
| M9 | Frontend (React + Vite) | ✅ Done | — |
| M10 | Docs & final summary | 🟡 In progress | — (this doc is part of it) |

Plus, this session, two **post-M9 enhancements** on the already-shipped system: a
live-update mechanism migrated from SSE to WebSocket/STOMP, and a click-notification
race-condition fix (see [`03-key-decisions.md`](./03-key-decisions.md) and
[`../process/03-brownfield-reasoning.md`](../process/03-brownfield-reasoning.md)).

## Why this order?
- **M1 → M2** is the only hard dependency chain (nothing works without the scaffold).
- **M3 (caching) was deliberately resequenced after M4 (analytics)**: caching is a
  performance optimization with no measured problem yet, while analytics was still an
  *unmet functional requirement* (FR5) — closing a functional gap took priority over
  optimizing a working path. This ordering also surfaced a constraint that's easy to
  miss the other way round: a cache hit must still record a click.
- **Auth (M6) was pulled forward ahead of M5 (rate limiting)**: rate limiting without
  an ownership boundary has nothing meaningful to key off; auth needed to exist first.

## What are the three required scenario types, and where do they show up?
- **Greenfield** — M2: core shorten/redirect built from nothing.
- **Brownfield** — M4: analytics added to an already-working service; also this
  session's SSE→WebSocket migration and click-race fix, both changes to a live system.
- **Ambiguous** — M5: "make it more reliable" has no given acceptance criteria; the
  criteria (rate limiting, IP hashing, consistent error format, Redis-outage
  resilience) were derived from what "unreliable" concretely threatens.

## What's genuinely left before this is "done"?
1. M3 — Redis caching (deferred, not blocked; correctness doesn't depend on it).
2. M5 — rate limiting and a consistent error format (not started).
3. M6 remainder — link deletion (soft delete), now unblocked since auth exists.
4. M7 — Testcontainers-based integration tests (unit tests exist; integration
   coverage is the gap).
5. M8 — prod containerization overlay.

## Where's the evidence this isn't just a plan on paper?
Every "Done" milestone in the table above has a matching **Outcome** entry in
[`../plan.md`](../plan.md) with concrete verification (test counts, curl smoke tests,
specific bugs found and fixed) — not just a checkbox.
