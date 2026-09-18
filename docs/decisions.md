# Architecture Decisions (ADR log)

Each entry: what we chose, what we considered instead, and why. This is the durable
"why" record — [plan.md](./plan.md) states *what* each milestone builds,
[.github/copilot-instructions.md](../.github/copilot-instructions.md) states the
conventions that follow from these decisions.

**Primary design reference**: [ByteByteGo — Design a URL Shortener](https://bytebytego.com/courses/system-design-interview/design-a-url-shortener).
We consult this throughout the build for the standard shape of this system — short-code
strategies, cache-aside redirects, data model, and scaling talking points. Where we
deviate, the deviation and its reason are stated explicitly. The substantive deviations
are ADR-004b (302 over 301), ADR-007 (random over sequential codes), and ADR-009
(no deduplication).

## Index
| ADR | Decision | Status |
| --- | --- | --- |
| [001](#adr-001-backend-language--framework--java-17--spring-boot-3) | Java 17 + Spring Boot 3 | Accepted |
| [002](#adr-002-datastore--postgresql--redis) | PostgreSQL + Redis | Accepted |
| [003](#adr-003-containerization--docker-compose-dev--prod-overlay) | Docker Compose + prod overlay | Accepted |
| [004](#adr-004-design-principles--solid--specific-patterns) | SOLID + specific patterns | Accepted |
| [004b](#adr-004b-redirect-status-code--302-over-301) | 302 over 301 | Accepted |
| [005](#adr-005--authentication) | API-key authentication | Proposed (Phase 2) |
| [006](#adr-006--frontend--react--vite) | React + Vite frontend | Accepted |
| [007](#adr-007-short-code-generation--random-base62-resolved-by-the-databases-unique-constraint) | Random Base62 codes; insert-and-catch; switchable strategies | Accepted |
| [008](#adr-008-schema-migrations--plain-schemasql-no-migration-framework) | `schema.sql`, no migration framework | Accepted |
| [009](#adr-009-no-url-deduplication--every-request-mints-a-new-code) | No URL deduplication | Accepted |
| [010](#adr-010-one-urls-table-for-both-generated-codes-and-custom-aliases) | One `urls` table for codes and aliases | Accepted |
| [011](#adr-011-the-collision-retry-loop-sits-outside-the-transaction-boundary) | Retry outside the transaction boundary | Accepted |
| [012](#adr-012-validation-posture--one-chain-allow-lists-fail-closed) | Validation posture: one chain, allow-lists, fail closed | Accepted |

Accepted ADRs appear first, in numeric order; proposed ones are grouped at the end
since they describe work not yet started.

## ADR-001: Backend language & framework — Java 17 + Spring Boot 3
- **Considered**: Node.js + TypeScript (Express/Fastify), Python (FastAPI), Java (Spring Boot)
- **Chosen**: Java 17 + Spring Boot 3
- **Why 17 specifically (not 21)**: 17 is the minimum LTS Spring Boot 3 requires and is
  the version actually available/pinned in the local dev environment — matching the
  toolchain we build and test against avoids environment drift between what's
  documented and what `mvn` actually compiles with.
- **Why**: mature, batteries-included ecosystem for the "production-grade" bar this
  assignment asks for — Spring Security, Spring Data JPA, Actuator, and Testcontainers
  are all first-class, so auth/validation/observability aren't hand-rolled from scratch.
- **Why not Node/TS**: faster to scaffold, but less structure out of the box for
  auth/validation — would mean building more of what Spring gives for free.
- **Why not Python/FastAPI**: great typing and fast iteration, but a weaker default
  story for enterprise-grade auth tooling than Spring Security.
- **Trade-off accepted**: more boilerplate/verbosity than Node; slower to iterate than Python.

## ADR-002: Datastore — PostgreSQL + Redis
- **Considered**: SQLite, PostgreSQL only, PostgreSQL + Redis, NoSQL (DynamoDB/Mongo)
- **Chosen**: PostgreSQL (canonical storage) + Redis (redirect cache + async click-event buffer)
- **Why not SQLite**: single embedded file, weak concurrent-write support, no server
  process — fine for a throwaway demo, wrong once we need concurrent click writes and
  a caching layer.
- **Why add Redis instead of Postgres-only**: the redirect path is the hottest,
  most latency-sensitive path in this system. Hitting Postgres on every click doesn't
  scale and isn't necessary — Redis gives O(1) cache-aside lookups and a buffer so
  analytics writes never block the redirect.
- **Why not NoSQL**: `short_code` needs a strong uniqueness constraint to guarantee no
  collisions — a classic relational strength. Analytics also benefit from SQL
  aggregation (`COUNT`, `MAX(occurred_at)`) instead of doing it in application code.
- **Trade-off accepted**: two services to run/operate instead of one; mitigated by
  Docker Compose and by designing the redirect path to degrade gracefully if Redis is
  down (see NFR3 in [requirements.md](./requirements.md)).

## ADR-003: Containerization — Docker Compose (dev) + prod overlay
- **Considered**: no containers, single Compose file, Compose + prod override, Kubernetes
- **Chosen**: Docker Compose base file + a prod overlay (`docker-compose.prod.yml`)
- **Why**: one-command dev loop (`docker compose up`) while keeping prod-only config
  (resource limits, restart policies, env values) isolated in a second file.
- **Why not Kubernetes**: overkill for a prototype — would consume the time budget on
  infra instead of the engineering the assignment is actually evaluating.
- **Trade-off accepted**: doesn't demonstrate orchestration-at-scale; explicitly out of
  scope for this exercise.

## ADR-004: Design principles — SOLID + Specific Patterns
- **Chosen patterns**: Strategy (short-code generation), Repository (persistence),
  Cache-Aside (Redis), Observer (click analytics via Spring events), Chain of
  Responsibility (request validation), Facade (service layer), Factory Method
  (generator bean selection).
- **Why these and not others**: each pattern solves one concrete problem already
  present in this system (see the mapping in copilot-instructions.md) — none are
  applied speculatively.
- **Why not something like CQRS/event sourcing**: the read/write shape of a URL
  shortener doesn't need it; it would add complexity with no corresponding problem to
  solve, which violates the "no pattern without a stated reason" rule for this repo.

## ADR-004b: Redirect status code — 302 over 301
- **Considered**: 301 (Permanent Redirect), 302 (Found/Temporary Redirect)
- **Chosen**: 302 for `GET /{shortCode}`
- **Why**: 301 is cached indefinitely by browsers/CDNs by default — after a client's
  first click, every subsequent click on that link is resolved from the browser's own
  cache and never reaches our server again. That breaks FR5 (click analytics): repeat
  visits from the same client would be invisible to us, undercounting clicks rather
  than just being an implementation nuance.
- **Why not 301 despite lower server load**: FR5 is a required functional requirement,
  not optional — a redirect strategy that silently corrupts our own analytics data
  isn't acceptable just to save load.
- **Trade-off accepted**: every click now hits our server. This is exactly why M3
  (Redis cache-aside on the redirect lookup, see ADR-002) exists — we absorb the load
  through caching the lookup, not by sacrificing analytics correctness.

## ADR-007: Short-code generation — random Base62, Resolved by the Database's Unique Constraint
- **Reference**: [ByteByteGo — Design a URL Shortener](https://bytebytego.com/courses/system-design-interview/design-a-url-shortener),
  "Hash function" section, Table 3.
- **Considered** (the reference presents the first two; we use a third):
  1. **Base62 conversion of a unique auto-increment ID** — no collisions possible, but
     codes are sequential and predictable, which the source itself flags as a security
     concern: an attacker enumerates every code ever issued just by counting.
  2. **Hash of the long URL, truncated to 7 characters** — unpredictable and fixed
     length, but *deterministic*: the same URL always yields the same code, which
     forces deduplication (see [ADR-009](#adr-009-no-url-deduplication--every-request-mints-a-new-code)).
  3. **Cryptographically random Base62** — unpredictable and non-deterministic, at the
     cost of a collision being possible in principle.
- **Chosen**: option 3 as the default, behind a `ShortCodeGenerator` interface
  (Strategy) so the algorithm is swappable without touching the service.
- **Why not option 1**: enumerability is a real risk here — analytics (FR5) and Phase 2
  deletion mean a short code gates per-owner data, so a sequential ID makes scraping
  the entire system trivial.
- **Why not option 2 as the default**: determinism is the problem, not the hashing.
  A pure function of the URL cannot issue two distinct codes for the same URL, which
  is incompatible with ADR-009. Note this is a *different* objection from the one
  against option 1 — a hash is not enumerable, so the security argument does not apply.
- **Code length**: 7 characters (`[0-9a-zA-Z]`) — `62^7 ≈ 3.5 trillion`, matching the
  reference's sizing.

### Collision handling: Insert-and-Catch, Not Check-Then-Insert
- **Chosen**: attempt the insert and treat a violation of the named unique constraint
  `urls_short_code_key` as the collision signal, retrying with an incremented attempt
  counter (bounded at 5).
- **Why not a pre-insert existence check**: it is both racy and wasteful. Two concurrent
  requests can each be told a code is free and both proceed — the unique index is the
  only component that can arbitrate, so we let it arbitrate. It also adds a round trip
  to every request to guard against an outcome that is near-unreachable (at 100M rows,
  one attempt collides with probability ~1 in 35,000).
- **Why not the reference's bloom filter**: the reference reaches for one because
  *"it is expensive to query the database to check if a shortURL exists for every
  request."* That cost only exists under check-then-insert. Removing the check removes
  the need for the filter — no probabilistic structure, no false-positive path, no
  memory overhead.
- **Trade-off accepted**: the retry loop must sit outside the transaction (a failed
  statement aborts the surrounding transaction in Postgres), and the exception handler
  must match the constraint *by name* so that unrelated integrity violations are not
  silently retried.
- **Why the bound exists**: not to absorb collisions — five consecutive collisions at
  100M rows is ~10⁻²³. It is a circuit breaker, so a generator that violates the
  vary-by-attempt contract fails loudly instead of looping forever.

### Multiple strategies, Switchable at Runtime
- **Chosen**: ship `base62-random` (default) and `hash-url`, selected by
  `app.shortcode.strategy` and switchable at runtime via `ShortCodeGeneratorFactory`
  (Factory Method).
- **Why safe**: the strategy governs creation only. Resolution is a lookup by
  `short_code` and is indifferent to which algorithm produced it, so codes minted under
  a previous strategy keep working — no migration, no dual-read path.
- **Why `hash-url` is included at all despite ADR-009**: it makes the comparison above
  demonstrable rather than asserted, and it is the sole justification for
  `GenerationContext` carrying `targetUrl` and `attempt` — without it those fields
  would be speculative.
- **Consequence**: a deterministic strategy collides on attempt 0 *by construction*
  whenever a URL is resubmitted, so its effective retry budget is one lower than the
  random strategy's. Mixing the attempt counter into the digest is what keeps the loop
  making progress; omitting it turns any duplicate URL into a 500.
- **Operational cost discovered in testing**: Hibernate logs every constraint violation
  at ERROR level (`SqlExceptionHelper`) *before* our handler can classify it, so each
  retried collision emits a WARN+ERROR pair even though it was handled correctly. With
  the default random strategy this is unreachable in practice and observed logs are
  clean; with `hash-url` it happens on every duplicate URL. The logger is deliberately
  **not** silenced — suppressing `SqlExceptionHelper` would also hide genuine integrity
  failures, which is a worse trade than tolerating noise from a non-default strategy.
  Anyone selecting `hash-url` should expect it.
- **Security note**: exposing strategy switching without authentication would let an
  attacker select an enumerable strategy and then scrape. Acceptable while the service
  is unauthenticated and local; must be secured alongside M6 auth before any real
  deployment.

## ADR-008: Schema migrations — plain `schema.sql`, No Migration Framework
- **Considered**: Flyway, Liquibase, Hibernate `ddl-auto: update`, plain `schema.sql`
- **Chosen**: a committed `backend/src/main/resources/schema.sql` executed by Spring Boot's SQL
  init, paired with `spring.jpa.hibernate.ddl-auto: validate`.
- **Context**: Flyway was originally on the dependency list (and briefly enabled in M1)
  without an ADR justifying it — it was a default, not a decision. Challenged during M1
  and removed.
- **Why not Flyway/Liquibase**: their value is versioned, checksummed, ordered migrations
  applied to a database that already holds data you cannot lose. This project has no
  production deployment, no existing data, no rollback requirement, and a total expected
  schema history of roughly two changes (`urls` at M2, `click_events` at M4). Paying a
  framework's concepts for that is scope we can't justify under this repo's own rule that
  nothing is introduced without a concrete problem it solves.
- **Why not `ddl-auto: update`**: this is the option we actively reject. It never drops or
  renames, diverges silently from what any long-lived database actually contains, and
  produces DDL that no one reviews. `validate` is kept precisely so that a mismatch between
  entities and the committed `schema.sql` fails fast at startup instead of being papered over.
- **Why `schema.sql` is sufficient here**: the DDL stays a reviewable artefact in git, and
  M7's Testcontainers tests run that same file against a fresh container — so tests exercise
  the real schema, which is the property that actually mattered in the Flyway argument.
- **Trade-off accepted**: `schema.sql` re-runs on startup, so every statement must be written
  idempotently (`CREATE TABLE IF NOT EXISTS`, etc.) to remain safe against the persistent
  `postgres-data` volume in `docker-compose.yml`. This is the one concrete thing Flyway would
  have handled for us, and it is a deliberate, cheap trade — M2 must enforce the idempotent
  style when it writes the first DDL. If this project ever grows real data or multiple
  environments, revisit this ADR: that is the point at which Flyway starts earning its keep.

## ADR-009: No URL deduplication — Every Request Mints a New Code
- **Considered**: return the existing code for a previously-seen URL (the reference's
  behaviour), versus always issuing a new one.
- **Chosen**: always issue a new code. The same URL submitted twice yields two
  independent short codes.
- **Why the reference can dedupe and we cannot**: it explicitly assumes *"shortened
  URLs cannot be deleted or updated"* and has no custom aliases and no expiry. Under
  those assumptions one row per URL is sufficient. We have expiry (FR4), per-code
  analytics (FR5), and Phase 2 deletion — each of which needs per-submission state:
  - **Expiry**: two callers cannot share one row if they want different expiry dates.
  - **Analytics**: sharing a code merges two campaigns' click counts into one figure.
  - **Deletion**: one caller deleting "their" link would silently break everyone else's.
- **Trade-off accepted**: more rows than strictly necessary, and no way to answer
  "how many distinct URLs do we store" without a scan. Both are acceptable; neither is
  a requirement.
- **If this is revisited**: deduplication is additive and nothing in the current design
  blocks it. It needs (a) a `target_url_hash` column plus an index — the raw column
  cannot be indexed directly, since a 2048-character value can exceed Postgres's
  ~2700-byte B-tree entry limit — and (b) a policy decision, because reuse is only
  valid when the new request specifies no custom alias and no expiry. Existing
  duplicate rows would keep working; only new requests would be affected.


## ADR-010: One `urls` table for both generated codes and custom aliases
- **Considered**: separate `urls` and `url_aliases` tables (the original plan), versus a
  single table with one `short_code` column.
- **Chosen**: a single table. A generated code and a custom alias are the same thing —
  a key in one redirect namespace — so one `UNIQUE(short_code)` constraint covers both.
- **Why two tables fails**: uniqueness would have to hold *across* both tables, which no
  single Postgres constraint can express. The only remaining option is to check both
  tables in application code before inserting — a check-then-act race. Concretely: a
  request generates `k7Fq2Xa` and inserts it into `urls`; concurrently another request
  asks for alias `k7Fq2Xa`, finds nothing in `url_aliases`, and inserts it there. Both
  succeed, both are "unique" in their own table, and `GET /k7Fq2Xa` now matches two
  rows with different targets. That is a link-hijack vector, not just an ambiguity.
- **Secondary benefit**: the redirect — the hottest path — is one indexed lookup rather
  than two queries or a `UNION`.
- **When the rejected design would be right**: if one URL could carry *many* aliases
  (vanity links such as `/spring-sale` and `/promo` for one target), that genuinely
  needs a child table. FR1 specifies a single optional alias chosen at creation, so the
  relationship is one-to-one and a column suffices.
- **Trade-off accepted**: an `is_custom` flag was considered to distinguish the two and
  deliberately omitted — the service already knows whether an alias was supplied, so the
  column would serve reporting curiosity rather than correctness.

## ADR-011: The collision-retry loop sits outside the transaction boundary
- **Context**: ADR-007 resolves collisions by attempting the insert and catching the
  unique-constraint violation. That interacts badly with a naive transaction boundary.
- **Chosen**: `UrlService` is deliberately **not** annotated `@Transactional`. Spring
  Data's `save`/`saveAndFlush` carry their own transaction, so with no outer transaction
  each attempt runs in a fresh one.
- **Why**: in PostgreSQL a failed statement aborts the entire surrounding transaction —
  every subsequent statement is refused with *"current transaction is aborted"*
  regardless of whether it would otherwise succeed. Had the loop been wrapped in one
  transaction, the first collision would poison it and attempts 1–4 would fail for a
  reason unrelated to their own codes. The retry mechanism would be inert, and the
  failure would surface only under concurrency.
- **Why this is recorded rather than left to the code**: adding `@Transactional` to a
  service method is the conventional, expected thing to do. Someone tidying up would add
  it; the code would still compile and single-insert tests would still pass, while
  retries silently stopped working. The absence is load-bearing, so it is documented
  both here and in the method's Javadoc.
- **Trade-off accepted**: creation is therefore not atomic across multiple writes. That
  is fine today — creation is a single insert — but if creation ever spans more than one
  table, the transactional unit must be introduced *inside* an attempt, never around the
  loop.

## ADR-012: Validation posture — One chain, Allow-lists, Fail Closed
- **All rules live in the validation chain; no bean-validation annotations on DTOs.**
  Splitting rules between annotations and the chain would let the two drift and would
  produce two differently-shaped 400 responses. One location, one error format.
- **Schemes are allow-listed (`http`, `https`), not block-listed.** Enumerating dangerous
  schemes is a losing game: anything omitted is permitted by default. `javascript:`
  matters most, since a redirect to one executes in the visitor's browser.
- **Host safety resolves DNS and checks every returned address.** Checking the literal
  host text is insufficient — anyone can point a public domain at a private address. A
  host resolving to both a public and a private address is refused.
- **Unresolvable hosts are rejected (fail closed).** If we cannot determine where a host
  points, we cannot establish that it is safe. The cost is that creation depends on DNS
  availability; that is accepted for a security control.
- **One message for every blocked host.** Distinguishing "loopback" from "private range"
  from "metadata endpoint" would let a prober map our network.
- **DNS sits behind a `HostResolver` interface.** Otherwise the most security-critical
  rule would be effectively untestable: verifying "a public domain resolving to a private
  address is blocked" would require owning a real domain that does so, and every test
  would need network access.
- **Known limitation (TOCTOU)**: resolution happens at creation. A domain resolving
  publicly today can be repointed at an internal address tomorrow and the stored link
  would still redirect there. Closing this requires re-resolving on every redirect —
  recorded as a gap rather than implied to be covered.
- **Rules are ordered cheapest-first, with the only I/O rule last**, and the chain stops
  at the first objection: later rules parse a URL that earlier rules have already
  accepted, so continuing would report errors derived from input already known invalid.

## ADR-005 : Authentication
- **Considered**: no auth, API-Key header, full JWT + login/registration, OAuth
- **Original decision (2026-09-18, superseded below)**: lightweight API-Key first
  (`X-Api-Key` header, hashed key storage), behind a pluggable auth boundary so
  OAuth or JWT could be added later without rewriting controllers and services.
- **Superseded (2026-09-18)**: replaced self-issued API keys with two explicit login
  paths — **Google OAuth** (Authorization Code) and **guest** — both minting the same
  underlying opaque, DB-backed session token via `Authorization: Bearer`.
  - **Why replace API keys rather than add OAuth alongside them**: the API-key
    bootstrap endpoint was open and unauthenticated by construction — anyone could
    mint one, silently, with no user awareness it had happened. That was flagged as a
    known weak point in the original slice (see Session 6/7 traceability). Google
    login is now the primary path with real identity; guest remains, but as an
    explicit, user-chosen action instead of an invisible default.
  - **Why not per-request JWT verification for our own sessions**: Google's id_token
    is itself a JWT, verified once at login via Google's `tokeninfo` endpoint. Our own
    session credential stays an opaque, revocable, DB-backed token — this needs one
    indexed lookup per request, and revocation is a single row delete, whereas a
    self-issued JWT session would need a denylist to be revocable before expiry
    anyway, which erases the "stateless" benefit it would otherwise buy.
  - **Why `tokeninfo` over local JWKS verification**: officially supported by Google
    for this exact purpose, and needs no JWKS fetch/cache/rotation code. Costs one
    extra HTTP round trip per login (never per request). A higher-traffic production
    system would verify the signature locally instead — noted as a deliberate,
    revisitable scope tradeoff for this project's size.
  - **Data model**: identity (`owners`: provider, external subject, email) is now
    split from credential (`owner_sessions`: token hash), so one owner can hold
    multiple valid sessions and a session can be revoked without touching the
    identity. The earlier bootstrap table conflated both into one row.
  - **Pluggability preserved**: `RequestAuthenticator`/`ActiveRequestAuthenticator`
    did not change at all — they only ever validated our own session token, never a
    provider's token directly, so adding Google as a second *login* mechanism needed
    no change to the per-request authentication seam.
- **Why not full JWT + login (still rejected)**: a full user/password/refresh-token
  subsystem remains disproportionate scope for a URL shortener prototype; Google
  OAuth gets real identity without building that.
- **Why not no auth at all**: Phase 2 link deletion and the analytics endpoint need
  an ownership boundary — leaving them open is a real gap, not a nice-to-have.
- **First protected surface**: `POST /api/urls`, `GET /api/urls/{code}/analytics`, and
  `GET /api/urls` require authentication; redirect (`GET /{shortCode}`) stays public.
- **Status**: accepted. Deletion remains pending inside M6; redirect stays public
  regardless of the provider. Roles/scopes and rate-limiting login endpoints remain
  explicitly out of scope for this round (see traceability log).

## ADR-006 : Frontend — React + Vite
- **Considered**: no frontend (API-only), static HTML/vanilla JS, React + Vite
- **Chosen**: React + Vite, 2 screens (create + analytics lookup), separate dev server
  calling the API cross-origin. All configuration via `VITE_*` env vars.
- **Why not vanilla JS**: UI state for form submission, error display and pagination
  would be hand-rolled for the same result — more effort, not less.
- **Why include a frontend at all**: makes the working prototype demonstrable
  end-to-end without inflating scope (2 screens only).
- **i18n dropped** *(2026-09-18)*: the original entry specified `react-i18next`, which
  was also part of the justification for choosing React. For two screens that argument
  does not hold — it is real setup cost for thin benefit. User-facing strings are
  instead centralised in one module, so introducing a library later is a swap rather
  than a rewrite. Note this weakens, but does not remove, the case for React over
  vanilla JS; the UI-state argument above is what now carries it.
- **Consequence — CORS**: a separate dev server makes every API call cross-origin, so
  the API must declare permitted origins (`CorsConfig`). Origins are configured and
  never wildcarded: `"*"` would let any site script requests against the API through a
  visitor's browser. Choosing instead to build the frontend into the Spring jar would
  have avoided CORS entirely by making everything same-origin; the conventional split
  was preferred for a normal frontend development workflow.
- **Status**: accepted.

## Where decisions live
- **`docs/decisions.md`** (this file) — the reviewable "why" record; part of the repo,
  linked from the architecture overview and final summary.
- **`docs/plan.md`** — the "what" per milestone, with FR/NFR coverage and verification.
- **`docs/requirements.md`** — the FR/NFR source list.
- **`.github/copilot-instructions.md`** — the conventions/guardrails that follow from
  these decisions (auto-loaded every session).
- **`/memories/repo/*`** — my own compressed recall notes across sessions; not a
  substitute for this file.
