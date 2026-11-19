# Changelog

All notable changes to Rafeek (رفيق) are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project uses
date-based milestones alongside the Android demo release tags.

## [Unreleased]

- CI re-enablement pending (workflow currently disabled manually).
- DEF-008 (#1102): investigate setup-check key-state anomaly on cloud devices (manual device run first).

## 2026-11-19 — Rafeek rebrand, test estate audit, defect fixes

### Changed
- **Renamed to Rafeek (رفيق)** with "The Gather" brand concept: launcher and
  in-app marks, bilingual brand strings (EN/AR), landing/auth screens,
  diagrams, and all README screenshots retaken on real flows.
- README rebuilt: tech badges, embedded architecture diagram, team and
  acknowledgments (DEPI Software Testing track, supervisor, training partner),
  honest testing and CI status.
- Android demo APK releases `android-standalone-v0.3.7-run22` / `-run23`.

### Fixed
- DEF-001 (#1095): V17 migration made portable across PostgreSQL and H2 —
  unblocked 325 cascading test errors. Existing databases need a one-time
  `flyway repair` (see the defect report).
- DEF-003 (#1097): backend suites and API docs aligned with the unified error
  contract (validation 422 / malformed 400) and evidence-screening behavior.
- DEF-004 (#1098): typed corrections no longer 500 on notes with
  never-recorded fields (null observation-state and verification mapping).
- DEF-005 (#1099): deterministic non-OpenAI request bodies (Map.of ordering).
- DEF-006 (#1100): plan-generation prompt now states the exact output schema;
  verified live against OpenRouter.
- DEF-007 (#1101): Android distinguishes missing / corrupted / provider-rejected
  API keys with dedicated EN/AR recovery guidance.

### Added
- `docs/testing/`: test strategy, measured inventory and coverage, execution
  status, and a defect register with evidence, repro, fixes, and verification
  (issues #1095–#1101).
- Backend suite fully green: **700/700**, JaCoCo **93.3% lines / 76.7% branches**.
- Web unit suite fully green: **1,086/1,086** across 49 files (DEF-002, #1096).
- Repository hygiene: issue/PR templates, changelog, description and topics,
  Rafeek release titles.

## 2026-11-18 — Standalone Android demo

### Added
- Android app imported from `feat/standalone-byok-ai`: fully offline-capable
  demo with bring-your-own-key AI (OpenRouter, OpenAI, Gemini, Anthropic, xAI,
  custom), on-device key storage, EN/AR RTL.
- Rolling `apk-latest` release for the web download button.

## 2026-11-17 — Hardening milestone (v0.3.7 demo line)

### Changed
- Unified API error status contract (#843): bean/domain validation → 422,
  malformed/unreadable requests → 400.
- Rate limiting on auth and BYOK AI endpoints (#841, #848).

### Fixed
- Arabic brand tagline letter-spacing reset for accessibility (#855).
- Web APK download points at the rolling latest release (#834).

### Added
- Standalone demo release line begins (`android-standalone-v0.3.7-run3` …
  `-run20`), built and verified on GitHub Actions.

## Earlier history

See the git log and the closed issue tracker (#1–#1093) for the full
development history of the web app, backend, and testing infrastructure.
