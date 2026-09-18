# 8. Final Engineering Summary

## Plan and rationale
The project was built milestone-by-milestone against explicit FR/NFRs
([`docs/requirements.md`](../requirements.md)), sequenced with dependencies in
[`docs/plan.md`](../plan.md), and deliberately covering all three required scenario
types: **greenfield** (M2 — core shorten/redirect from nothing), **brownfield** (M4 —
analytics added to an already-working service, plus this session's SSE→WebSocket
migration and click-race fix on top of a live system), and **ambiguous** (M5 —
"make it more reliable," where defining acceptance criteria *was* the task). Every
non-trivial technical choice has a named rationale in
[`docs/decisions.md`](../decisions.md) rather than being justified only in chat.

## Artifacts produced
- Working backend (Spring Boot 3, Java 17) and frontend (React 19/Vite/TypeScript),
  running via `make dev` against Postgres + Redis.
- 97 backend source files across a layered, pattern-driven architecture; 182 backend
  tests; a frontend typecheck + lint gate — both run together via `make test`.
- Full documentation set: requirements, plan, decisions (ADRs), a traceability log,
  and this `docs/process/` narrative mapping the work to each of the 8 required
  engineering competencies (files 1–7 preceding this one).
- A real production-code fix this session (click-broadcast race condition): 7 backend
  files changed, 1 file deleted, 3 new tests, verified via full suite + `make test`.

## Risks, trade-offs, and validation (full detail in file 6)
- Known, explicitly-flagged open risks: `ANALYTICS_IP_PEPPER`'s silently-failing dev
  default (owning decision deferred to M8), and the guest-login/strategy-switch
  self-service risk (mitigated by auth, not fully closed — needs role/scope gating).
- The race-condition fix traded a small bounded latency (the 200ms drain interval) for
  a correctness guarantee (notification only after data is truly queryable), validated
  with a dedicated failure-path test rather than assumed.
- Validation leaned on ground-truth evidence (direct DB row/timestamp cross-checks)
  over displayed/frontend state throughout, which is what let a red herring (React
  StrictMode double-invoking dev effects) be ruled out instead of chased.

## Assumptions
- No real users/production data exist yet, which justified drop-and-recreate schema
  changes (e.g. the M6 auth data-model redo) instead of in-place migrations — this
  assumption **must** be revisited before any real launch.
- The older product-record docs (`docs/requirements.md`, `plan.md`, `decisions.md`,
  the traceability log) remain the authoritative source of *what* was built and *why*;
  this `docs/process/` folder is the *how the work was carried out* narrative layered
  on top, not a replacement for them.

## Limitations
- This session's race-condition fix has been verified by the automated test suite but
  **not yet re-verified live** by the engineer against a running `make dev` instance —
  that end-to-end confirmation is the one open item before this specific piece of work
  is fully closed.
- M5 (rate limiting, consistent error format, Redis-outage resilience) and full
  link-deletion (M6) are not yet implemented — tracked as "Not Started"/"In Progress"
  in `docs/plan.md`, not silently dropped.
- No load/performance testing has been done against the caching or async-drain paths;
  correctness under normal and single-failure conditions has been checked, not
  behavior under sustained load.

## Index of supporting detail
| # | Topic | File |
| - | ----- | ---- |
| 1 | Requirement Understanding | [01-requirement-understanding.md](./01-requirement-understanding.md) |
| 2 | Task Decomposition | [02-task-decomposition.md](./02-task-decomposition.md) |
| 3 | Codebase Reasoning (Brownfield) | [03-brownfield-reasoning.md](./03-brownfield-reasoning.md) |
| 4 | AI-Assisted Execution | [04-ai-assisted-execution.md](./04-ai-assisted-execution.md) |
| 5 | Engineering Output Generation | [05-engineering-output.md](./05-engineering-output.md) |
| 6 | Validation and Risk Control | [06-validation-risk-control.md](./06-validation-risk-control.md) |
| 7 | Controlled Oversight | [07-controlled-oversight.md](./07-controlled-oversight.md) |
| 8 | Final Engineering Summary | this file |
