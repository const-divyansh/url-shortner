# AI-Assisted Engineering Process — Q&A

Maps directly to the 8-item evaluation rubric. Each answer is a summary — full
evidence and examples are in [`../process/`](../process/) (one file per item, linked
below).

## 1. Requirement Understanding — how was intent/ambiguity handled?
Every FR/NFR was normalized before coding (see `docs/requirements.md`). Where a
requirement was deliberately ambiguous (M5, "make it more reliable"), acceptance
criteria were derived and stated explicitly rather than guessed at silently. Vague bug
reports during the session ("+2 counter", "it's random") were converted into specific,
falsifiable claims before any fix was attempted.
→ [`../process/01-requirement-understanding.md`](../process/01-requirement-understanding.md)

## 2. Task Decomposition — how were requirements turned into sequenced work?
Milestone-level decomposition with explicit dependencies (M1→M2 hard dependency; M3
resequenced after M4 because a functional gap outranked a performance optimization).
Debugging work followed its own decomposition: reproduce → gather hard evidence → only
then decompose the code fix.
→ [`../process/02-task-decomposition.md`](../process/02-task-decomposition.md)

## 3. Codebase Reasoning (Brownfield) — how were impacts identified before changing a live system?
Before the SSE→WebSocket migration and the click-race fix, impacted modules, APIs, and
data flows were mapped out explicitly (event listeners, config, controller, frontend
API layer) — not discovered by trial and error mid-change.
→ [`../process/03-brownfield-reasoning.md`](../process/03-brownfield-reasoning.md)

## 4. AI-Assisted Execution — how was AI actually used, under what constraints?
Tasks were briefed with intent/constraints/acceptance criteria before implementation.
Debugging used disciplined, evidence-gathering prompts (a temporary diagnostic logger,
removed once it answered the question) rather than guesswork. Every AI-generated vs.
human-edited/rejected change is logged in `memories/repo/traceability-log.md`,
including AI arguments that were made and later withdrawn under scrutiny.
→ [`../process/04-ai-assisted-execution.md`](../process/04-ai-assisted-execution.md)

## 5. Engineering Output Generation — what was actually produced?
97 backend source files across a pattern-driven layered architecture (Strategy,
Repository, Cache-Aside, Observer, Chain of Responsibility, Facade, Factory Method),
182 backend tests, a documented REST/WebSocket API and schema, plus this documentation
suite.
→ [`../process/05-engineering-output.md`](../process/05-engineering-output.md)

## 6. Validation and Risk Control — how were risks and failure scenarios handled?
Every open risk is named at the milestone that introduces it (e.g., a dev-only secret
that fails *silently*, flagged explicitly as the dangerous kind). The click-race fix
was checked against a DB-failure scenario before being accepted, with a dedicated test
proving no false notification on a failed write.
→ [`../process/06-validation-risk-control.md`](../process/06-validation-risk-control.md)

## 7. Controlled Oversight — how did the engineer stay in control of AI output?
Brief-first and one-file-at-a-time review were enforced structurally, not just
intended — every file in this session's work (including this one) was approved before
the next began. A premature "this is resolved" claim was challenged and reopened
rather than defended. No commit/push happens without explicit human sign-off.
→ [`../process/07-controlled-oversight.md`](../process/07-controlled-oversight.md)

## 8. Final Engineering Summary — where's the wrap-up?
Plan/rationale, artifacts, risks/trade-offs, assumptions, and limitations, all in one
place, cross-linking every other item.
→ [`../process/08-final-summary.md`](../process/08-final-summary.md)

## Where's the guardrails/instruction file?
[`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — the
standing protocol every task in this repo runs under (brief-first, one-file-at-a-time,
mandatory traceability, quality gates, human sign-off), auto-loaded every session.
