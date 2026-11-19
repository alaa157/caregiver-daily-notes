# DEF-006 — Plan generation always failed: the prompt never stated the output schema

- **Severity:** Major (AI care-plan suggestions unusable with every tested model)
- **Component:** `backend/.../ai/PlanSuggestionService.java`, `StrictJsonParser`
- **Environment:** Spring Boot backend, OpenRouter provider, models `openai/gpt-4o-mini`, `openai/gpt-6.2-sol`, `anthropic/claude-sonnet-5`
- **Discovered:** 2026-11-18/19, backend log `plan_generation_failed reason=invalid_model_output` reproduced across all three models.

## Repro

`POST /api/plans/generate/byok` with valid BYOK credentials and a recipient with notes → HTTP 200 with `plan: null, aiUnavailable: true`; backend logs `invalid_model_output`.

## Evidence

Raw model output captured with the old prompt showed:

```json
{ "actions": [ { "actionId": "ESCALATE_TO_DOCTOR",
                 "parameters": { "reason": "…" },
                 "evidenceNoteIds": ["…"], "reason": "…" } ] }
```

The system prompt said only "Return ONLY valid JSON" — it never named the required top-level key `changes` nor the field `params`. `StrictJsonParser` (fail-on-unknown-properties, required top-level `changes`) rejected every response: models chose `actions`, and used `parameters` for the parameter map.

## Fix

- Commit `42b589af`: the prompt now states the exact JSON shape and mandates the top-level key `changes`.
- Commit `d70795fc`: live re-verification showed `gpt-4o-mini` still emitting `parameters`; `RawChange.params` accepts the synonym via `@JsonAlias("parameters")` and the prompt explicitly names `params`. All other strictness (unknown fields, evidence validation, medication hard lock) unchanged.

## Verification

Live end-to-end against OpenRouter (`openai/gpt-4o-mini`, synthetic seeded data): HTTP 200, `aiUnavailable: false`, plan **version 1 SUGGESTED** with one grounded, evidence-cited `ESCALATE_TO_DOCTOR` change. Backend suite 700/700 including `PlanSuggestionServiceTest` (16).

**Status: fixed.**
