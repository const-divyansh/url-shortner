# 5. Engineering Output Generation

The actual deliverables: code, API/schema definitions, tests, and documentation —
produced to a production-quality bar, not a demo bar.

## Code
- 97 main-source files, organized by layer (`controller`, `service`, `repository`,
  `cache`, `event`, `entity`, `config`, `auth`, `util`) with each layer's allowed
  dependencies documented in a `package-info.java` per package (added in M1, before
  any business logic existed).
- Design patterns applied deliberately, each tied to a concrete problem (not decoration
  — see [`docs/decisions.md`](../decisions.md) for the ADR behind each):
  - **Strategy** — short-code generation (`ShortCodeGenerator` implementations),
    swappable without touching the service layer.
  - **Repository** — persistence isolated behind Spring Data interfaces.
  - **Cache-Aside** — Redis in front of Postgres for redirect lookups.
  - **Observer** — click analytics via Spring application events, so the redirect path
    never depends on analytics succeeding.
  - **Chain of Responsibility** — request validation.
  - **Facade** — service layer as the single entry point per bounded operation.
  - **Factory Method** — generator bean selection at startup.

## API and schema
- REST surface: `POST /api/urls` (create), `GET /{code}` (redirect), `GET
  /api/urls/{code}/analytics` (click history), plus auth endpoints (Google OAuth,
  guest login) and a WebSocket/STOMP endpoint (`/ws`, topic
  `/user/queue/click-events`) for live analytics updates.
- Schema: `urls` and `click_events` tables, managed via `schema.sql` (no migration
  framework — a deliberate M1 decision, see ADR-008), with explicit column types and
  indexes matched to query patterns (e.g. unique index on short code).
- `ClickRecord`'s wire format (the Redis-buffered representation of a click event) is
  explicitly versioned (`clicks:v1` → `clicks:v2` this session) whenever its shape
  changes, so in-flight queued records are never silently misread by new code.

## Tests
- Backend: 182 tests (JUnit 5 + Mockito), covering unit-level service/listener logic
  and integration-level controller behavior, run via `mvn test`. Growth is
  documented per change — e.g. this session's click-broadcast fix added exactly the
  3 tests needed to cover its new behavior (broadcast-after-persist, no-broadcast-on-
  failure, null-owner-is-skipped), not a speculative pile of extra cases.
- Security-relevant behavior has dedicated tests, not just happy-path coverage — e.g.
  the analytics privacy guarantee (no raw IP ever persisted) and header-forgery
  rejection each have a test that was confirmed, via mutation (deliberately breaking
  the guarantee), to actually fail when the protection is removed.
- Frontend: `tsc -b --force` (typecheck) and `oxlint` as the quality gate, run via
  `make test` alongside the backend suite — no separate, easy-to-forget frontend test
  step.

## Documentation as a deliverable, not an afterthought
- [`docs/requirements.md`](../requirements.md) — FR/NFR source of truth.
- [`docs/plan.md`](../plan.md) — milestone-by-milestone execution record, kept live
  (status changes as work happens, not written retroactively).
- [`docs/decisions.md`](../decisions.md) — ADRs for every non-obvious technical choice,
  so "why" survives independently of the person who made the call.
- This `docs/process/` folder — the AI-assisted-engineering narrative itself.

## Evidence
- `backend/src/main/java/com/urlshortener/` — the 97-file package structure.
- `backend/src/test/java/com/urlshortener/` — the 182-test suite.
- [`docs/plan.md`](../plan.md) — per-milestone acceptance criteria and outcomes.
- [`docs/decisions.md`](../decisions.md) — ADR-008 (no migration framework) and the
  pattern-selection rationale referenced above.
