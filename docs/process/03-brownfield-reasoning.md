# 3. Codebase Reasoning (Brownfield)

Before touching an existing system: identify which modules, APIs, and data flows are
actually impacted, and demonstrate that the change fits the system's existing
architecture rather than fighting it.

## Case study A — Analytics added to an existing shorten/redirect service (M4)
Analytics (FR5) was added *after* the core shorten/redirect path (M2) already existed
and was working. Reasoning before writing code:
- **Impacted module**: `RedirectController` — the one existing call site that needed a
  single new line (`clickPublisher.publish(...)`), not a rewrite.
- **New modules, fitted to the existing pattern**: click recording used the Observer
  pattern already established for the codebase's event handling, rather than
  introducing a new mechanism — see [`docs/plan.md` §M4](../plan.md).
- **Data-flow constraint identified up front**: analytics must never add latency to,
  or be able to fail, a redirect. That constraint drove the architecture (async
  buffer + scheduled batch drain) before any code was written, not after a slow
  redirect was observed in testing.
- **A brownfield-specific bug this reasoning caught**: `ddl-auto: validate` rejected a
  column type mismatch against an already-existing dev table — Postgres's own DDL
  guard, working as intended. Documented as a brownfield-specific failure mode, since
  a greenfield build would never hit it (no pre-existing table to conflict with).

## Case study B — SSE → WebSocket migration (this session)
An existing, working live-update feature (Server-Sent Events) had to be replaced with
WebSocket/STOMP without changing its externally-observed behavior (owner sees their
own click counts update live, with no per-client ID plumbing).
- **Impacted modules identified before writing code**: `pom.xml` (new dependency),
  `SessionTokenRequestAuthenticator` (had to expose a reusable auth method for a second
  transport), a new `WebSocketAuthHandshakeInterceptor` and `WebSocketConfig`,
  `ClickBroadcaster` (full rewrite), `UrlController` and `GlobalExceptionHandler`
  (dead-code removal), and the frontend's `api.ts`/`config.ts`.
- **Data-flow constraint carried over from the old design**: native WebSocket, like
  `EventSource` before it, cannot send an `Authorization` header on the handshake — the
  existing SSE auth workaround (token as a query parameter, verified by a server-side
  interceptor before upgrade) was recognized as still valid and reused, rather than
  re-solved from scratch.
- **Owner-routing preserved architecturally**: the original SSE design's key property —
  the frontend never needs to know its own owner ID — was preserved by building a
  custom `HandshakeHandler` that resolves a `Principal` from the authenticated
  handshake, so STOMP's `/user/queue/...` convention could do the routing.

## Case study C — Diagnosing a live-update race across three components
A single reported symptom ("click doesn't show without pressing Look up") required
tracing one data flow across three independently-evolved components before any fix was
attempted:
- `RedirectController` → publishes an event, synchronously, on the request thread.
- `ClickRecordingListener` (`@Async`) → enqueues to Redis, on a *different* thread,
  with no ordering guarantee relative to the next listener.
- `ClickBroadcastListener` (synchronous) → notified the frontend *immediately*, racing
  ahead of both the async enqueue and the later scheduled Postgres drain.

Identifying that these three components had never been reasoned about *together* as
one data flow — each was correct in isolation — was the actual root-cause finding, not
a defect in any single file. See
[`docs/process/04-ai-assisted-execution.md`](./04-ai-assisted-execution.md) for how
this was investigated with evidence rather than guesswork.

## Why this matters
In a brownfield change, the risk is never "can I write this code" — it's "does this
code interact correctly with everything already here." All three cases above turned on
identifying a cross-component data flow before writing a line of the fix.

## Evidence
- [`docs/plan.md` §M4](../plan.md) — the analytics brownfield milestone, decisions, and
  the found-in-testing note.
- `backend/src/main/java/com/urlshortener/event/` — `ClickRecord`, `ClickDrainer`,
  `ClickPublisher`, `ClickRecordingListener` (the components reasoned about together
  in Case study C).
- `backend/src/main/java/com/urlshortener/config/WebSocketConfig.java` and
  `backend/src/main/java/com/urlshortener/auth/WebSocketAuthHandshakeInterceptor.java`
  (Case study B).
