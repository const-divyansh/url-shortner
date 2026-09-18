# 4. AI-Assisted Execution

How AI was used across implementation, debugging, refactoring, test generation,
documentation, and review preparation — under explicit, enforced constraints, with a
traceable record of what was generated, edited, or rejected.

## The instruction file (guardrails)
[`​.github/copilot-instructions.md`](../../.github/copilot-instructions.md) is the
standing constraint document every task in this repo runs under. It is read by the AI
assistant automatically at the start of every session in this repo — it is not a
one-time briefing. Key enforced rules:
1. **Brief first** — state intent, constraints, and acceptance criteria; get explicit
   go-ahead before schema/API/stack changes.
2. **One file at a time** — write, stop, present for review, then move on. No batching
   unreviewed files.
3. **Mandatory traceability** — AI-generated vs. human-edited vs. rejected, logged per
   task.
4. **Quality gates** — tests pass, no new warnings, security checklist pass, before a
   task is called done.
5. **Flag risks inline** — named in the same task that introduces them, not deferred to
   a summary.
6. **Human sign-off** — the engineer approves before any commit, push, or destructive
   command.
7. **Living docs** — `docs/plan.md` status is updated as work happens, not written once.

## How tasks were defined for the AI
Every non-trivial task followed the same shape: **intent** (what problem, in plain
terms), **constraints** (what must not change — e.g. "same function signature so no
frontend caller needs touching"), **acceptance criteria** (a concrete, checkable list),
and **technical context** (which files, which existing patterns to match). Example —
the SSE→WebSocket migration was briefed with the exact file list, the specific
constraint that `subscribeToClickEvents`'s signature must not change (so
`AnalyticsView.tsx` needed zero edits), and the tradeoffs (why WebSocket over SSE) laid
out *before* implementation began, with explicit go-ahead requested and given.

## Disciplined prompting, iterative refinement
Debugging work in particular used iterative, evidence-gathering prompts rather than a
single "fix it" instruction:
- A vague symptom ("+2 counter", "getting complex") was decomposed into a specific,
  falsifiable hypothesis before any code was touched.
- Each hypothesis was tested with direct evidence — a temporary, clearly-labeled
  diagnostic log line in `RedirectController`, cross-referenced against raw
  `click_events` rows via direct SQL queries — rather than accepted on inspection
  alone.
- The diagnostic code was explicitly temporary (commented as such at insertion) and
  was removed in its own reviewed step once it had answered the question, verified by
  a full recompile and test run afterward.
- When an early conclusion ("this is resolved") was challenged, the investigation
  continued rather than being defended — leading to the actual root cause (a
  cross-component race condition, see
  [`03-brownfield-reasoning.md`](./03-brownfield-reasoning.md)).

## Secure AI usage
- No secret values were ever generated or committed; all credentials remain
  environment-variable-driven per the security checklist.
- A tool-layer redaction artifact was identified mid-session (template-literal text
  resembling `Bearer ${token}` was being masked as `******` in tool output even though
  the underlying source contained no real secret) — this was recognized as a display
  artifact, not treated as a discovered secret, and worked around with an alternative
  editing method rather than ignored or worked around unsafely.
- Every generated dependency addition (`@stomp/stompjs`, `spring-boot-starter-websocket`)
  was a named, intentional choice stated in the brief, not an incidental import.

## Traceability
- [`memories/repo/traceability-log.md`](../../memories/repo/traceability-log.md) is the
  detailed, per-session log of AI-generated vs. human-edited vs. rejected changes,
  including arguments the AI made that were later withdrawn under scrutiny (see its
  M1 entry — this is treated as valuable signal, not something to omit).
- That log lives outside version control (`memories/` is gitignored) by original
  design, which the log itself flagged as a gap before M10: a reviewable traceability
  narrative needed a committed home. **This `docs/process/` folder is that committed
  narrative** — it resolves that flagged gap by giving the traceable record of AI
  usage a place in the reviewable repo history, while the detailed working log
  remains available as supporting evidence.

## Evidence
- [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — the
  enforced protocol itself.
- [`memories/repo/traceability-log.md`](../../memories/repo/traceability-log.md) — the
  detailed per-session provenance log.
