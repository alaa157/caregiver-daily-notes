# Rafeek — رفيق

![Rafeek preview](web/public/caregiver-notes-preview.svg)

A care-support system for recording structured daily observations about the people you care for, reviewing their history, and generating AI-assisted summaries and care plans from those notes.

Two clients, one domain model: a **React web application** and a **native Kotlin/Compose Android app**. Both are built around the same append-only note record and the same safety rules.

> **Project notice.** Rafeek is a capstone project using synthetic data only. It is not a diagnostic or medical-monitoring system, is not medical advice, and must not be used with real patient or care-recipient data.

---

## Contents

- [What it does](#what-it-does)
- [Screenshots](#screenshots)
- [Design principles](#design-principles)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Getting started](#getting-started)
- [Running the applications](#running-the-applications)
- [Testing](#testing)
- [API overview](#api-overview)
- [AI features and safety rules](#ai-features-and-safety-rules)
- [Data handling and privacy](#data-handling-and-privacy)
- [Repository layout](#repository-layout)
- [Documentation](#documentation)
- [Contributors](#contributors)
- [License](#license)
- [Contributing](#contributing)

---

## What it does

A caregiver signs in and manages their own care recipients. For each recipient they record a daily note: mood, appetite, sleep, mobility, medication status, pain score, falls, and free text in Arabic, English, or a mix of both.

Notes are never edited in place. Corrections are appended as **addenda** or as typed, immutable **correction events**, so the original record and its full history always survive. From that history the caregiver can:

- Browse and filter notes by date and recipient.
- Request an AI summary over the last 7, 14, or 30 days.
- Review an AI-suggested care plan, then accept, edit, or dismiss it.
- Track medications and per-dose intake events.
- See deterministic safety signals raised by the backend, independent of any model.

## Screenshots

### Web app

<p align="center">
  <img src="docs/screenshots/web/en-03-home.png" alt="Web workspace — daily overview with note availability and recent activity" width="760" />
</p>
<p align="center">
  <img src="docs/screenshots/web/en-14-summary.png" alt="Web AI summary — seven-day summary with safety signals and source-linked observations" width="760" />
</p>

### Mobile app (Android)

<p align="center">
  <img src="docs/screenshots/android/12-home-medication-dose.png" alt="Android home — care alert, medication dose tracking, and daily notes" width="240" />
  <img src="docs/screenshots/android/15-history.png" alt="Android notes history — browse past daily observations" width="240" />
  <img src="docs/screenshots/android/39-home-ar.png" alt="Android home in Arabic — full RTL layout" width="240" />
</p>

Captured from the running apps with synthetic data: the web client against a local Spring Boot + PostgreSQL backend, and the Android offline demo on a cloud device. The full set — every screen in English and Arabic, desktop and mobile — is in [`docs/screenshots/`](docs/screenshots/).

## Design principles

**The backend is the authority.** Business rules, ownership checks, deterministic safety flags, trend calculation, plan versioning, and audit logging all live server-side. Clients render; they do not decide.

**History is append-only.** A note's original content is immutable. Corrections are new records with their own sequence numbers, and the effective state is replayed from them. Nothing is silently overwritten.

**The model never holds the pen on safety.** Red flags are computed by ordinary backend code. An LLM may raise urgency but can never lower or remove a deterministic flag, and it cannot add, remove, or alter medications.

**Uncertainty is a first-class state.** Observations distinguish `RECORDED`, `UNKNOWN`, `NOT_RECORDED`, and `NOT_APPLICABLE`. A contradictory medication report collapses to "unverified" rather than being presented as a fact in either direction.

**Bilingual by construction.** Arabic and English ship together, with RTL layout and bidi isolation for mixed-direction text. Arabic string parity is enforced by test, not by convention.

## Architecture

```
        ┌────────────────────┐        ┌────────────────────┐
        │    Web client      │        │  Android client    │
        │  React + TS + Vite │        │ Kotlin + Compose   │
        └─────────┬──────────┘        └─────────┬──────────┘
                  │                             │
                  │  HTTPS + JWT                │  HTTPS + JWT
                  │                             │  (or local-only)
                  ▼                             ▼
        ┌──────────────────────────────────────────────────┐
        │            Spring Boot REST API (Java 21)        │
        │   auth · ownership · safety rules · versioning   │
        └───────────┬──────────────────────────┬───────────┘
                    │                          │
                    ▼                          ▼
        ┌────────────────────┐      ┌────────────────────────┐
        │  PostgreSQL        │      │  LLM provider          │
        │  (Supabase)        │      │  (caregiver's own key) │
        └────────────────────┘      └────────────────────────┘
```

The web client talks to the backend for everything. The Android release build is **local-first and standalone**: accounts, notes, and plans live in on-device Room databases, and no backend is required. Its AI features call the caregiver's chosen provider directly with a key held in the Android Keystore.

## Technology stack

| Layer | Stack |
|---|---|
| **Backend** | Java 21, Spring Boot 3.3, Spring Security, JWT (jjwt), Spring Data JPA, Flyway, PostgreSQL, Maven, JUnit 5 |
| **Web** | React 18, TypeScript 5, Vite 5, React Router 6, Vitest, Testing Library, Playwright, axe-core |
| **Android** | Kotlin, Jetpack Compose, Room, DataStore, Retrofit, Gradle (AGP 8.7), minSdk 23 / target 35 |
| **Infrastructure** | Supabase PostgreSQL, Docker (Render runtime), GitHub Actions, GitHub Releases for APKs |

## Getting started

### Prerequisites

- **Java 21+** and **Maven** (backend)
- **Node.js 18+** and **npm** (web)
- **JDK 17**, Android SDK `platforms;android-35` and `build-tools;35.0.0` (Android only — AGP 8.7 does not accept newer JDKs)
- A PostgreSQL database (Supabase free tier is sufficient)

### One-command setup

```bash
./setup.sh            # checks prerequisites, builds backend, installs web deps
./setup.sh --test     # also runs backend and web unit tests
./setup.sh --e2e      # also installs Playwright Chromium
./setup.sh --run      # builds, then starts backend + web dev server
./setup.sh --tunnel   # also exposes the backend via a Cloudflare quick tunnel
```

The script creates `.env` from `.env.example`, generates a random `JWT_SECRET`, and warns you while database values are still placeholders.

### Manual setup

```bash
cp .env.example .env       # then fill in DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
mvn -f backend/pom.xml -DskipTests package
cd web && npm ci
```

### Environment variables

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | Full `jdbc:postgresql://…` URL (pooler recommended on Supabase free tier) |
| `DATABASE_USERNAME` | Database user |
| `DATABASE_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing secret. Generate with `openssl rand -base64 32`; never reuse across environments |

The backend holds **no** AI provider keys. AI credentials are supplied per request by the caregiver (see [AI features](#ai-features-and-safety-rules)).

## Running the applications

**Backend** — listens on `8080` (or `PORT`), applies Flyway migrations on startup, and validates the schema with `ddl-auto: validate`:

```bash
set -a; source .env; set +a
mvn -f backend/pom.xml spring-boot:run
```

API docs are disabled in the base configuration and enabled only under the `dev` profile, so `/v3/api-docs` and Swagger UI do not exist in production.

**Web** — Vite dev server, proxying to the backend:

```bash
cd web && npm run dev
```

The built web app reads its API origin from `public/config.json` at runtime, so a deployment can be repointed without a rebuild:

```bash
cd web && npm run build
API_BASE_URL=https://your-backend.example npm run config:runtime
```

`API_BASE_URL` must be HTTPS (HTTP is permitted only for loopback) and must not contain credentials, a query, or a fragment.

**Android** — from `android/`:

```bash
./gradlew :app:assembleDebug      # offline demo APK, unminified
./gradlew :app:assembleMinified   # offline demo APK, R8-shrunk
./gradlew :app:assembleRelease    # standalone release build
```

`debug` and `minified` serve canned in-memory data with no network and no login: long-press the logo on the login screen to enter the offline demo. `release` is the standalone local-first build with local accounts and no backend dependency. See [`android/README.md`](android/README.md) for the full build, variant, and data-layout reference.

## Testing

| Suite | Command |
|---|---|
| Backend unit and integration | `mvn -f backend/pom.xml test` |
| Web unit | `cd web && npm test` |
| Web type check | `cd web && npm run typecheck` |
| Web browser (Playwright) | `cd web && npx playwright test` |
| Android JVM unit | `cd android && ./gradlew :app:testDebugUnitTest` |
| Android instrumentation | `cd android && ./gradlew :app:connectedDebugAndroidTest` |

CI runs the frontend suite (unit, typecheck, build, Playwright), the backend suite with JaCoCo coverage, and a Docker image build on every pull request to `main`.

## API overview

All endpoints require a caregiver bearer token except registration and login. The caregiver identity is derived from the verified JWT; clients never submit an owner ID. Another caregiver's resources return `404`, never `403`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Recipients | `GET/POST /api/recipients`, `GET/PUT /api/recipients/{id}`, `PATCH …/care-details`, `…/archive`, `…/restore` |
| Notes | `POST /api/notes`, `GET /api/notes/{id}`, `GET /api/notes/by-recipient/{id}?date=`, `GET /api/notes?from=&to=` |
| Addenda | `POST/GET /api/notes/{id}/addenda` |
| Corrections | `POST/GET /api/notes/{id}/corrections` |
| Signals | `GET /api/notes/signals?recipientId=&from=&to=` |
| Activity | `GET /api/activity?limit=` |
| Medications | `GET/POST /api/recipients/{recipientId}/medications`, `…/{medicationId}/intake-events` |
| Summaries | `POST /api/summaries`, `POST /api/summaries/byok` |
| Plans | `POST /api/recipients/{recipientId}/plans/suggestions`, `GET …/active`, `GET …/history`, `POST …/{planId}/accept`, `…/edit-and-accept`, `…/dismiss` |
| Safety | `GET /api/recipients/{recipientId}/safety-signals`, `GET/POST …/safety-acknowledgements` |
| AI connection | `POST /api/ai/byok/connection-test` |

Writes accept an optional `Idempotency-Key` header: the same key with an identical payload replays the original record, while the same key with a different payload returns `409 IDEMPOTENCY_CONFLICT`. Full request and response contracts, including error codes, are in [`docs/api/`](docs/api/).

## AI features and safety rules

AI is **bring-your-own-key**. The backend stores no provider credentials and has no default model. Each summary or plan request carries the caregiver's provider, model, and key; the key is used for that call only, never persisted server-side, and never logged. Supported providers are OpenAI, Gemini, xAI, and OpenRouter (all via OpenAI-compatible endpoints); the Android app additionally supports Anthropic.

Four rules constrain what the model is allowed to do:

- **Deterministic red flags.** Falls, high pain, repeated missed medication, and sustained poor appetite are computed in backend code. The model may escalate urgency but never suppress a flag.
- **Backend-computed trends.** Period-over-period changes are calculated server-side, not inferred by the model.
- **Fixed action catalog.** Plan items are selected and parameterized from a predefined catalog, with evidence cited. The model does not invent free-form medical advice.
- **Medication hard lock.** A plan's medication list is copied from the server-owned baseline, never from model output.

AI output is measured by a deterministic evaluation harness over 22 synthetic golden cases spanning English, Modern Standard Arabic, Egyptian dialect, mixed-language, short, long, messy, contradictory, irrelevant, and prompt-injection inputs. Four metrics — red-flag recall, schema validity, groundedness, and medication safety — must all equal `1.0`, and a regression fails CI. See [`docs/ai/eval-harness.md`](docs/ai/eval-harness.md).

## Data handling and privacy

The repository currently has **no approved privacy policy and no terms document**. [`docs/privacy/README.md`](docs/privacy/README.md) records that gap and inventories, from source, what the software actually stores and who can see it. It is a factual audit, not a policy, and it is the correct starting point for anyone writing one.

Two things worth knowing before you run this:

- The backend persists health-adjacent data about identifiable people: recipient names, dates of birth, allergies, conditions, emergency contacts, and note contents including free text.
- BYOK summaries send per-note transcripts — note ID, date, and all observation values including free text verbatim — to whichever provider the caregiver selects. Recipient name and date of birth are excluded. The UI discloses this before AI is enabled.

There is no retention schedule, purge job, or data-export feature in the codebase. Archiving a recipient is reversible and non-destructive.

## Repository layout

```
backend/          Spring Boot API — auth, notes, recipients, plans, medications, safety, AI
  src/main/resources/db/migration/   Flyway migrations V1–V17
  src/main/resources/prompts/        Model prompt templates
web/              React + TypeScript client
  src/features/   auth, notes, recipients, history, summary, plans, medications, safety, ai, settings
  src/i18n/       English and Arabic translations, RTL and bidi support
  src/tests/      Unit, integration, and live-workflow suites
android/          Native Kotlin/Compose app
  app/src/main/java/com/caregiver/mobile/   core, data, presentation, ai
docs/             API contracts, AI evaluation, design system, privacy audit, screenshots
.github/          CI and Android release workflows
setup.sh          One-command local setup
```

## Documentation

| Document | Contents |
|---|---|
| [`docs/api/daily-notes.md`](docs/api/daily-notes.md) | Notes, addenda, corrections, signals, idempotency, error codes |
| [`docs/api/recipients.md`](docs/api/recipients.md) | Recipient lifecycle, care details, archive and restore |
| [`docs/ai/eval-harness.md`](docs/ai/eval-harness.md) | AI evaluation metrics and golden dataset |
| [`docs/design-system/DESIGN-STYLE.md`](docs/design-system/DESIGN-STYLE.md) | Android visual design guide |
| [`docs/design-system/DECISIONS.md`](docs/design-system/DECISIONS.md) | Design system parity decisions |
| [`docs/privacy/README.md`](docs/privacy/README.md) | Verified data-handling inventory and policy gap |
| [`docs/brand/the-gather/`](docs/brand/the-gather/) | Selected brand concept — Rafeek "The Gather" mark and logo files |
| [`android/README.md`](android/README.md) | Android build variants, local accounts, data layout |

## Contributors

Rafeek is a four-person team. Contributions are summarised from the repository's
commit history and issue tracker:

| Contributor | GitHub | Area of work |
|---|---|---|
| **Alaa Shehata** (team lead) | [`alaa157`](https://github.com/alaa157) | Project lead and full-stack owner: Android app (Kotlin/Compose, local-first release, BYOK AI and model picker, OpenRouter integration), backend features (auth, safety signals, medications, AI summaries), branding (Rafeek "The Gather"), CI workflows, documentation, diagrams and repo setup. |
| **Mohamed Hemdan** | [`mohamed-hemdan1`](https://github.com/mohamed-hemdan1) | Backend — care plans, audit and authorization: plan API and lifecycle (accept / edit / dismiss, versioning, active-plan uniqueness), append-only audit events with owner-scoped reads, caregiver-role authorization for verified JWTs, and plan/audit/ownership integration tests. |
| **Mahmoud Hewidy** | [`hewidyy`](https://github.com/hewidyy) | Web frontend (React + TypeScript + Vite): project foundation, caregiver authentication (Task 14), recipient management (15), daily notes with append-only corrections (16), notes history and filtering (Task 5), summaries and protected care-plan workflows (Tasks 6–7), and later UI refreshes across workflows. |
| **Ebrahim Ahmed** | [`ebrahimahmedss47-commits`](https://github.com/ebrahimahmedss47-commits) | Backend — daily notes API: implemented the daily notes API (issue #47) and completed its acceptance criteria (issue #56). |

## License

Released under the [MIT License](LICENSE).

## Contributing

Work is tracked as GitHub issues, one defect or feature per issue, each independently actionable and self-contained. Pull requests target `main` and must pass the full CI suite. Branch convention for fixes is `fix/<area>-<issue-number>-<slug>`.

If you find a separate problem while working, file a separate issue rather than widening the current change.
