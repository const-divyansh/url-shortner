# Functionality Reference

What this service does today, as built. Behaviour here is taken from the code, not from
the original requirements — where the two differ, this file is the accurate one.

Companion documents, deliberately not repeated here:

| For | Read |
| --- | --- |
| Why a decision was made | [decisions.md](./decisions.md) |
| What was asked for | [requirements.md](./requirements.md) |
| How it was built, milestone by milestone | [plan.md](./plan.md) |
| Condensed review summary | [overview.md](./overview.md) |

---

## 1. What the service does

Turns a long URL into a short code, redirects visitors from that code to the original
address, and records every redirect so the owner can see how the link performed.

Four capabilities, in the order a user meets them:

1. **Sign in** — with Google, or as an anonymous guest.
2. **Shorten** — paste a URL, optionally choose the code and an expiry.
3. **Redirect** — anyone visiting the short code is sent on; the click is recorded.
4. **Analyse** — the owner sees click counts and history, updating live.

Owners see only their own links. The redirect itself is public: a short link is useless
if the recipient has to log in to follow it.

---

## 2. Identity and permissions

Two ways to sign in, both issuing the same kind of opaque session token, sent as
`Authorization: Bearer <token>`.

| | **Guest** | **Google** |
| --- | --- | --- |
| How | one click, no details asked | Google OAuth 2.0 (Authorization Code) |
| Verified by | nobody | Google |
| Create links | yes | yes |
| View own analytics | yes | yes |
| **Delete links** | **no** | **yes** |

**Why guests cannot delete.** A guest session is minted on demand for anyone who asks,
so it establishes continuity for one browser rather than identity. Deletion is
irreversible and affects everyone still holding the link, so it is reserved for an
identity someone actually vouched for. A guest attempting it receives `403` with
`auth.guest_forbidden` and a message naming the remedy — deliberately distinct from
`auth.forbidden`, because the link *is* theirs; they simply may not destroy it.

The frontend hides the Delete control for guests, but that is presentation only — the
API enforces the rule independently, because anything the browser decides can be
changed by whoever holds the browser.

**Sessions are server-side.** The browser holds a token; the matching record lives in
`owner_sessions`. If that record disappears — database reset, session revoked — the next
request returns `401 auth.invalid` and the app returns to the login screen rather than
silently appearing signed in.

---

## 3. Creating a short link

`POST /api/urls` — authenticated.

```json
{
  "targetUrl": "https://example.com/a/very/long/address",
  "customAlias": "spring-sale",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

`customAlias` and `expiresAt` are optional. Response `201`:

```json
{
  "shortUrl": "http://localhost:8080/spring-sale",
  "shortCode": "spring-sale",
  "targetUrl": "https://example.com/a/very/long/address",
  "createdAt": "2026-09-18T11:36:57.683983Z"
}
```

### Validation rules

Applied in order; the first failure is reported and nothing is stored.

| Rule | Requirement | Error code |
| --- | --- | --- |
| Present | `targetUrl` is required | `url.missing` |
| Syntax | must parse as a URL | `url.malformed` |
| Absolute | no relative paths | `url.not_absolute` |
| Host | must name a host | `url.no_host` |
| Scheme | `http` or `https` only | `url.scheme_not_allowed` |
| Length | at most 2048 characters | `url.too_long` |
| Host safety | must resolve, and must not be a private/loopback address | `url.host_not_allowed`, `url.host_unresolvable` |
| Alias charset | `A–Z a–z 0–9 _ -` only | `alias.charset` |
| Alias length | 3–32 characters | `alias.length` |
| Alias reserved | not `api`, `actuator`, `health`, `metrics`, … | `alias.reserved` |
| Expiry | must be in the future | `expiry.in_past` |

**Host safety is a security control, not tidiness.** Without it the service would
happily shorten `http://169.254.169.254/…` or `http://localhost:5432`, turning a public
endpoint into a way to reach machines behind the firewall.

**Reserved aliases** exist because the redirect is mapped at the domain root. Allowing
`api` as an alias would let a link shadow the service's own routes.

### Expiry

Optional, and **immutable once set** — the column is not updatable. A link whose expiry
has passed returns `410 code.expired` rather than `404`: it existed, and saying
otherwise would be false. Expiry is decided by PostgreSQL alone, never by a cache TTL.

