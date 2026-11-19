# Defect register — Rafeek

Defects found by the project's own testing, documented with evidence and repro. One issue per defect in the GitHub tracker; reports here carry the full detail. Status values: `fixed` (verified), `in progress`, `open`.

| ID | Severity | Component | Summary | Status |
|---|---|---|---|---|
| [DEF-001](def-001-v17-h2-migration.md) | Blocker (test layer) | backend / migrations | V17 incompatible with H2 — 325 cascading test errors | fixed |
| [DEF-002](def-002-web-unit-drift.md) | Major | web / unit tests | 109 stale unit tests after UI contract changes | in progress |
| [DEF-003](def-003-backend-contract-drift.md) | Major | backend / tests + docs | 24 assertions stale after the unified error contract | fixed |
| [DEF-004](def-004-corrections-npe.md) | Major | backend / notes | Typed corrections 500 on notes with never-recorded fields | fixed |
| [DEF-005](def-005-nondeterministic-llm-body.md) | Minor | backend / AI | Flaky byte-for-byte body contract (Map.of ordering) | fixed |
| [DEF-006](def-006-plan-output-schema.md) | Major | backend / AI | Plan generation always failed: prompt never stated the schema | fixed |
| [DEF-007](def-007-android-key-classification.md) | Major | android / AI | Corrupted/missing keys reported as "provider rejected the key" | fixed (device verification pending) |

## Report template

ID and title · severity · component · environment · discovery date/method · repro steps · expected vs actual · evidence (logs, traces, scripts) · root cause · fix (commit) · verification · status.

Reports never contain credentials or real care-recipient data; all evidence uses synthetic inputs.
