# Engineering Process Documentation

This folder is the evidence trail for how this project was built: not just what the
code does, but how requirements were understood, decomposed, executed with AI
assistance under explicit guardrails, and validated. Each file below answers one part
of that story with a few bullets and links to the concrete artifacts (docs, code,
tests, commits) that back the claim up.

| # | Area | What it covers |
| - | --- | --- |
| 1 | [Requirement Understanding](./01-requirement-understanding.md) | Intent, ambiguity, normalization into an engineering problem |
| 2 | [Task Decomposition](./02-task-decomposition.md) | Requirements → sequenced, dependency-aware milestones |
| 3 | [Codebase Reasoning (Brownfield)](./03-brownfield-reasoning.md) | Impacted modules/APIs/data flows for later changes to an existing system |
| 4 | [AI-Assisted Execution](./04-ai-assisted-execution.md) | How AI was used, prompted, and constrained; traceability |
| 5 | [Engineering Output Generation](./05-engineering-output.md) | The production artifacts: code, schema, API, tests, docs |
| 6 | [Validation and Risk Control](./06-validation-risk-control.md) | Risks, trade-offs, failure scenarios, and how each was guarded |
| 7 | [Controlled Oversight](./07-controlled-oversight.md) | Where the engineer led, approved, or overrode the AI |
| 8 | [Final Engineering Summary](./08-final-summary.md) | Plan/rationale recap, artifacts, risks, assumptions, limitations |

## Relationship to the rest of `docs/`
This folder is the *process* record. The *product* record it draws on already exists
and stays authoritative:
- [`docs/requirements.md`](../requirements.md) — the FR/NFR list itself.
- [`docs/plan.md`](../plan.md) — the living milestone tracker (status, acceptance
  criteria, outcomes).
- [`docs/decisions.md`](../decisions.md) — the ADR log (what was chosen, what was
  considered, why).
- [`memories/repo/traceability-log.md`](../../memories/repo/traceability-log.md) — the
  per-task AI-generated / human-edited / rejected log.
- [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — the
  standing instruction file that constrains how AI is used in this repo (the
  "guardrails" document): brief-first, one-file-at-a-time review, mandatory
  traceability, quality gates, human sign-off before commit/push.

Each file in this folder is short by design — it points at the real evidence rather
than restating it.