### Short-code generation

Two strategies, switchable at runtime (see §7):

- **Random (Base62)** — default. Unpredictable; the same URL shortened twice yields two
  different codes.
- **Hash of URL** — deterministic and therefore enumerable. Offered because it is a
  common design, with that trade-off stated in the UI.

On collision the service retries with a new code (up to 5 attempts). It never checks
"is this code free?" first: two requests can both be told yes. The unique index is the
only component that can arbitrate, so its rejection is the collision signal.

**A requested alias is never substituted.** If `spring-sale` is taken, the response is
`409 alias.unavailable` — quietly returning a different code would defeat the point of
asking for one.

---

## 4. Redirecting

`GET /{shortCode}` — public, no authentication.

| Situation | Response |
| --- | --- |
| Live link | `302` to the target |
| Unknown code | `404 code.not_found` |
| Expired | `410 code.expired` |
| Deleted by its owner | `410 code.deleted` |

**302, not 301.** A 301 is cached indefinitely by browsers, so repeat visits would never
reach this service again and click counts would silently undercount.

**Expired and deleted are separate codes** even though both return 410, so logs and API
consumers can tell "this timed out" from "the owner removed it".

**Only successful redirects count as clicks.** A 404 or 410 fails before the click is
published, so a click means "a visitor was actually sent somewhere" rather than "someone
tried a code".

---

## 5. Analytics

`GET /api/urls/{shortCode}/analytics?page=0&size=20` — authenticated, owner only.
Returns total clicks, last click time, and a page of history (time, referrer, user
agent). Page size is capped at 100 so a large `size` cannot force an expensive query.

`GET /api/urls` lists the caller's own links with click counts, so earlier analytics can
be reopened without remembering short codes. Deleted links are excluded.

### How clicks are recorded

The redirect does not wait for analytics. The click is published to an async listener,
buffered in Redis, and written to PostgreSQL by a scheduled drain.

**Analytics is deliberately lossy so that redirects never are.** If Redis is unavailable
the click is dropped and logged, and the visitor is still redirected.

**Live updates.** The analytics screen subscribes over WebSocket (STOMP) and refreshes
when one of the caller's links is clicked. The notification carries no click data — only
a ping — and the browser then refetches from the real endpoint, so PostgreSQL stays the
single source of truth for the number displayed.

The broadcast fires **after** the click is persisted. Notifying first caused a real race
where the UI refetched before the row existed and displayed a stale count.

### Privacy

**Raw IP addresses are never stored.** Each is hashed with SHA-256 plus a secret pepper
supplied via the environment. A plain hash would be trivially reversible — IPv4 has only
~4.3 billion addresses — so the pepper is what makes the stored value meaningful to
protect. It must never be stored in the database beside the hashes it protects.

`X-Forwarded-For` is ignored unless the immediate peer is a configured trusted proxy;
otherwise any caller could forge their own address.

---

## 6. Deleting a link

`DELETE /api/urls/{shortCode}` — authenticated, owner only, **Google identities only**.

| Situation | Response |
| --- | --- |
| Owner deletes their live link | `204` |
| Guest session | `403 auth.guest_forbidden` |
| Someone else's link | `403 auth.forbidden` |
| Unknown code, or already deleted | `404 code.not_found` |
| No session | `401 auth.required` |

**Deletion is soft.** The row stays with `is_active = false`; it is never a SQL `DELETE`.
Two reasons: the short code must not be reissued to a stranger — a shared link is still
in circulation after deletion, and handing its code to someone else would silently
redirect everyone holding it — and click history keeps a valid owner reference.

**Already-deleted returns 404, the same as never-existed.** The caller cannot use the
response to tell the two apart, so repeated calls and probing leak nothing.

**The guest check runs before the link is looked up**, so a guest cannot use the
difference between 403 and 404 to discover other people's short codes.

> **Phase 2 note.** Deletion currently takes effect immediately because every redirect
> reads PostgreSQL. Once caching lands (§11), deleting a link **must evict its cache
> entry**, or a deleted link keeps redirecting until its TTL expires and the guarantee
> above quietly weakens to "revoked, eventually".

### Reclaiming your own alias

A deleted alias stays claimed against everyone else, **but its original owner may reuse
it.** The hijack risk that justifies retiring a code applies to a *different* party
taking it; the same owner could point the link anywhere in the first place.

