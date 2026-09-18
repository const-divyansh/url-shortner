# 2. Task Decomposition

Turning normalized requirements into an ordered list of actionable tasks, each with
explicit dependencies — so work can be sequenced correctly and status tracked
honestly, rather than discovered "as we go."

## What this looked like in practice
- **Milestone-level decomposition**: the full FR/NFR list was broken into 10
  milestones (M1–M10), each stating which requirements it closes, its dependencies on
  earlier milestones, and its own acceptance criteria — see
  [`docs/plan.md`](../plan.md). Example dependency chain: M6 (auth) depends on M2
  (core shorten/redirect) and M4 (analytics), because deletion without an ownership
  boundary is unsafe, so auth had to land before it could be picked up. Living
  document — `Status` moves `Not Started → In Progress → Done` as work actually
  happens, not on a fixed schedule (see [`plan.md`](../plan.md) itself, updated
  throughout this project rather than written once).
- **Feature-level decomposition, sequenced by blast radius**: the SSE→WebSocket
  migration (a single brownfield task) was broken into an ordered sub-task list before
  any file was touched: (1) add the WebSocket starter dependency, (2) extract a
  reusable token-authentication method usable by both HTTP and the new handshake path,
  (3) build the handshake interceptor depending on (2), (4) wire the STOMP config
  depending on (3), (5) rewrite the broadcaster to depend on the new messaging
  template, (6) remove the now-dead SSE endpoint and exception handler. Each step
  became reviewable and buildable in isolation rather than one large diff.
- **Bug-fix decomposition with a dependency on evidence, not just code**: the
  click-broadcast race fix was not sequenced as "write the fix" — it was sequenced as
  (1) reproduce with controlled, countable tests, (2) get server-side and
  database-level proof of the actual request/row count (not screenshots alone), (3)
  only once root cause was confirmed, decompose the fix itself into ordered file
  changes (`ClickRecord` → `UrlRedirectedEvent` → `ClickPublisher` →
  delete `ClickBroadcastListener` → `ClickDrainer` → dependent tests), because each
  later file's correctness depends on the field added in the first.

## Why this matters
Sequencing without dependency-awareness produces work that has to be redone: a schema
field discovered mid-way through touching five call sites, or a fix applied before the
root cause is actually confirmed. Explicit dependencies also mean the *order* of
review is defensible — a reviewer can trust that file 3 only exists because files 1–2
were already accepted.

## Evidence
- [`docs/plan.md`](../plan.md) — the milestone graph, dependencies, and per-milestone
  acceptance criteria; kept current as a living document rather than written once.
- [`.github/copilot-instructions.md` §Working Mode, item 2](../../.github/copilot-instructions.md)
  — the standing rule that enforces this: one file at a time, in dependency order,
  each reviewed before the next is written.
