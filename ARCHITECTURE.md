# Project Architecture

## Purpose

This document defines the repository architecture, application boundaries, ownership boundaries, and implementation rules for the Caregiver Daily Notes project.

The architecture is intentionally feature-oriented. Backend domains are grouped by business capability, while web and mobile clients mirror product feature boundaries without sharing frontend source code.

## Repository layout

```
caregiver-daily-notes/
├── backend/        # Spring Boot 3 / Java 21 / Maven REST API
├── web/            # React + TypeScript web application (on `main`)
├── android/        # Native Kotlin (Compose) Android application
├── docs/           # api, ai, android, demo, design-reference, presentations, releases, superpowers
├── .github/        # Repository-owned automation and CI configuration
├── ARCHITECTURE.md
├── CONTRIBUTING.md
├── README.md
└── LICENSE
```

## Backend

The backend is organized by feature/domain rather than by global controller/service/repository folders.

```
backend/src/main/java/com/caregiver/
├── auth/
├── recipient/
├── notes/
├── plans/
├── ai/
├── audit/
├── common/
├── config/         # entrypoint (CaregiverApplication) and configuration
└── preview/        # in-progress preview API on this branch
```
The package namespace is intentionally generic and does not contain a person's name. The project uses `com.caregiver` as its application package namespace.

Each feature owns its controllers, services, repositories, DTOs, domain models, and feature-specific validation unless a concern is explicitly shared.

### Backend domains

- `auth/`: caregiver authentication and JWT/Spring Security.
- `recipient/`: care-recipient ownership and CRUD.
- `notes/`: daily notes, append-only addendums, history, filtering.
- `plans/`: plan versions, statuses, acceptance/editing/dismissal/archive transitions.
- `ai/`: LLM client abstraction, OpenAI-compatible provider integration with failover, summaries, grounded plan suggestions, validators, red flags, fallback handling, and AI evaluation support.
- `audit/`: persistent audit events for writes and important state changes.
- `common/`: genuinely cross-cutting exceptions, API utilities, security helpers, and validation utilities.
- `config/`: the application entrypoint and configuration such as OpenAPI and infrastructure client configuration.
- `preview/`: the in-progress preview API on this branch; superseded by the feature packages above once they are merged in.

Database migrations live under `backend/src/main/resources/db/migration/`. AI prompt templates live under `backend/src/main/resources/prompts/`. Tests mirror backend feature boundaries under `backend/src/test/java/` and `backend/src/test/resources/`.

## Web

The web application uses React + TypeScript and is organized by product feature.

> The web client currently lives on `main`. It has not been merged into
> `feat/standalone-byok-ai` yet; see the branch note in `README.md`.

```
web/src/
├── app/
├── features/
│   ├── auth/
│   ├── recipients/
│   ├── notes/
│   ├── history/
│   ├── ai/
│   └── plans/
├── components/
├── lib/
├── i18n/
├── styles/
└── types/
```

Feature folders own feature-specific pages, components, hooks, API functions, and types. Truly reusable UI belongs in `components/`. Shared API/auth/error utilities belong in `lib/`.

The web client is a separate application and is deployed independently from the backend.

## Mobile

The Android client is native Kotlin (Jetpack Compose + Material3, Gradle) and
is built as a universal debug APK through the `Android` GitHub workflow
(`android-debug-apk` artifact).

```
android/app/src/main/java/com/caregiver/mobile/
├── core/theme/        # palette, type, theme
├── core/navigation/   # tabs + typed routes
├── core/i18n/         # bidi isolation
├── core/network/      # auth interceptor
├── core/time/         # locale-aware dates
├── ai/                # provider-agnostic LLM clients, grounding, sanitizer, prompts
├── data/              # Room (source of truth), settings/token store, backup, CSV, export
└── presentation/      # auth, home, people, notes, history, summary, plans, trends, settings
```

The mobile application is separate from the web application. It may mirror feature concepts and API contracts but must not become a shared frontend monorepo.

### Standalone-first runtime

The app is built to run **without a backend**:

- **Room is the source of truth** for recipients, notes, addendums, plans and medication. The
  backend is optional; when a base URL is configured the app syncs through an outbox
  (`OutboxSyncWorker`) and keeps working offline.
- **AI is bring-your-own-key.** The app holds the provider key itself, encrypted in the Android
  Keystore through the `ApiKeyCipher` seam, in a separate slot per provider. Requests go from the
  device straight to the provider — the backend is not in the path.
