# Caregiver Daily Notes

A cloud-hosted care-support system consisting of a separate web application and Android mobile application backed by a Spring Boot REST API.

The system allows a caregiver to manage their own care recipients, record structured daily observations and free-text notes, generate AI summaries, review AI-suggested care plans, and maintain a complete history of notes and plans.

> **Disclaimer:** Care-support suggestions generated from your notes. Not medical advice.

## Project Purpose

This project is a DEPI Software Testing capstone focused on building and testing a realistic care-support application.

The system is **not** a diagnostic system and is **not** a medical monitoring system.

The application uses **synthetic data only**.

There is a single application role:

- Caregiver

Each caregiver can manage only their own care recipients, with one caregiver assigned to each recipient.

## Applications

The project contains two separate client applications:

- **Web application** — React + TypeScript
- **Android mobile application** — Native Kotlin (Jetpack Compose)

Both clients consume the same backend REST API. The mobile application is a real separate application and is distributed as an APK through GitHub Releases.

The web and mobile clients are not separate backends and do not maintain separate databases.

## Architecture

```
                    ┌──────────────────────┐
                    │      Web Client      │
                    │ React + TypeScript   │
                    └──────────┬───────────┘
                               │
                               │ HTTPS
                               │
                     ┌──────────▼───────────┐
                     │    Mobile Client     │
                     │    Native Kotlin     │
                    └──────────┬───────────┘
                               │
                               │ HTTPS
                               ▼
                    ┌──────────────────────┐
                    │    Spring Boot API   │
                    │       Java 21        │
                    │   REST + JWT Auth    │
                    └───────┬───────┬──────┘
                            │       │
               ┌────────────┘       └──────────────┐
               ▼                                   ▼
      ┌──────────────────┐                ┌──────────────────┐
      │ Supabase         │                │ Upstash Redis    │
      │ PostgreSQL       │                │ Cache            │
      └──────────────────┘                └──────────────────┘
                            │
                            ▼
                  ┌──────────────────┐
                  │ Hugging Face LLM │
                  │ HTTP API         │
                  └──────────────────┘
```

The backend is the central application layer. Business rules, authorization, deterministic safety rules, AI validation, plan versioning, and audit logging are enforced by the backend rather than trusted to the clients.

## Cloud Infrastructure

All application infrastructure is hosted online. Team members do not need to install or run the application locally.

| Requirement | Service |
|---|---|
| Source code | GitHub |
| PostgreSQL database | Supabase |
| Redis cache | Upstash Redis |
| Spring Boot API | Local JVM (+ Cloudflare Tunnel for shared/demo access) |
| Web application | Cloudflare |
| AI/LLM API | Hugging Face |
| Android APK distribution | GitHub Releases |
| CI/CD | GitHub Actions |

### Supabase

Supabase PostgreSQL is the system's persistent source of truth.

Application data stored in PostgreSQL includes:

- Caregivers
- Care recipients
- Daily notes
- Addendum notes
- AI summaries
- Plans
- Plan versions
- Audit records
- Related metadata

Database changes must be implemented through versioned migrations.

### Upstash Redis

Upstash Redis is used for temporary/cache data rather than permanent application data.

Primary uses include:

- LLM result caching
- Regeneration limiting
- Short-lived cached data
- Other explicitly temporary application state when required

Redis must never be treated as the source of truth for application records.

The application should continue to behave safely when cached data is unavailable.

### Backend runtime (local + tunnel)

The Spring Boot backend runs locally on the owner's machine for development and
demos. Shared access (phone, other machines, evaluators) goes through a
Cloudflare quick-tunnel `https://` URL pointed at the local backend, created
fresh per session. Hosted deployment is deferred until a card-backed host is
available.

The backend provides:

- REST API
- Authentication
- Authorization
- Business logic
- Notes
- Recipients
- Summaries
- Plans
- Audit logging
- AI orchestration
- Validation

### Cloudflare

Cloudflare hosts the web client and provides the public web entry point.

### Hugging Face

The backend communicates with the selected LLM through an HTTP API.

The AI provider is hidden behind the application's `LlmClient` interface so that the provider/model can be changed without coupling the rest of the application to a specific LLM.

