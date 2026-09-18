# 7. Controlled Oversight

The engineer leads execution and approves every output; AI assists within tasks it is
explicitly given, under a standing protocol that keeps the human in the loop at every
decision point — not just at the end.

## Where this was structurally enforced, not just intended
- **Brief-first for high-impact changes**: schema changes, API contract changes, and
  stack/datastore choices required a stated intent + constraints + acceptance criteria
  and an explicit go-ahead before any code was written. Example: the SSE→WebSocket
  migration was briefed with impacted files and trade-offs before implementation
  started.
- **One file at a time, reviewed before the next**: every file in this documentation
  suite (this one included) was written, presented, and explicitly approved or sent
  back before the next file began — the same discipline used for the backend
  race-condition fix's files. Batch-reviewing ten files after the fact was rejected
  by design, per house rule, because issues compound across files if caught late.
- **Explicit choice between named options, not a unilateral pick**: when the
  click-update race condition's root cause was found, two fixes were presented —
  fix it in the backend (broadcast after persistence) or mitigate in the frontend
  (poll/re-fetch) — and the engineer chose the backend fix explicitly before any code
  changed.
- **Premature conclusions were challenged and corrected, not defended**: when an
  earlier reported symptom was declared "resolved" without conclusive proof, the
  engineer pushed back hard. The investigation reopened and continued until the actual
  root cause was found and proven with direct database evidence — the correction was
  accepted, not argued against. This is the oversight mechanism working exactly as
  designed: the AI does not get the final word on whether its own work is correct.
- **Human sign-off gate before anything lands**: no commit, push, or destructive
  command has been run without asking first, per the standing instruction. The M1
  traceability entry is explicit about this: "**Not yet reviewed by a human** — no
  commit made; awaiting sign-off."

## What "AI assists within tasks" meant in practice
The AI's role stayed scoped to the task it was given at each step — implementing a
briefed file, running an evidence-gathering diagnostic, or drafting a documentation
section — not deciding *what* the next task should be. Milestone sequencing
(`docs/plan.md`), scope decisions (what counts as "reliable" for the deliberately
ambiguous M5), and architectural direction (choosing the backend fix over the frontend
one) were engineer calls that the AI executed against, not calls the AI made
unilaterally.

## Traceability as the oversight record
[`memories/repo/traceability-log.md`](../../memories/repo/traceability-log.md) exists
specifically so oversight is auditable after the fact, not just exercised live. It
records AI arguments that were made and then **withdrawn under scrutiny** (e.g., an
initial claim that Hibernate could not infer indexes well, disproven and retracted) —
kept deliberately, because the log's value is in showing where oversight caught and
corrected something, not just in listing what shipped.

## Evidence
- [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — the
  enforced protocol (brief-first, one-file-at-a-time, human sign-off).
- [`memories/repo/traceability-log.md`](../../memories/repo/traceability-log.md) — the
  M1 "AI arguments withdrawn under scrutiny" section.
- This session's `docs/process/` files themselves — each approved individually before
  the next was written.
