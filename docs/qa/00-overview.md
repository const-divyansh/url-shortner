# Overview — Q&A

## What is this project?
A URL shortener service: submit a long URL, get a short code back, get redirected when
that code is visited, and see click analytics per code.

## What's the stack?
- **Backend**: Java 17, Spring Boot 3.3.4, Maven
- **Datastore**: PostgreSQL (canonical storage) + Redis (redirect cache + async click buffer)
- **Frontend**: React 19, Vite, TypeScript
- **Infra**: Docker Compose (Postgres + Redis)

## How do I run it?
```bash
make setup   # installs frontend deps, creates .env files from .env.example
make dev     # starts Postgres + Redis, then backend (:8080) + frontend (:5173)
```
No manual env editing needed — defaults work out of the box for a local run.
See [`../requirements.md`](../requirements.md) "Design constraints" if you want the
full detail; that's not repeated here.

## How do I run the tests?
```bash
make test    # backend suite (mvn verify) + frontend typecheck/lint
```

## What should I look at, and in what order?
1. **This folder (`docs/qa/`)** — concise Q&A, read this first.
2. [`04-ai-process.md`](./04-ai-process.md) — how AI was used, mapped to the 8-item
   evaluation rubric directly (likely the most relevant one for this review).
3. [`02-plan-and-milestones.md`](./02-plan-and-milestones.md) — what was built, in what
   order, and why.
4. [`03-key-decisions.md`](./03-key-decisions.md) — the significant "why X not Y" calls.
5. For full depth beyond the summaries here: [`../plan.md`](../plan.md) (milestone
   journal), [`../decisions.md`](../decisions.md) (full ADR log), and
   [`../process/`](../process/) (the detailed, evidence-linked version of file 2 above).

## Is this finished?
Core flow (shorten, redirect, validate, expire, analytics, auth) is done and
live-verified. Two things remain: Redis caching (currently deferred — the redirect
path works correctly without it, just not yet cache-accelerated) and link deletion.
See [`02-plan-and-milestones.md`](./02-plan-and-milestones.md) for exact status.