### GitHub Releases

The Android application is built as an APK and published through GitHub Releases.

The repository should provide a clear release artifact for evaluators to download and install.

## Technology Stack

### Backend

- Java 21
- Spring Boot 3
- Maven
- Spring Security
- JWT authentication
- REST API
- OpenAPI
- PostgreSQL
- JUnit 5
- Testcontainers

### Web

- React
- TypeScript
- Internationalization (Arabic/English)
- RTL support
- Playwright
- Accessibility testing

### Mobile

- Native Kotlin (Jetpack Compose + Material3)
- Gradle Android build (`android/`)
- Android APK
- Arabic/English
- RTL support

### Infrastructure

- Supabase PostgreSQL
- Upstash Redis
- Local JVM runtime + Cloudflare Tunnel (dev/demo backend access)
- Cloudflare
- Hugging Face
- GitHub Actions

## Development Model

This project is developed through the team's connected ChatGPT/GitHub workflow.

Team members do not need to install the application or development environment on their personal machines for normal project work.

The expected workflow is:

```
GitHub Issue
     ↓
Assigned team member
     ↓
ChatGPT / connected GitHub
     ↓
Repository analysis and implementation
     ↓
Tests
     ↓
Commit / Pull Request
     ↓
Code review
     ↓
Merge
     ↓
GitHub Actions
     ↓
Cloud deployment / release
```

Each team member should work only within the scope of their assigned GitHub issue unless the team explicitly changes the scope.

If a separate problem is discovered, create a separate issue instead of silently expanding the current task.

## Required Backend Environment

The backend targets:

- **Java 21**
- **Spring Boot 3**
- **Maven**

Maven is the backend's build and dependency-management system.

Gradle is used for the Android app only (`android/`); the backend never uses Gradle.

## Environment Variables

Secrets and environment-specific configuration must never be committed to GitHub.

The exact variable names will be defined by the implementation, but the deployment configuration is expected to include values for areas such as:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

REDIS_URL

JWT_SECRET
JWT_ALGORITHM

LLM_API_KEY
LLM_MODEL