When an owner reclaims an alias, the previous link's **click history is discarded**.
This is deliberate and lossy: those rows belong to the link that used to hold that code,
and carrying them forward would report a different URL's traffic as the new link's own.

---

## 7. Short-code strategy

`GET` / `PUT /api/shortcode/strategy` — authenticated. Reports and switches the active
generation strategy, surfaced in the UI so the algorithm is visible rather than
invisible server state.

**The switch is global**, not per-user: it affects every link created afterwards, by
anyone. Existing codes keep resolving, because lookup does not depend on which algorithm
produced them.

> **Known gap.** Guest sign-in is open and self-service, so anyone can obtain a session
> and select the enumerable `hash-url` strategy. Closing this needs a role/scope check.
> Recorded rather than hidden — see [plan.md](./plan.md) M6.

---

## 8. Rate limiting

Per-IP request budgets, counted in Redis.

| Scope | Default | Applies to |
| --- | --- | --- |
| `create` | 20 / minute | `POST /api/urls` |
| `redirect` | 300 / minute | `GET /{shortCode}` |

Exceeding a budget returns `429 rate_limit.exceeded` with a `Retry-After` header. A 429
without one invites an immediate retry, which is the behaviour the limit exists to
suppress.

**It fails open.** If Redis is unreachable, requests are **allowed** and a warning is
logged. A limiter outage must never become a service outage. The trade-off is explicit:
while Redis is down there is no limiting.

**Counted against a hashed, normalised address**, so limiting introduces no new privacy
surface. Normalisation matters: `127.0.0.1` and `::1` are one machine, and IPv6 is
counted by `/64` prefix because a subscriber is typically delegated the whole range and
could otherwise rotate addresses indefinitely to evade the limit.

**Known limits, deliberately accepted:**
- **Per-IP, not per-account.** Callers sharing an address share a budget.
- **Fixed window**, so up to 2× a limit is possible across a window boundary. This
  defends against sustained abuse, not a momentary burst.
- **No edge limiting yet.** nginx `limit_req` rejects a flood before it costs a
  servlet thread or a Redis round trip, which this limiter cannot — it pays the full
  request cost to say "no". Layered, not an alternative: nginx cannot read a bearer
  token and enforce a per-account quota. **Phase 2** — see §11.

Set `RATELIMIT_ENABLED=false` to remove limiting from the request path entirely — a
no-op limiter is wired in, so no Redis calls are made at all.

---

