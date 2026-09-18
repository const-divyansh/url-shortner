# URL Shortener — Engineering Guidelines

## Working Mode (AI-Assisted Execution Protocol)
Every task in this repo follows this loop — do not skip steps:
1. **Brief first**: before generating code, state intent, constraints, and acceptance
   criteria for the task. Get explicit go-ahead before high-impact changes (schema,
   API contract, stack/datastore choices).
2. **One file at a time**: write a single file, then stop and present it for review
   before moving to the next. Do not batch multiple new files into one step, even when
   they are related and the design is already agreed. Reviewing a ten-file batch after
   the fact is not review — issues are cheapest to correct in the file that introduces
   them, before the same assumption is repeated across the rest.
   - Exception: trivial, mechanical companions to an already-reviewed file (for example
     a one-line config key the reviewed file reads) may accompany it, stated explicitly.
3. **Traceability**: after generating code, note what was AI-generated vs. human-edited
   or rejected (and why) in `/memories/repo/traceability-log.md`. Never silently
   overwrite a human edit.
4. **Quality gates before calling a task done**: tests pass, no new compiler/linter
   warnings, and a manual pass against the security checklist below.
5. **Flag risks inline**: when a task introduces a real risk (concurrency, cache
   invalidation, abuse surface, data privacy), name it and its mitigation in that same
   task — don't defer everything to a final summary.
6. **Human sign-off**: the engineer approves before commit/push. Never commit, push,
   or run destructive commands without asking first.
7. **Keep `docs/plan.md` current**: it is a living document, not a one-time artifact.
   Update a milestone's `Status` (Not Started → In Progress → Done) as work on it
   starts/finishes. Add new milestones there (with rationale in `docs/decisions.md`)
   instead of tracking scope changes only in chat.

## Architecture
Monorepo: `backend/` (Java API) and `frontend/` (React client) side by side, with
shared `docs/` and a root `Makefile` that orchestrates both. Maven commands must be
run from `backend/`, npm commands from `frontend/`.

- Java 17 + Spring Boot 3 (Maven), in `backend/`
- PostgreSQL: canonical storage (urls, click analytics)
- Redis: redirect cache (cache-aside) + async click-event buffer
- Docker Compose: `docker-compose.yml` (dev), `docker-compose.prod.yml` (prod overlay)
- Layering: `controller -> service (facade) -> {generator (strategy), repository, cache, event publisher (observer)}`
- Frontend: React 19 + Vite + TypeScript (`frontend/`), separate dev server calling the API cross-origin (CORS configured in `CorsConfig`)

## Frontend Rules (`frontend/`)
- **Nothing is hardcoded.** No literal URLs, ports, endpoint paths, magic numbers, or
  user-facing strings inline in components.
  - API base URL comes from a Vite env var (`VITE_*`), never a literal.
  - Endpoint paths live in one API module; components call functions, not URLs.
  - User-facing text lives in one strings module, imported by key. No i18n library —
    deliberately dropped as disproportionate for two screens — but the strings stay
    centralised so adding one later is a swap, not a rewrite.
  - Limits and defaults (page size, debounce, retry counts) are named constants, not
    numbers scattered through JSX.
- **Never commit `.env`.** Provide `.env.example` and keep real values out of git.
- The frontend is a client, not a source of truth: it must not re-implement validation
  rules that the API owns. It may improve the experience (disable a button, show a
  hint), but the API's response is authoritative.

## Design Principles
- SOLID is mandatory — see `/memories/repo/architecture-decisions.md` for rationale.
- Patterns in use: Strategy (short-code generation), Repository (persistence),
  Cache-Aside (Redis), Observer (click analytics via Spring events), Chain of
  Responsibility (request validation), Facade (service layer), Factory Method
  (generator bean selection).
- Don't introduce a new pattern without stating which SOLID principle or concrete
  problem it solves.

## Security Checklist (apply to every task touching input/output/storage)
- No raw SQL string concatenation — JPA/parameterized queries only.
- Validate and normalize all external input (URL format, alias length/charset).
- Rate-limit the redirect and creation endpoints.
- Never store raw client IPs for analytics — hash or truncate.
- Secrets via environment variables only, never hardcoded.

## Build and Test
Infrastructure must be up before the app: the datasource and Redis are required at startup.

```bash
make setup    # install frontend deps, create .env files
make dev      # Postgres + Redis + API + frontend together
make test     # backend suite + frontend typecheck/lint
make help     # every target
```

Prefer these over raw `mvn`/`npm`/`docker compose`: the Makefile is the single place
that knows the correct working directory for each toolchain. Run `make test` before
calling any task done.

Config comes from the environment; copy `.env.example` to `.env` for local overrides.
Defaults in `application.yml` already match the Compose defaults, so no env setup is
needed for a plain local run.

Startup must be free of WARN/ERROR lines — treat any new warning as a quality-gate
failure.

## Scenario Coverage
This repo must demonstrate three scenario types for the assignment: greenfield
(initial build), brownfield (a later enhancement/refactor/fix), and ambiguous (a
deliberately underspecified requirement). Each should show decomposition → execution
→ validation explicitly, not just the resulting code.