- **The deterministic safety layer runs on-device**, so the app can raise a safety flag with no
  network and no key configured.
- **`DEMO_BACKEND` and `LOCAL_MODE` are different switches.** The demo banner is driven by
  `DEMO_BACKEND` only, so production content is never mislabelled as demo data.


## Infrastructure boundaries

- Backend runtime: local JVM for development and demos, shared over HTTPS via a
  Cloudflare Tunnel URL created per session. Hosted backend deployment is
  deferred (no card-backed PaaS is in use); clients must therefore read the
  backend base URL from a single runtime-editable setting, never a build-time constant.
- Web hosting: Cloudflare.
- Database: Supabase PostgreSQL.
- LLM provider, app: bring-your-own-key. The device calls Gemini, OpenAI, Anthropic,
  xAI or OpenRouter directly with the user's own key, which never reaches our servers.
- LLM provider, backend: Gemini primary, OpenRouter backup, template fallback.
- CI/CD: GitHub Actions.
- Android distribution: GitHub Releases.

Infrastructure configuration and repository administration are owner-controlled. Feature contributors must not change repository settings, permissions, secrets, deployment configuration, or external infrastructure unless explicitly authorized.

## Ownership boundaries

Each area is owned by one person and tracked by that person's own issues.

| Area | Owner | Issues |
|---|---|---|
| `backend/.../ai/`, AI evaluation harness | [Alaa (`alaa157`)](https://github.com/alaa157) | [#22](https://github.com/alaa157/caregiver-daily-notes/issues/22) – [#29](https://github.com/alaa157/caregiver-daily-notes/issues/29) |
| `android/` (the whole app), cross-module integration, release readiness | [Alaa (`alaa157`)](https://github.com/alaa157) | [#27](https://github.com/alaa157/caregiver-daily-notes/issues/27), [#28](https://github.com/alaa157/caregiver-daily-notes/issues/28) |
| `backend/.../notes/` | [Ebrahim Ahmed (`ebrahimahmedss47-commits`)](https://github.com/ebrahimahmedss47-commits) | [#30](https://github.com/alaa157/caregiver-daily-notes/issues/30) – [#34](https://github.com/alaa157/caregiver-daily-notes/issues/34) |
| `backend/.../auth/`, `recipient/`, `plans/`, `audit/` | [Mohamed Hemdan (`mohamed-hemdan1`)](https://github.com/mohamed-hemdan1) | [#8](https://github.com/alaa157/caregiver-daily-notes/issues/8) – [#12](https://github.com/alaa157/caregiver-daily-notes/issues/12) |
| `web/`, frontend E2E and accessibility tests | [Mahmoud Hewidy (`hewidyy`)](https://github.com/hewidyy) | [#13](https://github.com/alaa157/caregiver-daily-notes/issues/13) – [#21](https://github.com/alaa157/caregiver-daily-notes/issues/21) |
| Repository and infrastructure administration | [Alaa (`alaa157`)](https://github.com/alaa157) | — |

Ownership is primary responsibility, not permission to bypass review. Cross-feature changes should be coordinated through an issue and PR.

## Issue-to-architecture rule

Every implementation issue must identify the feature/domain, primary owner, allowed repository areas, dependencies, required behavior, required tests, explicitly prohibited unrelated areas, and Definition of Done.

Issues should describe required behavior and architectural boundaries without unnecessarily dictating every class name. Contributors may introduce small supporting classes inside their owned area when justified.

## Dependency rule

Initial foundation/setup work establishes application scaffolding first. Feature issues then build on that foundation. Dependencies should be expressed in GitHub issues rather than hidden in implementation assumptions.

## Cross-cutting rules

- Backend: Maven, Java 21, Spring Boot 3. No Gradle for the backend.
- Android: Gradle (Kotlin/Compose) under `android/` only; Maven remains backend-only.
- Clients: TypeScript where applicable (web).
- No Expo-based mobile workflow.
- The web client and the backend need no local database or cache to run.
- The Android app is local-first: Room is the source of truth on the device, and no backend
  service is required for any of its workflows.
- API contracts are owned by the backend and consumed by both clients.
- Secrets must never be committed. A user's own provider key never leaves their device and is
  never sent to our backend.
- Notes are treated as data, not instructions, by AI components.
- AI must never bypass deterministic safety controls.
- No contributor merges their own PR.
- Unrelated refactors should not be included in feature PRs.

## Source of truth

This file defines the detailed intended architecture. Individual GitHub issues define implementation scope. The README explains the project at a high level.
