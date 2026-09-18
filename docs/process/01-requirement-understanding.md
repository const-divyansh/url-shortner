# 1. Requirement Understanding

Interpreting intent, surfacing ambiguity, and turning a plain-language ask into a
precise, testable engineering problem — before any code is written.

## What this looked like in practice
- **Initial ask → FR/NFR**: the brief was one sentence ("shorten a URL, redirect on
  visit, show click stats"). Normalized into 5 functional requirements (FR1–FR5) and 9
  non-functional requirements (NFR1–NFR9) with explicit pass/fail conditions for each —
  see [`docs/requirements.md`](../requirements.md).
- **Explicit scope cuts, not silent ones**: anything dropped from v1 (auth, deletion)
  is named and justified in [Phase 2 scope](../requirements.md#phase-2-scope), not
  quietly absent.
- **Deliberately underspecified requirements were called out, not guessed at
  silently**: "make it more reliable" (M5) has no acceptance criteria in the original
  ask. Rather than invent a definition privately, the chosen definition — rate
  limiting, IP hashing, consistent error format, degrade-gracefully-under-Redis-outage
  — is written down as the interpretation, so it can be challenged before it's built.
  See [`docs/plan.md` §M5](../plan.md).
- **Vague bug reports normalized into falsifiable claims**: during the click-analytics
  work, reports like *"clicks count is not correct"* and *"always +2"* were not
  actionable as stated. Each was narrowed to a specific, testable hypothesis before any
  fix was attempted — e.g. "does HTTP HEAD or browser prefetch execute the redirect
  handler and record a click it shouldn't?" (yes — fixed in `ClickPublisher`), and
  later "is the WebSocket broadcast racing ahead of Postgres persistence?" (yes — fixed
  in `ClickDrainer`). Both started as an ambiguous symptom and ended as a specific,
  falsifiable, testable claim about one component.

## Why this matters
An AI assistant that starts generating code from an ambiguous prompt tends to encode
one arbitrary interpretation permanently. Normalizing first — and writing the
normalization down — means the interpretation is reviewable and correctable *before*
it's baked into a schema, an API contract, or a hundred lines of code.

## Evidence
- [`docs/requirements.md`](../requirements.md) — FR1–FR5, NFR1–NFR9, explicit deferred
  scope, explicit design constraints carried forward from day one.
- [`docs/plan.md` §M5](../plan.md) — the ambiguous-scenario definition, made explicit
  before implementation.
- [`docs/decisions.md`](../decisions.md) — ADRs record what was *actually asked for*
  vs. what was chosen, with alternatives considered.