CORS_ALLOWED_ORIGINS
```

Actual credentials must remain in the appropriate hosting provider's secret/environment-variable configuration.

## Core Functional Scope

### Authentication and Authorization

- Caregiver login
- JWT-based authentication
- Each caregiver can access only their own care recipients
- Server-side authorization is mandatory
- IDOR protection is required

### Daily Notes

Structured fields:

- Mood
- Appetite
- Sleep
- Mobility
- Medication taken
- Pain
- Falls

Plus free-text notes in:

- Arabic
- English
- Mixed Arabic/English

Notes are append-only.

Corrections are represented by addendum notes rather than modifying the original note.

### Notes History

Notes can be filtered by:

- Date
- Care recipient

The complete history remains available.

### AI Summaries

The caregiver can request a summary covering:

- Last 7 days
- Last 14 days
- Last 30 days

Summaries are generated on demand.

### AI Suggested Plans

A suggested plan is generated from:

- The generated summary
- Deterministic flags
- The last accepted plan

The caregiver can:

- Accept
- Edit then accept
- Dismiss

Accepting a new plan archives the previous active plan.

Editing creates a new plan version while preserving the original AI suggestion.

### Plan History

Plans use versioning such as:

```text
v1 → v2 → v3 → ...
```

Each version is linked to:

- The summary it was based on
- The notes it was based on
- The reason for the change
- Its status

There is no diff view in the current scope.

## AI Safety and Validation Rules

These rules are mandatory.

### Deterministic Red Flags

Red flags are calculated by ordinary backend code, not by the LLM.

Examples include:

- Falls
- High pain
- Repeated missed medication
- Several days of poor appetite

A fixed banner advising the caregiver to consider contacting a doctor is displayed when applicable.

The LLM may increase urgency but must never lower or remove a deterministic red flag.

### Backend Trends

The backend calculates trends and changes since the previous period.

The LLM does not calculate these trends.

### Fixed Action Catalog

Plan items come from a predefined action catalog.

The LLM selects and parameterizes allowed catalog items and provides evidence for them.

The LLM must not invent arbitrary medical advice.

### Medication Hard Lock

The AI cannot add, remove, or modify medications or medication doses.

The backend compares the medication section before and after AI generation.

Any violation causes the generated result to be rejected.

### Grounding

Observations and plan changes must cite:

- Note IDs
- Verbatim supporting quotes

A validator verifies that the quoted text actually exists in the supplied input.

Arabic normalization is applied before comparison, including normalization such as:

- Removing diacritics
- Unifying relevant alef forms
- Unifying relevant ya forms

### Uncertainty

Unclear or conflicting information must remain explicitly uncertain.

For example, a note saying that the caregiver is unsure whether medication was taken must not be transformed into a definitive "taken" statement.

The generated output contains an `unclear_or_conflicting` section.

### Prompt Injection Defense

Notes are data, not instructions.

User-authored notes must never be inserted into the system prompt as trusted instructions.

Notes must be clearly delimited as untrusted input.

### Failure Handling

Saving a note must never depend on successful AI generation.

AI generation uses:

- 30-second timeout
- Up to 2 retries with backoff
- One JSON repair retry
- Rule-based template fallback summary

If AI generation ultimately fails, the application shows an appropriate "AI unavailable" message.

Think-tags and code fences are stripped before JSON parsing.

### LLM Abstraction

All LLM access goes through an `LlmClient` interface.

Tests use a fake implementation.

The provider can therefore be replaced without rewriting the application logic.

### Privacy-Preserving AI Input

The LLM receives a pseudonymous identifier rather than unnecessary identifying information.

Names and dates of birth must not be sent to the LLM.

### Caching

LLM results are cached using an input hash.

Regeneration is rate-limited to avoid unnecessary repeated model calls.

## Testing Strategy

Testing is a core part of the project rather than a final-stage activity.

Required testing areas include:

### Unit Tests

- Business rules
- Deterministic red flags
- Trend calculations
- Plan state transitions
- Validators
- Authorization logic

### Decision-Table Testing

Red-flag rules require decision-table tests covering combinations of relevant conditions and boundary cases.

### Plan State-Transition Testing

Plan statuses:

```text
Suggested
   ├──→ Accepted
   ├──→ Edited-and-Accepted
   └──→ Dismissed

Accepted / Edited-and-Accepted / Dismissed
   └──→ Archived
```

Illegal transitions must also be tested.

### Integration Testing

Integration tests use Testcontainers and a fake `LlmClient`.

The fake client must simulate:

- Timeout
- HTTP 500
- Truncated JSON
- Invalid enum
- Invented quotes
- Medication modification attempts
- Other validation failures required by the test suite

### Golden Dataset

Maintain a synthetic dataset of approximately 20–30 notes covering:

- English
- Modern Standard Arabic
- Egyptian Arabic
- Mixed Arabic/English
- Short notes
- Long notes
- Messy text
- Contradictory information
- Irrelevant content
- Prompt-injection attempts

Evaluation reports should include at least:

- Red-flag recall
- Schema-valid rate
- Groundedness

### Metamorphic Testing

Test behavior under controlled transformations such as:

- Adding an irrelevant sentence
- Paraphrasing
- Negation flips

### Security Testing

Authorization tests must verify that a caregiver cannot access another caregiver's recipients or related data.

### Web Testing

Playwright tests cover:

- Core workflows
- Arabic
- English
- RTL
- Mobile viewport
- Desktop viewport

Accessibility testing includes an axe scan.

### Mobile Testing

The native Android app is tested with JUnit local unit tests plus Compose UI
instrumentation tests (`android/README.md`). Unit tests run with
`./gradlew :app:testDebugUnitTest`; UI tests need a booted emulator/device via
`./gradlew :app:connectedDebugAndroidTest`. Tests must verify core user
workflows, board-15 loading/error/empty/retry/AI-unavailable states, string
parity, RTL/bidi rendering, and safety-banner retention.

### CI

GitHub Actions runs the required test suite on every push and pull request.

CI must publish a coverage report.

## Security and Privacy

The project must follow secure development practices even though the capstone uses synthetic data.

Required controls include:

- HTTPS for deployed communication
- JWT authentication
- Server-side authorization
- IDOR protection
- Secret management through environment variables
- No credentials committed to Git
- Audit logging of writes
- Synthetic data only
- Least-privilege access to infrastructure
- Input validation
- AI output validation

The project documentation will consider requirements and principles relevant to the **Egypt Personal Data Protection Law No. 151 of 2020**.

This project must not be used with real patient or care-recipient data.

## Internationalization

Both client applications support:

- Arabic
- English

Arabic requires proper RTL layout and bidirectional text handling.

The same functional workflows must remain available in both supported languages.

## GitHub Workflow

GitHub Issues are the source of task ownership.

Each issue should specify:

- Epic
- Assignee
- Week
- Labels
- Estimate
- Dependencies
- Description
- Tasks
- Acceptance criteria
- Required tests

### Issue Rules

1. Work only on the assigned issue.
2. Read the existing implementation before changing it.
3. Do not rewrite unrelated working code.
4. Do not introduce new product features without an approved issue.
5. Add or update tests required by the issue.
6. Keep changes reviewable.
7. If another problem is discovered, create a separate issue.
8. Do not commit secrets.
9. The issue is not complete until its acceptance criteria and required tests are satisfied.

## CI/CD

GitHub Actions is responsible for automated validation.

The intended pipeline includes:

```text
Push / Pull Request
        ↓
