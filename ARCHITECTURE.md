# Project Architecture

## Purpose

This document defines the repository architecture, application boundaries, ownership boundaries, and implementation rules for the Caregiver Daily Notes project.

The architecture is intentionally feature-oriented. Backend domains are grouped by business capability, while web and mobile clients mirror product feature boundaries without sharing frontend source code.

## Repository layout

```
caregiver-daily-notes/
├── backend/        # Spring Boot 3 / Java 21 / Maven REST API
├── web/            # React + TypeScript web application
├── mobile/         # React Native CLI Android application
├── docs/           # Architecture, API, AI, security, testing, privacy docs
├── .github/        # Repository-owned automation and CI configuration
├── ARCHITECTURE.md
├── README.md
└── TEST_CASES.md
```

## Backend

The backend is organized by feature/domain rather than by global controller/service/repository folders.

```
backend/src/main/java/com/caregiver/
├── CaregiverApplication.java
├── auth/
├── recipient/
├── notes/
├── plans/
├── ai/
├── audit/
├── common/
└── config/
```

The package namespace is intentionally generic and does not contain a person's name. The project uses `com.caregiver` as its application package namespace.

Each feature owns its controllers, services, repositories, DTOs, domain models, and feature-specific validation unless a concern is explicitly shared.

### Backend domains

- `auth/`: caregiver authentication and JWT/Spring Security.
- `recipient/`: care-recipient ownership and CRUD.
- `notes/`: daily notes, append-only addendums, history, filtering.
- `plans/`: plan versions, statuses, acceptance/editing/dismissal/archive transitions.
- `ai/`: LLM client abstraction, Hugging Face integration, summaries, grounded plan suggestions, validators, red flags, fallback handling, and AI evaluation support.
- `audit/`: persistent audit events for writes and important state changes.
- `common/`: genuinely cross-cutting exceptions, API utilities, security helpers, and validation utilities.
- `config/`: application configuration such as OpenAPI and infrastructure client configuration.

Database migrations live under `backend/src/main/resources/db/migration/`. AI prompt templates live under `backend/src/main/resources/prompts/`. Tests mirror backend feature boundaries under `backend/src/test/java/` and `backend/src/test/resources/`.

## Web

The web application uses React + TypeScript and is organized by product feature.

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

The Android client uses React Native CLI and is built as an APK through GitHub Actions.

```
mobile/src/
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

The mobile application is separate from the web application. It may mirror feature concepts and API contracts but must not become a shared frontend monorepo.

## Infrastructure boundaries

- Backend hosting: Render.
- Web hosting: Cloudflare.
- Database: Supabase PostgreSQL.
- Cache: Upstash Redis.
- LLM provider: Hugging Face HTTP API.
- CI/CD: GitHub Actions.
- Android distribution: GitHub Releases.

Infrastructure configuration and repository administration are owner-controlled. Feature contributors must not change repository settings, permissions, secrets, deployment configuration, or external infrastructure unless explicitly authorized.

## Ownership boundaries

| Area | Owner |
|---|---|
| `.../auth/` | Member 3 |
| `.../recipient/` | Member 3 |
| `.../plans/` | Member 3 |
| `.../audit/` | Member 3 |
| `.../notes/` | Member 2 |
| `.../ai/` | Member A / Owner |
| `mobile/` | Member A / Owner |
| `web/` | Member 4 |
| Frontend E2E/accessibility tests | Member 4 |
| AI evaluation harness | Member A / Owner |
| Cross-module integration | Member A / Owner |
| Repository/infrastructure administration | Owner |

Ownership is primary responsibility, not permission to bypass review. Cross-feature changes should be coordinated through an issue and PR.

## Issue-to-architecture rule

Every implementation issue must identify the feature/domain, primary owner, allowed repository areas, dependencies, required behavior, required tests, explicitly prohibited unrelated areas, and Definition of Done.

Issues should describe required behavior and architectural boundaries without unnecessarily dictating every class name. Contributors may introduce small supporting classes inside their owned area when justified.

## Dependency rule

Initial foundation/setup work establishes application scaffolding first. Feature issues then build on that foundation. Dependencies should be expressed in GitHub issues rather than hidden in implementation assumptions.

## Cross-cutting rules

- Backend: Maven, Java 21, Spring Boot 3.
- Clients: TypeScript where applicable.
- No Gradle for the backend.
- No Expo-based mobile workflow.
- No local database or Redis requirement for normal project work.
- API contracts are owned by the backend and consumed by both clients.
- Secrets must never be committed.
- Notes are treated as data, not instructions, by AI components.
- AI must never bypass deterministic safety controls.
- No contributor merges their own PR.
- Unrelated refactors should not be included in feature PRs.

## Source of truth

This file defines the detailed intended architecture. Individual GitHub issues define implementation scope. The README explains the project at a high level.
