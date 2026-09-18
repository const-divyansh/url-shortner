# 6. Validation and Risk Control

Every non-trivial change names its risks and trade-offs in the same task that
introduces them, and defines how the risk is checked or bounded — not deferred to a
separate "hardening" phase.

## Risks flagged and tracked live (in `docs/plan.md`, updated as work happens)
- **Redis cache-stampede risk (M3)**: unknown short codes miss the cache every time
  and hit Postgres directly, so a flood of random-code requests bypasses the cache
  entirely. Flagged at the milestone that introduced caching, not discovered later.
- **Open, self-service key/strategy risk (M6)**: switching the short-code generation
  strategy is global, and — before real auth existed — anyone could mint a bootstrap
  key and flip it. Explicitly logged as *reduced, not removed* by adding auth, with a
  named follow-up (role/scope column) rather than treated as closed.
- **Dev secret that fails silently (M4)**: `ANALYTICS_IP_PEPPER`'s dev default
  (`dev-only-not-a-secret`) lets the app start and analytics work normally, while every
  stored click hash stays reversible by anyone reading the repo. Called out explicitly
  as **not** a fail-loud risk (the dangerous kind — nothing breaks to tell you), with
  an owning decision deferred to a named milestone (M8) rather than silently carried
  forward.
- **Cache-invalidation race (M6, deletion)**: soft-deleting a link and evicting its
  cache entry has a known race — a concurrent reader can repopulate the cache with the
  now-stale entry between eviction and delete completing. Bounded, not eliminated, by
  the existing TTL from M3 as a backstop; documented as a known cost rather than an
  unnoticed gap.
- **M5 ("make it more reliable") — an intentionally underspecified requirement**: with
  no acceptance criteria given, risk analysis *was* the task definition. Rate limiting,
  IP hashing, consistent error shape, and Redis-outage resilience were derived from
  what "unreliable" concretely threatens (abuse, privacy leakage, inconsistent client
  handling, and a single point of failure) rather than left to guesswork.

## Failure-scenario reasoning applied to this session's fix
The click-broadcast race-condition fix was explicitly checked against a **database
failure** scenario before being accepted:
- **Question asked and answered before the fix was called done**: if a Postgres write
  fails inside `ClickDrainer`, does the frontend get told to refresh anyway (a false
  notification)? Answer: no — the broadcast call sits *after* `repository.saveAll(...)`
  in code, and a failure there logs and returns early, never reaching the broadcast.
  This was verified with a dedicated test (`doesNotBroadcastOnPersistenceFailure`), not
  just reasoned about.
- **Trade-off made explicit**: moving broadcast to fire only after persistence trades a
  small amount of latency (bounded by the 200ms drain interval) for a correctness
  guarantee (notified ⇒ actually queryable). This was presented to the engineer as a
  named choice, not applied silently — see
  [`07-controlled-oversight.md`](./07-controlled-oversight.md).

## Validation discipline
- **Mutation-tested security guarantees, not just green tests**: the analytics privacy
  test and the header-forgery test were each confirmed to *fail* when the protection
  they check is deliberately removed — a green suite alone doesn't prove a security
  test is doing anything.
- **Ground-truth over displayed state**: this session's investigation used direct
  Postgres row counts and timestamps as the source of truth, not frontend counters or
  browser DevTools screenshots, which are one layer removed and can be misleading
  (React StrictMode's double-invoked dev-only effects were ruled out this way rather
  than mistaken for a real bug).
- **Quality gates enforced per task, not per release**: `mvn test` and `make test` were
  run after every substantive change this session, not batched to the end.

## Evidence
- [`docs/plan.md` §M3, M4, M5, M6](../plan.md) — risks flagged inline at each milestone.
- `backend/src/test/java/com/urlshortener/event/ClickDrainerTest.java` —
  `doesNotBroadcastOnPersistenceFailure`.
- [`docs/decisions.md`](../decisions.md) — trade-offs behind each ADR.