GitHub Actions
        ↓
Backend tests
        ↓
Integration tests
        ↓
Web tests/build
        ↓
Mobile tests/build checks
        ↓
Coverage report
```

Release automation will build and publish the Android APK through GitHub Releases.

Deployment targets:

- Web → Cloudflare
- Backend → local JVM; shared over HTTPS via Cloudflare Tunnel
- Database → Supabase
- Redis → Upstash

## Deployment

The final deployed system is intended to be accessible over the internet.

### Web

The web application is deployed through Cloudflare.

### Backend

The Spring Boot API runs locally (`mvn -f backend/pom.xml spring-boot:run`, once the
application entrypoint exists) and is shared over HTTPS through a Cloudflare
Tunnel URL created at session/demo time. Both clients read the backend base URL
from a single runtime-editable setting so a fresh tunnel URL never requires a
rebuild.

### Database

PostgreSQL is hosted by Supabase.

### Cache

Redis is hosted by Upstash.

### AI

LLM requests are made from the backend to the configured Hugging Face provider.

### Mobile

The native Kotlin Android app (`android/`, `com.caregiver.mobile`) is packaged
as a universal debug APK by the `Android` GitHub workflow
(`android-debug-apk` artifact) and published through GitHub Releases.

The mobile application communicates with the backend over HTTPS — directly on the
host machine, or via the tunnel URL from other devices.

## Project Scope

### In Scope

- Single caregiver role
- Login
- Care recipient management
- Daily structured notes
- Free-text Arabic/English/mixed notes
- Append-only notes
- Addendum corrections
- Notes history
- Date and recipient filtering
- 7/14/30-day AI summaries
- Deterministic red flags
- AI suggested plans
- Plan acceptance
- Edit-then-accept
- Plan dismissal
- Plan versioning
- Plan history
- Audit log
- Arabic/English UI
- RTL support
- Separate web application
- Separate Android mobile application
- Automated testing
- Cloud deployment
- Synthetic data

### Out of Scope

- Doctor role/dashboard
- Multi-role approval chain
- Plan diff view
- Notifications
- Native iOS application
- Voice notes
- Offline mode
- LLM fine-tuning
- Confidence scores
- Arabizi support
- Diagnosis
- Medical monitoring
- Real patient data

## Project Documentation

Project documentation should be maintained alongside the implementation and should describe:

- Architecture
- API contracts
- Database schema
- AI schemas
- Action catalog
- Testing strategy
- Evaluation methodology
- Deployment
- Security and privacy considerations

The README is an orientation document; detailed technical specifications should live in the appropriate project documentation.

## Status

The repository is being developed as a DEPI Software Testing capstone project.

The implementation, automated tests, cloud deployment, evaluation dataset, and final demonstration will be completed according to the project's GitHub milestones and issues.