## 9. API summary

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/guest` | none | Start an anonymous session |
| `GET` | `/api/auth/google/start` | none | Begin Google sign-in (browser redirect) |
| `GET` | `/api/auth/google/callback` | none | Google's return leg |
| `GET` | `/api/auth/session` | required | Who am I, and am I a guest? |
| `POST` | `/api/urls` | required | Create a short link |
| `GET` | `/api/urls` | required | List my links |
| `DELETE` | `/api/urls/{code}` | required, non-guest | Delete my link |
| `GET` | `/api/urls/{code}/analytics` | required, owner | Click statistics |
| `GET` | `/api/shortcode/strategy` | required | Active generation strategy |
| `PUT` | `/api/shortcode/strategy` | required | Switch strategy (global) |
| `GET` | `/{code}` | none | Redirect |
| `GET` | `/actuator/health` | none | Health check |

The machine-readable contract is served at `/v3/api-docs`, with Swagger UI at
`/swagger-ui.html` (redirecting to `/swagger-ui/index.html`). The OpenAPI document is
the source of truth for exact request and response shapes and documented status codes.
This table stays as the human-readable companion describing what each route is for and
why some behaviours exist.

### Error format

Every failure returns the same shape, produced centrally so no controller invents its
own:

```json
{
  "code": "alias.unavailable",
  "message": "customAlias 'byte' is already taken",
  "timestamp": "2026-09-18T11:37:10.981398Z"
}
```

`code` is stable and safe to branch on; `message` is written for a person to read. The
frontend shows the API's message verbatim rather than restating validation rules, so the
two can never disagree.

| Code | Status | Meaning |
| --- | --- | --- |
| `url.*`, `alias.*`, `expiry.*` | 400 | Input rejected — see §3 |
| `request.malformed` | 400 | Body unparseable or a field has the wrong type |
| `auth.required` | 401 | No session supplied |
| `auth.invalid` | 401 | Session no longer valid — sign in again |
| `auth.forbidden` | 403 | Belongs to another owner |
| `auth.guest_forbidden` | 403 | Yours, but guests may not do this |
| `code.not_found` | 404 | No such code, or already deleted |
| `alias.unavailable` | 409 | Alias taken |
| `code.expired` | 410 | Expired |
| `code.deleted` | 410 | Deleted by its owner |
| `rate_limit.exceeded` | 429 | Over budget; see `Retry-After` |
| `code.generation_failed` | 500 | Could not allocate a code — retry |
| `internal_error` | 500 | Unexpected failure |

---

## 10. Behaviour when things fail

The service degrades rather than stopping. What survives a dependency outage:

| If this fails | Redirects | Creating links | Analytics | Rate limiting |
| --- | --- | --- | --- | --- |
| **Redis** | work | work | reads work; **new clicks are lost** | disabled (fails open) |
| **PostgreSQL** | fail | fail | fail | works |

PostgreSQL is the source of truth; Redis is disposable. Every Redis interaction —
click buffering, rate limiting — is written to absorb its own failures rather than
propagate them.

Note that redirects currently survive a Redis outage **because nothing caches them**;
every lookup reads PostgreSQL. Caching (§11) must preserve that column, not weaken it:
a cache miss or a Redis failure has to fall through to the database rather than fail
the redirect.

---

## 11. Not built — Phase 2

Stated plainly so the boundary is not mistaken for an oversight. Phase 1 delivered
correctness and security; **Phase 2 is the performance and scale story** — caching,
scalability, and nginx at the edge. Scope is defined in
[requirements.md](./requirements.md#phase-2-scope--performance-and-scale).

### Caching — the main Phase 2 item
Redis runs and is reported healthy, but **nothing caches the redirect lookup yet**.

Deferred deliberately, and after rate limiting rather than before it: a cache is a
second source of truth for data that can already be deleted and expired, and its
failures are silent and correctness-level — a deleted link that keeps redirecting.
Rate limiting only ever *rejects*, so it cannot corrupt a redirect or lose a click.

Its correctness contract is written before any code ([plan.md](./plan.md) M3):

1. The cached record carries `expiresAt` and the active flag, so a hit can answer
   `410` without a database trip.
2. `ttl = min(configured, untilExpiry)` — Postgres owns expiry, never the TTL (§3).
3. **Deleting a link evicts its entry immediately.** Without this, revocation becomes
   "revocation, eventually", and §6's guarantee quietly weakens to a TTL.
4. **A cache hit must still record a click**, or the redirect gets faster and the
   click silently disappears (§4, §5).
5. Ships disabled by default, enabled deliberately.

### Scalability
The application layer is already stateless — sessions, links, clicks and rate-limit
counters all live in Postgres or Redis — so a second instance needs no sticky sessions
and no shared filesystem.

**Not yet proven, and two things need checking first:** the scheduled click drain runs
per instance, so Redis must stay the arbiter of which instance takes a batch (`LPOP`
already provides this); and WebSocket notifications (§5) are delivered by the instance
holding the connection, so a click served by instance A must still reach a browser
connected to instance B.

### nginx at the edge
- **Rate limiting belongs here too.** `limit_req` rejects a flood before it costs a
  servlet thread, a database connection or a Redis round trip; the in-app limiter of
  §8 still pays the full request cost to say "no". They are layered, not alternatives
  — nginx cannot read a bearer token and enforce a per-account quota.
- TLS termination and serving the built frontend.
- **Required change when added:** the real client address moves into
  `X-Forwarded-For`, which this service ignores unless the peer is a configured
  trusted proxy (§5). Deploying behind nginx without setting
  `ANALYTICS_TRUSTED_PROXIES` collapses every visitor onto the proxy's address —
  analytics silently reports one visitor, and §8's per-IP budget becomes one global
  budget shared by everyone.

### Smaller gaps, not Phase 2 themes
- **Editing a link's target or expiry.** Expiry is immutable by design (§3); changing
  a target after sharing is a redirect-integrity question not yet answered.
- **Roles/scopes**, which the global strategy switch needs (§7).
