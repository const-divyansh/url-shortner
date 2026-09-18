# Key Decisions — Q&A

Full detail (12 ADRs with considered/rejected/trade-off analysis):
[`../decisions.md`](../decisions.md). This is the top ~10, condensed to one line of
"why" each.

## Why Java 17 + Spring Boot 3, not Node or Python?
Mature, batteries-included ecosystem for the "production-grade" bar this exercise
asks for — Spring Security, Data JPA, Actuator, Testcontainers are first-class instead
of hand-rolled. Trade-off accepted: more boilerplate than Node, slower iteration than
Python.

## Why PostgreSQL + Redis, not Postgres-only or NoSQL?
The redirect path is the hottest, most latency-sensitive path — Redis gives O(1)
cache-aside lookups without touching Postgres on every click. Postgres stays because
`short_code` needs a strong uniqueness constraint and analytics benefits from SQL
aggregation. Not NoSQL: uniqueness and aggregation are relational strengths.

## Why 302 (temporary) redirect, not 301 (permanent)?
301 is cached indefinitely by the browser — after the first click, every repeat visit
never reaches our server again, which silently breaks click analytics (FR5). 302
guarantees every click is observable. Trade-off (every click hits the server) is why
caching exists at all.

## Why random Base62 short codes instead of sequential IDs or a URL hash?
Sequential IDs are enumerable — an attacker counts and scrapes every code ever issued.
A hash of the URL is deterministic, which forces deduplication and blocks per-code
expiry/analytics/ownership. Random Base62 is neither predictable nor deterministic;
collisions are handled by attempting the insert and catching the DB's own unique-
constraint violation (racy and wasteful to pre-check instead).

## Why no URL deduplication (same URL submitted twice → two different codes)?
Because this system has expiry, per-code analytics, and (eventually) per-owner
deletion — all of which need independent state per submission. A shared code would
merge two different owners' click counts or let one owner's deletion break another's
still-active link.

## Why one `urls` table for both generated codes and custom aliases, not two tables?
Uniqueness would have to hold *across* two tables, which no single Postgres constraint
can express — the only alternative is an application-level check-then-insert race that
can let two different targets claim the same code (a link-hijack bug, not just an edge
case).

## Why plain `schema.sql`, no Flyway/Liquibase?
This project has no production deployment yet, no data that can't be lost, and a
two-change schema history. A migration framework's value (versioned, checksummed,
rollback-safe changes to data you can't lose) doesn't apply yet. Explicitly flagged to
revisit once real deployments/data exist.

## Why Google OAuth + guest login, not a full username/password system or plain API keys?
Started as a self-issued API key (simplest possible ownership boundary), but that
bootstrap endpoint was open and unauthenticated by construction — anyone could mint a
key silently. Replaced with real identity (Google OAuth) plus an explicit, user-chosen
guest option — both mint the same opaque, DB-backed, revocable session token, so no
per-request authentication code had to change.

## Why React + Vite for the frontend, not a static page or no frontend at all?
Makes the working system demonstrable end-to-end without inflating scope (2 screens
only). `react-i18next` was dropped as disproportionate for two screens — user-facing
strings are centralized in one module instead, so adding real i18n later is a swap,
not a rewrite.

## Why was the live-update mechanism moved from SSE to WebSocket this session?
Not a requirement change — an architectural upgrade to support bidirectional,
STOMP-addressable per-owner routing more cleanly. See
[`../process/03-brownfield-reasoning.md`](../process/03-brownfield-reasoning.md) for
the full impacted-module analysis.

## Why was the click-broadcast logic changed to fire only after DB persistence?
The original design notified the frontend synchronously, straight off the redirect
event — before the click was actually written to Postgres. That's a race: the
frontend could be told "refresh" before the data it would fetch existed yet.
Broadcasting from inside the persistence step itself (after a successful save) makes
"notified" and "queryable" the same guarantee. See
[`../process/06-validation-risk-control.md`](../process/06-validation-risk-control.md).
