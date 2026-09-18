# Start here

One page, in the order an interviewer needs it. Everything else in `docs/` is
supporting detail — linked from here, not required reading.

## 1. What is this?
A URL shortener: submit a long URL (optionally with a custom alias and expiry), get a
short code, get redirected (302) when that code is visited, and see per-code click
analytics live. Built from a
[ByteByteGo system-design brief](https://bytebytego.com/courses/system-design-interview/design-a-url-shortener).

## 2. Run it
```bash
make setup   # installs frontend deps, creates .env files from .env.example
make dev     # Postgres + Redis + backend (:8080) + frontend (:5173)
make test    # backend suite (mvn verify) + frontend typecheck/lint
```
No manual env editing needed for a local run — defaults match Compose. See
[`../README.md`](../README.md) for the full quickstart if anything above doesn't work
as-is.

## 3. Architecture, in one paragraph
Java 17 / Spring Boot 3, layered `controller → service (facade) → {generator
(strategy), repository, cache, event publisher (observer)}`. PostgreSQL is the
system of record (URLs, click analytics); Redis is planned for the redirect cache
(deferred — see §6) and already used for the rate-limit counters. React 19 + Vite
frontend, talking cross-origin over REST + WebSocket/STOMP for live click updates.
Docker Compose runs Postgres + Redis for dev; a prod overlay + nginx is scoped but not
built (Phase 2, §6). Full detail: [`requirements.md`](./requirements.md) (why),
[`plan.md`](./plan.md) (build order + outcomes), [`decisions.md`](./decisions.md)
(the "why X not Y" log), [`functionality.md`](./functionality.md) (what it verifiably
does today), [`HighLevelDesign.md`](./HighLevelDesign.md) (component diagram + create/redirect sequence
diagram).

## 4. The three required scenarios

**Greenfield — building M1–M4 from nothing.** Scaffold → core shorten/redirect → click
analytics, each milestone with stated intent/constraints/acceptance criteria before
any code, verified against those criteria after. Detail:
[`plan.md`](./plan.md), [`process/02-task-decomposition.md`](./process/02-task-decomposition.md).

**Brownfield — fixing a live bug without breaking working behavior.** A user report
("delete a link, then re-creating the same alias says it's taken") looked cosmetic but
was a real design choice (soft-delete blocks alias reuse to prevent hijacking) that
still needed a fix: an owner should be able to reclaim their *own* deleted alias while
strangers stay blocked. Required reasoning about which invariants could not move
(`expires_at`/`created_at` immutability) before touching code, landed as a
delete-and-recreate rather than a revive, with 6 new tests covering the reclaim,
cross-owner-block, and legacy-data cases. Detail:
[`decisions.md`](./decisions.md), [`process/03-brownfield-reasoning.md`](./process/03-brownfield-reasoning.md).

**Ambiguous — "delete should not be supported in non sign in user."** Read literally
this could mean "no session at all," which was already true. Clarified via direct
question that it meant guests specifically, and that the UI should hide the button,
not just have the API reject it. Normalizing the ambiguity into a concrete spec (403
for guests, button hidden client-side, API still authoritative) before writing code is
the point of this example. Detail:
[`process/01-requirement-understanding.md`](./process/01-requirement-understanding.md).

## 5. AI-assisted execution — the differentiator
Every task in this session ran brief-first, one-file-at-a-time, with disciplined
prompting and mandatory traceability
(`memories/repo/traceability-log.md`) — enforced structurally by
[`.github/copilot-instructions.md`](../.github/copilot-instructions.md), not just
intended. Three concrete examples of AI catching its own defects under scrutiny,
before they shipped further:
- **A rate-limiting bypass**: IPv4 loopback and IPv6 loopback were counted as separate
  buckets, doubling the effective limit — found while live-testing the user's own
  bug report, fixed with a dedicated key-normalizer + 10 tests.
- **A SOLID violation self-audit**: asked "are these following SOLID?", found and
  fixed 3 real issues (duplicated authorization logic, a misplaced dependency, an
  inline-if that should have been a policy object) rather than assuming the answer
  was yes.
- **A hardcoded-literal privilege-escalation risk**: a "hardcoded?" question surfaced
  that new identity-provider constants had been introduced but two of the three call
  sites still used the original string literals — three sources of truth for a value
  that gates delete permission. Fixed before it became a real bug.

Full rubric mapping (all 8 evaluation items):
[`process/README.md`](./process/README.md).

## 6. What's not built, and why
- **Redis redirect caching (M3)** — deferred deliberately, not forgotten: a cache is
  a second source of truth for data that can already be deleted/expired, and its
  failure mode is silent. The redirect path works correctly without it today; adding
  it is scoped as Phase 2.
- **Multi-instance scale-out** — click-draining and WebSocket notification are
  currently per-instance; running >1 backend instance is unproven, not merely
  untested. Flagged rather than hidden.
- **nginx / edge layer** — TLS termination, static-asset serving, and rate-limit
  layering ahead of the app are scoped (Phase 2) but not implemented; the app-level
  rate limiter is deliberately designed to keep working standalone either way.
- **Roles/scopes and editable link targets** — see
  [`functionality.md`](./functionality.md#11-not-built) for the complete, current
  list with rationale for each.

## 7. Testing, limitations, trade-offs
[`functionality.md`](./functionality.md) is the single source of truth for verified
behavior (every claim in it was checked live against a running instance, not assumed)
including a full API/error-code table and failure-mode table. Test counts and what's
covered vs. not (e.g., unit tests exist, Testcontainers-based integration tests do
not yet) are in [`plan.md`](./plan.md) M7.

## 8. Everything else, for depth
| Doc | What it's for |
|---|---|
| [`requirements.md`](./requirements.md) | Normalized FR/NFR list, Phase 1/2 scope |
| [`plan.md`](./plan.md) | Milestone-by-milestone journal: intent, outcome, bugs found |
| [`decisions.md`](./decisions.md) | ADR log — every "why X not Y" call, with trade-offs |
| [`functionality.md`](./functionality.md) | What the system verifiably does today, API + error tables |
| [`process/`](./process/) | One file per rubric item, with concrete evidence/examples |
| [`../memories/repo/traceability-log.md`](../memories/repo/traceability-log.md) | AI-generated vs. human-edited/rejected, with rationale |
