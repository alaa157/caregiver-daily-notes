# WP-01 — Product Decisions and Web Parity Contract

**Status:** Decision capture and semantic contract documented; implementation is not authorized.

**Decision date:** 2026-11-03

**Approved by:** Product owner (no personal name provided)

**Repository baseline:** `main` at `c447e8f`

**Scope:** Web product/domain decisions and implementation constraints only. No application, API, database, migration, Android, or transfer implementation is included.

> **Important:** Owner-approved product scope is distinct from engineering feasibility, clinical approval, security approval, and deployment evidence. Those review gates are called out explicitly below. No unresolved recommendation is represented as an approved requirement.

## 1. Purpose and scope

This artifact records the product-owner responses gathered for WP-01, defines semantic contracts for future Web parity work, and identifies the work that remains gated. It complements, rather than replaces, the historical Phase 1 parity matrix and Phase 2/Phase 3A analyses.

The approved product direction is **capability and semantic parity**, not identical screens or a shared database:

- Android remains Kotlin/Jetpack Compose, Room-backed, offline-first, standalone, and BYOK.
- Web remains React/TypeScript, Spring Boot, PostgreSQL, authenticated caregiver accounts, and BYOK.
- There is no shared runtime database or automatic synchronization.
- Web and Android AI credentials and private configuration remain separate.
- Transfer-specific decisions remain in the separate transfer workstream.
- WP-01 does not authorize implementation.

## 2. Source documents and evidence

### 2.1 Authoritative project documents

| Source | Relevant evidence |
|---|---|
| [Phase 1](../../phase-1/README.md) | Architecture, product specifications, original 33-row parity matrix, data/business-rule gaps, AI behavior, portability. The original matrix remains unchanged. |
| [Phase 2](../../phase-2/README.md) | Canonical semantic model, PO-01–PO-23 register, migration/security principles, and recommendations. Recommendations were not treated as approvals until owner response. |
| [Phase 3A](../../phase-3a/README.md) | Implementation-readiness gaps, verified code facts versus unresolved requirements, and implementation dependencies. |
| [Web parity roadmap](../README.md) | WP-01–WP-11 scope, dependencies, quality gates, and existing product-owner decision register. |

### 2.2 Targeted technical evidence for Web BYOK

Only the existing Web summary/API boundary and backend provider wiring were inspected for feasibility:

- `web/src/api/summary.ts` calls the authenticated application API; its comment says the browser never calls an LLM.
- `backend/src/main/java/com/caregiver/config/LlmClientConfig.java` wires environment-configured Gemini as primary and OpenRouter as backup.
- `backend/src/main/java/com/caregiver/ai/FailoverLlmClient.java` attempts the backup after primary fallback; if unavailable it returns an explicit `AI_UNAVAILABLE` result.
- `backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java` sends OpenAI-compatible chat-completions requests and uses the configured key as a bearer credential.
- `backend/src/main/resources/application.yml` describes a Gemini → OpenRouter → template/unavailable chain. The implementation path and that comment should be reconciled in a later implementation phase.
- The Spring application therefore currently uses server-configured provider credentials and provider failover. This conflicts with approved PO-24. No code was changed here.

Provider feasibility evidence:

- Google's [Gemini OpenAI compatibility documentation](https://ai.google.dev/gemini-api/docs/openai) documents chat-completions requests using a Gemini API key.
- Google's [API key guidance](https://ai.google.dev/gemini-api/docs/api-key) describes key security and management.
- OpenRouter's [API reference](https://openrouter.ai/docs/api/reference/overview) documents its chat-completions API.
- On 2026-11-03, unauthenticated `OPTIONS` preflight requests to the configured Gemini and OpenRouter API paths returned CORS headers allowing `POST` with `Authorization` and `Content-Type` from a test origin. The probes sent no API key or care data. This is point-in-time evidence only; it is not a provider guarantee, authenticated browser test, production-origin test, or security approval.

## 3. Product-owner decision register

All owner responses in Groups A–D were provided on **2026-11-03**. Unless specifically recorded otherwise, the owner did not provide additional personal rationale. Do not invent a person's name, clinical reviewer, security reviewer, or deployment owner.

### 3.1 Core data and business semantics — PO-01–PO-08

| ID | Status and owner decision | Alternatives/recommendation considered | Rationale, impact, and remaining gate |
|---|---|---|---|
| PO-01 | **APPROVED** — Add nullable free-text recipient relationship to both platforms. | Keep Web-only/noncanonical or defer. Phase 2 recommended shared optional free text. | No separate rationale supplied. Android schema/editor/backup changes are future implementation work; relationship must not be used for automatic identity matching. |
| PO-02 | **APPROVED** — Use canonical `ACTIVE`, `ARCHIVED`, `DELETED` lifecycle; deletion is an explicit legal/data operation, not an automatic consequence of archive. | Keep platform states distinct or retain Web deactivation without alignment. Phase 2 recommended a reversible lifecycle and explicit deletion. | No separate rationale supplied. Existing `INACTIVE`/deactivated data must be inventoried; ambiguous mappings require review. Physical purge, retention, and deletion completion remain subject to PO-13 and deployment/legal policy. |
| PO-03 | **APPROVED** — Android may support explicit historical note creation in a later UX release; preserve existing note dates. | Keep Android today-only or permit historical dates only for imports. Phase 2 recommended later Android historical-date support. | No separate rationale supplied. This is not authorization to change Android now. Local-date and time-zone behavior require tests before implementation. |
| PO-04 | **APPROVED** — At most one original note per recipient per caregiver-local calendar date. The caregiver's local date at creation is the date basis; an available IANA zone is provenance and must not rewrite the saved date. | Multiple notes/day or multiple notes with one designated daily note. Phase 2 recommended one original note/day, with duplicate preflight. | No separate rationale supplied. Existing duplicate rows must be inventoried and reported; never silently merge/delete. Do not add a database constraint until duplicate analysis and a safe resolution plan are complete. |
| PO-05 | **APPROVED** — Use explicit observation states `RECORDED`, `UNKNOWN`, `NOT_RECORDED`, `NOT_APPLICABLE`, and `LEGACY_AMBIGUOUS`; represent verification separately. | Nullable values plus an unknown enum, or defer. Phase 2 recommended an explicit state and separate verification. | No separate rationale supplied. Empty, false, zero, and null must not be reinterpreted as a confirmed negative. Legacy ambiguity preserves the raw source. |
| PO-06 | **APPROVED** — Support both text addenda and structured corrections as immutable typed events. | Text only or structured corrections only. Phase 2 recommended both, with an explicit field mask and intentional clear. | No separate rationale supplied. Preserve original notes and old text verbatim. Corrections change only explicitly targeted fields; `SET`, `CLEAR`, `UNKNOWN`, and `NOT_RECORDED` remain distinct. |
| PO-07 | **OWNER DIRECTION APPROVED; CLINICAL REVIEW PENDING** — Use current Android deterministic rules as the selected product baseline, subject to versioning and clinical review. | Keep current Android rules, use current Web rules, or use a joint catalog. Phase 2 recommended a versioned catalog with deterministic fixtures and clinical review. | The owner's choice does **not** establish clinical validation or approve specific thresholds, text patterns, severity, or advice. Version and test the selected baseline; qualified clinical review is required before activation/claims for clinically interpreted rules. |
| PO-08 | **APPROVED** — Use a closed structured action catalog; preserve unmappable legacy free text as opaque history or exclude it only after explicit preview. | Retain free text or use Web's current catalog directly. Phase 2 recommended a closed catalog with conservative mapping. | No separate rationale supplied. Do not infer clinical actions from ambiguous wording or use an LLM to silently convert historical text. |

### 3.2 Web capabilities — PO-14–PO-19 and PO-21

| ID | Status and owner decision | Alternatives/recommendation considered | Rationale, impact, and remaining gate |
|---|---|---|---|
| PO-14 | **APPROVED** — Web CSV import and export are required capabilities, separate from backup/restore and transfer. | Keep Android-only or provide one canonical CSV on both. Phase 2 recommended user-controlled Web CSV if wanted. | No separate rationale supplied. Exact columns, unknown-state representation, import preview, duplicate policy, and formula-injection safeguards remain design gates. |
| PO-15 | **APPROVED** — Web must have separate user-level backup/restore. | Server backup only or both server and user archive. Phase 2 recommended a separate Web archive. | No separate rationale supplied. Archive contents, schema/version policy, restore ownership/modes, preview, and recovery behavior remain design gates. This is not operational disaster recovery or cross-platform transfer. |
| PO-16 | **APPROVED** — Web reports, PDF, print, and sharing are required capabilities. | Android-only, limited reports/print, or defer. Phase 2 recommended evaluating Web PDF independently. | No separate rationale supplied. Report content, recipient/date filters, redaction, and explicit share/download behavior remain design gates. Do not upload or share automatically. |
| PO-17 | **APPROVED** — Web supports System/Light/Dark modes and semantic palette tokens. | Limited Light/Dark or no parity requirement. Phase 2 recommended these modes without forcing identical native presentation. | No separate rationale supplied. Maintain contrast, semantic meaning, Arabic/RTL layout, and accessible preferences. |
| PO-18 | **APPROVED** — Add a Web medication tracker after its domain semantics are defined. | Read-only view, Android-only, or defer. Phase 2 recommended Web capability after medication semantics. | No separate rationale supplied. Define medication record, schedule, intake mark, and verification independently. A schedule/reminder is not proof of ingestion. This does not approve any transfer scope. |
| PO-19 | **APPROVED** — Target capability and semantic parity; Web and Android may have platform-specific UX. | Full feature/UI parity or no parity target. Phase 2 recommended semantic parity over pixel/UI parity. | No separate rationale supplied. Shared concepts must retain equivalent meaning and outcomes; native-only capabilities need not be recreated in Web without a product need. |
| PO-21 | **APPROVED** — Allow caregiver acknowledgement as metadata separate from the computed safety signal. Acknowledgement does not dismiss, hide, or erase the signal. | No acknowledgement, acknowledgement only, or dismissal with reason/expiry. Phase 2 recommended separate acknowledgement metadata. | No separate rationale supplied. Record actor and time; do not treat acknowledgement as clinical resolution. Dismissal is not approved. |

### 3.3 Operational policy — PO-13

| ID | Status and owner decision | Alternatives/recommendation considered | Rationale, impact, and remaining gate |
|---|---|---|---|
| PO-13 | **POLICY APPROVED; DEPLOYMENT EVIDENCE PENDING** — Require durable persistence and verified backup/recovery before describing or operating Web as production care-data storage. | Explicitly temporary/demo-only or defer. Phase 2 recommended no production durability claims until evidence exists. | The approved gate requires deployment-owner evidence for durable database provisioning, backup schedule, restore drill, retention/deletion policy, and recovery objectives. Source configuration alone does not prove any of these. No deployment owner or evidence was supplied. |

### 3.4 Web BYOK policy and technical architecture — PO-24

| ID | Status and owner decision | Alternatives/recommendation considered | Rationale, impact, and remaining gate |
|---|---|---|---|
| PO-24 | **PRODUCT POLICY APPROVED** — Web AI is BYOK only: each caregiver supplies their own provider key; no application/shared/pooled provider account, no application-provided AI fallback, and no cross-platform import/sync of credentials or private AI configuration. | This policy is settled and is not reopened here. Technical execution choices are recorded in Section 8. | **Approved by:** Product owner. **Date:** 2026-11-03. **Rationale:** “The Web application must use caregiver-supplied provider credentials rather than an application-managed AI service.” The technical execution direction is conditional and is not approved for implementation until its engineering/security gates pass. |

### 3.5 Transfer-only decisions — explicitly outside WP-01

The following IDs remain governed by Phase 2/Phase 3A and the separate transfer workstream. They are **not decided or approved by this WP-01** and are not Web parity prerequisites.

| ID | WP-01 status | Existing Phase 2 recommendation retained as a recommendation only |
|---|---|---|
| PO-09 | OUT OF SCOPE — no new decision | Defer medication tracker transfer until schedule and intake semantics are approved. Not a blocker for standalone Web medication work under PO-18. |
| PO-10 | OUT OF SCOPE — no new decision | First transfer scope recommendation is recipients, original notes, and addenda. |
| PO-11 | OUT OF SCOPE — no new decision | API may support multiple recipients; first-release UI may restrict scope. |
| PO-12 | OUT OF SCOPE — no new decision | Browser-mediated OAuth device authorization was recommended; no transfer implementation is authorized. |
| PO-20 | OUT OF SCOPE — no new decision | Preserve source identity with installation namespace and separate server-generated canonical IDs. Preserve stable Web IDs; do not implement transfer identity here. |
| PO-22 | OUT OF SCOPE — no new decision | Do not transfer generated AI summary text in transfer v1; this does not limit Web summary generation. |
| PO-23 | OUT OF SCOPE — no new decision | Read-only imported Android copies were recommended for transfer v1; this does not constrain native Web record editing. |

No transfer sessions, device linking, transfer-specific authentication, manifests, chunks, receipts, or synchronization are included or required by this contract.

### 3.6 Approved Web capability contracts

The following scope decisions are approved. Detailed formats and designs listed as pending remain gates; they are not silently filled in here.

| ID | Approved scope and explicit exclusions | Delivery status/dependencies | Data/security implications | Minimum acceptance criteria |
|---|---|---|---|---|
| PO-14 — CSV | Web user-controlled CSV import and export. Excludes Android-to-Web transfer, backup/restore, API keys, prompts, tokens, and private AI configuration. | Required; WP-07. Depends on WP-02 field and observation semantics. | Care data is portable; import must handle formulas, unknown values, and duplicates without silent changes. | Versioned/documented columns; preview before writes; explicit duplicate/conflict report; round-trip semantics documented; no silent overwrite; sensitive credentials/config excluded. |
| PO-15 — User archive | Separate Web user-level archive and restore. Excludes operational disaster recovery, Android `.carebackup.json` restore, and cross-platform transfer. Archive contents and restore modes are not yet approved. | Required; WP-08. Depends on stable WP-02 model and PO-13 deployment/recovery evidence for production. | Archive contains sensitive care data; access and retention must be scoped to authenticated owner; never include keys/tokens. | Versioned format; integrity validation; owner/scope preview; explicit restore mode and confirmation; failure must not partially destroy live data; test restore in isolation. |
| PO-16 — Reports | Web reports, PDF, print, and user-directed sharing. Excludes automatic upload, automatic sharing, and clinical diagnosis/treatment claims. Exact report content is pending. | Required; WP-09. Depends on WP-02 semantics and WP-04 evidence/AI labels. | Outputs can expose sensitive care data; apply least-data selection, explicit recipient/date scope, safe download/share, no content in logs. | Preview scope; preserve uncertainty, evidence attribution, and source dates; no silent share/upload; Arabic/RTL and print layout verified. |
| PO-17 — Theme | System/Light/Dark and semantic palette tokens. Excludes pixel-identical Android presentation. | Required; WP-10. Accessibility and localization work can proceed independently. | Color must not be the only safety/status cue; no clinical severity is implied by decorative palette. | Preference works across supported views; contrast and keyboard/accessibility checks pass; RTL unaffected. |
| PO-18 — Medication | Web caregiver medication tracker. Excludes prescription ordering, clinical dosing advice, verified-ingestion claims, and any transfer requirement. Exact schedule/intake contract remains open. | Required; WP-06. Depends on WP-02 states/date and a reviewed WP-06 model. PO-09 transfer decision is not a dependency. | Medication names/doses are sensitive; schedule is not ingestion; absence of event is not missed dose. | Create/edit/archive tracker entries; separate schedule, daily note medication state, intake mark, and verification; never infer adherence; audit/date/index behavior tested. |
| PO-19 — Parity target | Capability and semantic parity with intentional platform-specific UX. Excludes pixel/screen parity and mandatory replication of device-only features. | Required acceptance target; WP-11. | Shared data meanings must not diverge by UI or backend implementation. | Each shared capability has an equivalent meaning/outcome; platform-specific capabilities are explicitly classified and justified. |
| PO-21 — Safety acknowledgement | A caregiver may acknowledge a signal; acknowledgment metadata is separate from the computed signal. Excludes dismissal/hiding/removing the signal. | Approved; optional signal metadata is part of WP-04. Depends on reviewed signal identity/rules. | Store actor and time; acknowledgment is not clinical resolution. | Acknowledged signals remain computed and visible as acknowledged; underlying evidence and rule version remain unchanged. |

## 4. Web parity domain contract

This is a semantic contract. It does not require identical Room, JPA, or PostgreSQL schemas.

### 4.1 Recipient, relationship, and lifecycle

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Recipient** | Required: stable ID, name, owner scope, lifecycle. Optional: relationship, timestamps, provenance on legacy rows. | Name is required; relationship and timestamps may be absent on legacy Android data. Preserve stable IDs and child history. Web owner is the authenticated caregiver; Android owner is the local user/store. | Both platforms associate notes/plans/medication with the same logical recipient. Web ownership is enforced by authenticated caregiver context; Android remains local-first. | Final field limits and legacy lifecycle mapping must be checked against existing data before schema work. |
| **Recipient relationship** | Optional nullable free text, shared by owner decision; current Web maximum is 80 characters. | Null means not supplied, not “no relationship.” Never infer from names. Owned by the caregiver who manages the recipient; preserve source text. | Add a corresponding Android field in a future separately authorized implementation. | Localization and validation rules remain implementation details. |
| **Recipient lifecycle** | Required state `ACTIVE`, `ARCHIVED`, or `DELETED`; transition actor/time required for new events; archive timestamp optional outside archived state. | Archive is reversible and preserves children; archived recipient is read-only. Deletion is a separate explicit legal/data operation. Preserve prior transitions. | Android and Web present equivalent lifecycle meaning; no automatic deletion propagation or synchronization. | Physical purge, tombstone retention, legal requirements, and restoration behavior require PO-13/deployment/legal policy. |

### 4.2 Daily notes, observations, and dates

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Daily note** | Required: stable ID, recipient ID, note date, observation states for supported fields. Optional: free text, capture/update instants, time-zone provenance, source version. One original per recipient/local date. | Original is caregiver-owned through its recipient and is preserved. Corrections are events, not silent overwrites. Legacy missing timestamps remain missing/precision-marked. | Web and Android share field meaning; Android remains local-first. | Existing Web duplicate inventory and historical backfill are prerequisites to any uniqueness migration. |
| **Note date** | Required `LocalDate` for caregiver's local day at creation; `YYYY-MM-DD`; one original note per recipient/date. Optional IANA zone is provenance. | Date is never shifted by UTC conversion. Save the original date even if time zone changes later. | Android may add historical-date entry in a later UX release; Web already accepts dates. | Legacy Android rows may have no time-zone provenance; do not fabricate one. |
| **Date-only value** | Required calendar date with no time-of-day/instant, represented as `LocalDate`/ISO `YYYY-MM-DD`. | Never parse/serialize through UTC in a way that changes day. A date-only value is not a missing timestamp. | Preserve across browser, API, database, locale, and timezone boundaries. | Test locale display separately from stored date. |
| **Timestamp/instant** | Required for newly timed events when capture provides it; optional for legacy events. RFC 3339/UTC instant with offset semantics. | Missing legacy instant remains absent; do not invent one from a date-only value or offset-free local time. | UI may localize display; persisted instant remains stable. | Android text-addendum `LocalDateTime` has no zone; preserve original string and precision until resolved. |

### 4.3 Observation and verification

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Observation** | Required: state; typed value only when state is `RECORDED`. Optional: raw source value, field path, capture time. | Caregiver-owned within its note/event. Use a wrapper; preserve raw legacy values when mapping is uncertain. | Android primitive/non-null fields and Web nullable fields map into the same semantics, not by naive coercion. | Exact field catalog and value vocabularies are finalized with WP-02 fixtures. |
| **Observation state** | Required enum: `RECORDED`, `UNKNOWN`, `NOT_RECORDED`, `NOT_APPLICABLE`, `LEGACY_AMBIGUOUS`. `value` is required only for `RECORDED`; raw value is optional. | False/zero/empty/null are not interchangeable. `RECORDED,false` is an explicit negative only when source capture proves it; pain zero is recorded only when explicitly selected. Preserve raw source in ambiguous cases. | Same state meaning in both platforms. | Per-field legacy mappings need diagnostics and fixtures; no clinical defaults. |
| **Verification** | Optional sibling enum where meaningful: `VERIFIED`, `UNVERIFIED`, `NOT_ASSERTED`; never encoded as an observation state. | Default is not verified unless source evidence proves verification. `UNVERIFIED` does not mean no value was recorded. Owner/provenance follows the observation or intake event. | Keep separate from medication status and observation values. | Apply only to concepts where verification is meaningful; do not invent verification from an intake mark. |

### 4.4 Corrections and effective values

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Addendum** | Required: event ID, note ID, immutable event type/payload, recorded order/time precision. Optional: actor/source metadata where unavailable on legacy data. | Caregiver/account owns it through the parent note. Preserve text verbatim. Omitted correction field means unchanged. Existing events/order retained. | Android text-only append stays text; Web structured correction stays structured. Both appear in history. | Timestamp ordering for legacy zone-free events must remain explicit and deterministic. |
| **Structured correction** | Required: target field path, explicit operation, event/note IDs. Conditional value for `SET`; operations include `SET(value)`, `CLEAR`, `UNKNOWN`, and `NOT_RECORDED`. | Never overwrite the original note or earlier corrections. Do not infer a clear from null. | Apply only to named fields; preserve provenance and actor. | Exact payload schema and conflict/tie ordering are WP-02 implementation contracts. |
| **Effective value** | Derived only; required provenance references to original observation and applied event IDs/order. No independent caregiver-entered owner/value. | Recompute deterministically; never present projection as a new source fact. | Web/Android safety and summary inputs use approved projection rules. | Rule ordering and conflict behavior must be fixture-defined before use in safety calculations. |

### 4.5 Plans and medication

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Care plan** | Required: plan ID, recipient ID, lifecycle, immutable version relation. Optional: active accepted version and provenance/timestamps when unavailable in legacy data. | Caregiver-owned; preserve all prior versions/statuses/evidence. | Web and Android present equivalent caregiver meaning; no medication changes through plan actions. | Version numbering/status transitions must be harmonized in WP-05. |
| **Plan action** | Required when structured: allowlisted action ID and validated typed parameters. Legacy text item is retained separately and opaquely. | Unknown action is not coerced; no LLM conversion of historical free text. | Android free-text items and Web catalog map only when unambiguous. | Catalogue and exact parameter set require WP-05 validation. |
| **Plan version** | Required: version ID, plan ID, version number, status, source type, creation instant for new versions. Optional: actions/legacy text, evidence IDs, summary/baseline refs. | Editing creates a new revision; never erase earlier version. Caregiver owns versions through plan. | Preserve both native history forms with explicit mapping. | Concurrency/version conflict policy is a WP-05 design gate. |
| **Medication** | Required: ID, recipient ID, caregiver-entered name, doses-per-day, active state. Optional: raw dose/schedule text, creation time, source provenance. It is not a prescription order. | Caregiver-owned. Preserve exact source strings; do not normalize free text into dose times without an approved deterministic rule. | Web medication capability is approved by PO-18; Android remains offline-first. | Whether schedule normalization/reminders exist is not decided by PO-18 and requires separate design. |
| **Medication schedule** | Optional: raw caregiver-entered schedule text; normalized occurrences only if separately approved. | Missing schedule is not “no medication”; schedule is not an ingestion assertion. | Same meaning across platforms. | Dose occurrence, timezone, DST, and reminder behavior remain unresolved. |
| **Medication intake event** | Required: event ID, medication ID, local date, dose index, explicit mark state. Optional: marked instant, actor/provenance, verification. | Absence of a row means “not marked,” not “missed.” A mark is not verified ingestion. | Keep tracker marks distinct from medication status on a daily note. | Index base and multi-dose schedule semantics require WP-06 fixtures and caregiver review. |

### 4.6 Safety, summaries, and BYOK

| Concept | Meaning; required/optional fields | Null/unknown, owner/lifecycle, and history | Web/Android compatibility | Open detail |
|---|---|---|---|---|
| **Safety signal** | Derived only; required stable rule ID/version, severity after review, recipient/date window, evidence refs, explanation key. Optional acknowledgement actor/time. Not a diagnosis. | Caregiver acknowledgement metadata never removes signal. Missing/unknown/ambiguous follows explicit rule policy. | Owner-selected Android behavior is baseline only after clinical review and shared fixtures. | Specific rules, wording, thresholds, severity, and text patterns remain clinically gated. |
| **Safety-rule version** | Required immutable rule ID/version, input paths, deterministic condition, window, missing-data policy, evidence selection, effective interval, review state. Parameters/severity only after approval. | No arbitrary client/model executable rules. Preserve old versions for historical interpretation. | Same canonical data and rule version must yield the same deterministic output. | Qualified clinical reviewer has not been named; no clinical approval is claimed. |
| **Summary** | Required recipient/date window and validation/availability state. Optional narrative, observations, uncertainties, evidence, generated instant, model/prompt identifiers. Derived, not source care data by default. | Caregiver-scoped input; invalid/unavailable is explicit and not generated content. | Web uses only caregiver's selected provider/key after gates; Android BYOK remains separate. | Exact response/evidence schema and cache/persistence policy remain WP-04 decisions. |
| **Summary evidence** | Required parent summary and source entity type/ID; optional exact quote, field path, claim index. | Must resolve to caregiver-owned source data; never invent citations. Sensitive quotes are excluded from logs/analytics/exports by default. | Web/Android evidence semantics align without identical DTOs. | Exact quote granularity and display are tested in WP-04. |
| **AI-generated content** | Provider-produced text with provider/model and prompt/schema version where appropriate; never a verified clinical fact. | Failed/invalid output is not success-shaped. No application-managed/provider fallback. | Web and Android use separate configurations/keys. | Provider data retention and user disclosure require privacy review. |
| **BYOK provider configuration** | Required for a call: selected provider, model, user key, consent. Optional: supported base URL and connection state. | User owns key; Web key is memory-only under conditional direct-call direction. Never ordinary browser storage, application responses/logs/analytics/exports/archives/transfer. Android config remains Android-only. | No import/synchronization of either platform's key/configuration. | Direct execution remains conditional on engineering/security review; supported endpoints and nonsecret setting persistence need approval. |

## 5. Legacy-data handling policy

All migration work must produce a per-category diagnostic count and a reviewable report before changing stored records. Ambiguous values remain unchanged or are explicitly represented as `LEGACY_AMBIGUOUS`; they are not guessed.

| Existing value/representation | Intended meaning | Deterministic? | Safe conversion/diagnostics | Manual review? | Preserve unchanged? |
|---|---|---:|---|---|---|
| Backend nullable observation is null | Usually `NOT_RECORDED`; not a negative | Only if source contract confirms no value was submitted | Map to `NOT_RECORDED` only when supported by request/history evidence; count nulls per field | If null could encode an older unknown/N/A meaning | Preserve original raw row and provenance |
| Android empty/blank category string | Could be default, missing, or explicit placeholder | No, absent capture proof | Preserve exact raw string; map semantic state to `LEGACY_AMBIGUOUS` | Yes, for any attempt to assign a clinical value | Yes |
| Android `false` fall value | Explicit “no” only if capture flow proves a deliberate selection; otherwise default/ambiguous | Conditional | Inspect source flow/version; map to `RECORDED,false` only when proven, else `LEGACY_AMBIGUOUS` | Yes where source behavior is not proven | Yes |
| Android pain `0` | Explicit zero only if capture flow proves it was selectable and intentionally recorded | Conditional | Verify source editor/version; otherwise `LEGACY_AMBIGUOUS` | Yes where source behavior is not proven | Yes |
| Medication `unsure` / `UNKNOWN` | Explicit unknown status | Yes when source enum is recognized | Map to observation state/status `UNKNOWN`; verification remains `NOT_ASSERTED` unless separately evidenced | No, unless other legacy fields conflict | Preserve source enum |
| Android missing medication intake row | No caregiver “taken” mark exists | Yes as absence, not as missed ingestion | Represent “not marked”; do not create an intake event or missed-dose fact | No | Preserve absence |
| Android intake mark | Caregiver marked a dose, not verified ingestion | Yes for mark existence | Preserve medication/date/dose index; verification `NOT_ASSERTED` unless separate evidence exists | Review index base and duplicates | Yes |
| Offset-free Android addendum time | Local date-time with unknown zone/offset | No safe UTC conversion | Preserve original timestamp string and precision marker; do not synthesize UTC | Yes before ordering across timezone boundaries | Yes |
| Existing `active=false`, archive timestamp absent | Could mean inactive/deactivated or archived | No without lifecycle evidence | Preserve raw state; map only proven values; report counts and samples | Yes | Yes |
| Duplicate original notes for recipient/date | Distinct original records or repeated submissions | No safe merge | Inventory IDs, source, timestamps, contents hash; classify duplicate/conflict; no silent merge/delete | Yes before remediation | Yes until explicit resolution |
| Android plan `items: List<String>` / JSON history | Free-text action and historical versions | Only exact reviewed mapping is safe | Preserve source JSON/text and every version; map only deterministic allowlisted patterns | Yes for unmappable content | Yes |
| Free-text medication dose/schedule | Caregiver-entered text, not normalized prescription | No | Preserve exact string/count; do not infer time or dose; do not mark ingested | Yes before normalized schedule conversion | Yes |
| CSV row without stable note ID | Partial note export; no complete provenance | No stable identity | Require preview, matching policy, and duplicate handling before import; never overwrite existing notes | Yes for collisions | Preserve original CSV source |
| Generated summary/evidence | Derived provider output tied to a source window | Not a source observation | Keep separate from original records; preserve provenance only if product later approves persistence | Yes before treating as durable record | Yes as historical artifact if retained |
| Unknown enum or future field | Unsupported source meaning | No | Preserve raw value and report unsupported mapping; reject unsafe writes rather than coercing | Yes before mapping | Yes |
| Provider key, prompt, endpoint credential, or consent setting | Private AI configuration | Never a care-data migration | Exclude from database/API DTOs, CSV, archive, logs, analytics, and any cross-platform payload | Security review required for any exception; no exception approved | Do not import or synchronize |

**Mandatory invariants:** never discard history; never infer a clinical fact from missing information; never turn unknown into negative; never infer medication ingestion from a schedule/reminder; never overwrite original notes with corrections; never silently merge duplicates; never add uniqueness constraints before duplicate analysis; preserve stable IDs and provenance.

## 6. API compatibility principles

These are constraints for future work, not API changes in WP-01:

1. Authenticate every caregiver-owned Web operation and derive owner identity from the authenticated server context, never a request-supplied caregiver ID.
2. Scope parent and child lookups by the authenticated owner. Validate that recipient, note, addendum, plan, evidence, and medication references belong together. Use non-disclosing not-found behavior for inaccessible IDs.
3. Keep TypeScript request/response contracts aligned with backend OpenAPI/DTO definitions. Contract changes must update clients, validation, fixtures, and compatibility tests together.
4. Return predictable, field-specific validation errors. Never return credentials, provider request headers, internal exception details, or raw provider errors containing secrets.
5. Represent date-only values as `YYYY-MM-DD` strings/`LocalDate`; do not convert them through UTC instants. Represent timestamps as RFC 3339 instants with explicit offset semantics.
6. Preserve append-only corrections and explicit event order. An omitted field is not a clear operation.
7. Keep API keys out of ordinary API requests/responses except the separately reviewed direct-to-provider browser call. A Web BYOK key must never be accepted as a persistent account setting by the application backend.
8. No transfer endpoint, synchronization protocol, device authorization, transfer receipt, or cross-platform credential/configuration API is introduced.
9. Preserve existing Web workflows and response semantics unless the corresponding approved contract explicitly changes them.

## 7. Database migration principles

No database migration or schema change is authorized by WP-01.

- Use reviewed, versioned Flyway migrations; do not rely on Hibernate schema mutation.
- Prefer expand/backfill/validate/contract steps so existing Web records remain readable throughout rollout.
- Before uniqueness constraints, run duplicate preflight queries and produce counts/IDs for review. Never delete or merge duplicates automatically.
- Backfills may only convert values with deterministic evidence. Ambiguous rows are reported and retained; no default value may convert into a clinical fact.
- Preserve stable identifiers, source data, event history, audit/provenance, and timestamps/precision.
- Validate migration behavior against representative old-schema snapshots, including nulls, duplicates, unknown enums, archived/inactive recipients, and correction histories.
- Every migration needs a recovery strategy: reversible migration where safe, otherwise a tested forward-fix plus verified backup/restore path. Do not claim a rollback is safe when it could discard new writes.
- Verify row counts, foreign-key ownership, checksums/selected field equality, and application workflows before/after migration.
- Production deployment is blocked until PO-13 evidence demonstrates durability and recovery; no source configuration alone establishes that guarantee.

## 8. Minimum caregiver workflow contract

The workflows below define parity acceptance intent. An approved scope does not mean its implementation is complete. Shared prerequisites are: authenticated owner scoping, compatible date/observation/correction semantics, and explicit loading/empty/error states.

| Workflow | Prerequisite decisions | Minimum acceptance criteria |
|---|---|---|
| 1. Register/login and workspace | PO-13 for production claims | A caregiver can register/login, access only their workspace, and receive explicit auth/expiry errors. No cross-account record disclosure. |
| 2. Create/manage recipients | PO-01, PO-02 | Create and find recipients; relationship is optional; archive is reversible and preserves history; archived records are read-only; deletion is a separate explicit action and does not imply Android deletion. |
| 3. Create daily notes | PO-03, PO-04, PO-05 | Create for correct caregiver-local date; historical Web date behavior is explicit; duplicate date is surfaced; values preserve `RECORDED`/unknown/missing/N/A/legacy ambiguity; no silent default-to-negative. |
| 4. Inspect note detail/history | PO-04, PO-05 | Recipient/date filters use inclusive date-only semantics; original and event history remain visible; loading/empty/error states do not imply missing records were negative. |
| 5. Add correction | PO-05, PO-06 | Add text or typed correction without changing original; explicit field mask; intentional clear differs from omitted; actor/time/provenance retained; effective view links back to event timeline. |
| 6. Review summary/evidence | PO-05, PO-06, PO-07, PO-24 | Summary claims resolve to owned note/addendum evidence; uncertainty and safety are separate; invalid/unavailable AI is not presented as generated; safety rule/version and acknowledgement are visible. |
| 7. Generate/manage care plan | PO-05–PO-08, PO-24 | Version history is preserved; unmappable text remains opaque; typed actions validate parameters/evidence; no action changes medication/dose/schedule; AI use is BYOK only. |
| 8. Configure Web BYOK and generate AI | PO-24 and conditional ADR in §9 | Provider/model and supported endpoint shown; key is entered, tested, replaced, and removed in memory only; data destination is disclosed; user key is used; invalid/unavailable/rate-limited/malformed responses show explicit errors; no fallback provider or app key. |
| 9. Handle AI failure | PO-24 | Failure does not create success-shaped content or clear deterministic safety. User may retry the same selected provider; switching provider requires explicit user choice. |
| 10. Medication tracker (if implemented) | PO-05, PO-18; WP-06 domain review | Medication, schedule, intake mark, and verification are separate; absent mark is not missed; mark is not verified ingestion; schedule/reminder never means taken. |
| 11. CSV import/export | PO-05, PO-14 | User-controlled separate operation; documented columns/unknown-state behavior; preview and duplicate conflict; no silent overwrite; keys, prompts, tokens, and private configuration absent. |
| 12. Web archive/restore | PO-13, PO-15 | Versioned archive scope, preview, explicit restore mode, integrity checking, ownership isolation, failure-safe recovery; no confusion with operational backup or transfer. |
| 13. Reports/PDF/print/share | PO-05–PO-07, PO-16, PO-24 | Selected recipient/date scope is explicit; uncertainty/evidence/source labels survive; no automatic upload/share; private data is not leaked to logs or analytics. |
| 14. Arabic/English, RTL, responsive and accessible use | PO-17, PO-19 | Correct directionality, dates and labels; keyboard/screen-reader operation; responsive desktop/mobile browser layout; semantic colors meet contrast requirements. |

## 9. Web BYOK architecture decision record

### 9.1 Status

**PO-24 product policy:** APPROVED.

**Owner direction:** Direct browser-to-provider is conditionally preferred and was selected as the target direction by the product owner on 2026-11-03.

**Technical selection:** **PENDING final engineering/security review and implementation-origin/provider tests.** This conditional direction is not an authorization to implement or a claim that the design has passed formal review.

The conditional direction prefers direct provider execution because it avoids adding the caregiver's key to the application backend's custody boundary. It is viable for the currently configured Gemini/OpenRouter HTTP endpoint shapes based on a point-in-time CORS preflight, but must be revalidated for exact provider, account, model, browser, deployment origin, and request format. If a provider fails a gate, show an explicit provider-unavailable/configuration error; do not proxy through the backend or select another provider silently.

### 9.2 Alternatives and consequences

| Option | Benefits | Risks/consequences | WP-01 disposition |
|---|---|---|---|
| Direct browser-to-provider | App backend does not receive the API key; provider-specific CORS preflight was observed for current endpoint paths; user controls provider credential and can see destination. | Key exists in browser process memory while used and is exposed to XSS, malicious extensions, compromised scripts, devtools, or device malware; reload requires re-entry; care data goes directly to provider; CORS/API behavior can change; custom URLs can exfiltrate key/data. | **Conditional target direction endorsed by owner; final selection awaits security/engineering gates.** |
| Backend-mediated BYOK | Centralized network/error handling; avoids browser-to-provider CORS dependency; provider credentials can be supplied per call. | Key must transit through and be visible to application backend, JVM/framework, proxies, and possibly APM/error tooling; Java immutable strings cannot be reliably zeroed; logging, heap dumps, SSRF, access-control, and operator threat exposure increase. “Transient” does not mean the backend never sees the secret. | Not selected. Would require a separate security review and explicit owner approval before any implementation. |
| Pending/no architecture | Avoids prematurely freezing a technical design. | Blocks provider integration and Web AI settings implementation until review and decision. | Safe fallback if any direct-call gate fails; no server-managed fallback is permitted. |

### 9.3 Threat analysis and required controls

| Threat/asset | Direct-call concern | Required control/review gate |
|---|---|---|
| Caregiver API key | XSS, malicious extension, compromised bundle, browser inspection, accidental telemetry. Browser cannot guarantee secure erasure from memory. | Keep only in component/session memory; clear references on removal and logout; never persist to `localStorage`, `sessionStorage`, IndexedDB, cookies, URL, logs, analytics, or ordinary API DTOs. Explicitly document re-entry after reload. Review CSP, third-party scripts, dependency supply chain, and XSS protections. |
| Care data sent to provider | Direct call transmits selected notes/prompts to the configured provider, outside the Web backend. | Before first generation and provider changes, clearly name provider and endpoint and describe data sent; obtain separate, explicit consent; disclose provider retention/processing based on current provider terms; send only minimum data for the requested period. Privacy/legal review required. |
| Provider substitution/fallback | Invalid key or outage could otherwise trigger an app-owned or alternate provider. | Bind each request to the selected user provider/model; on 401/403, timeout, rate limit, outage, CORS, or malformed output, return explicit state. No application key, alternate provider, or hidden automatic switch. Deterministic safety remains separate and can still be displayed. |
| CORS/provider drift | Provider may change CORS, endpoint behavior, or model compatibility. | Test preflight and authenticated browser request from actual deployed origins for each supported provider/model; version compatibility tests and periodic revalidation; disable that provider with explicit error if checks fail. |
| Custom base URL | User could direct key and care data to an attacker-controlled host. | Only support base URLs for providers that explicitly support them; HTTPS only; display exact destination; reject credentials/query secrets in URL; do not allow arbitrary endpoints until reviewed. A backend proxy is not an SSRF workaround. |
| Request/response observability | Browser logs, exception details, analytics, session replay, traces, and support reports could capture keys or clinical text. | Do not instrument provider headers/body. Redact error messages and URLs; disable session replay for sensitive settings/generation views; test with sentinel secrets and care text. |
| Authentication boundary | Web account authentication is separate from the provider key. | Use authenticated backend only to fetch caregiver-owned source data; direct provider request must not include application JWT unless provider specifically needs it (not expected). Never send provider key to the Web API. |
| User consent/revocation | Consent to send care data to AI is different from account login, Android consent, or any future transfer. | Separate consent per Web provider/destination; revocation/removal stops subsequent calls and clears in-memory key; do not import Android consent/key/configuration. |
| Browser cancellation/failure | A cancelled browser request may already have reached provider. | Support `AbortController` and explicit cancelled/unknown completion state; never represent cancellation as successful output; explain that provider may have received request once dispatched. |

### 9.4 Web BYOK settings and runtime contract

| Setting/behavior | Required contract |
|---|---|
| Provider selection | User chooses an explicitly supported provider. Display provider name, destination, and the fact that selected note data will be sent there. |
| API key entry | Masked input; never send to application backend; do not echo it in ordinary responses, logs, error reports, analytics, CSV, archive, or transfer. |
| Key lifetime | Memory-only under the conditional direct-call design. Do not put keys in browser storage, cookies, URLs, telemetry, or state snapshots. Re-entry after refresh is expected unless a separately reviewed secure-storage decision is approved. |
| Model selection | Provider-specific allowlisted/model identifiers; invalid model returns provider error. Do not silently substitute a model. |
| Custom base URL | Available only for provider configurations that support it and pass review; HTTPS and exact destination disclosure required. Arbitrary host support remains unapproved. |
| Connection test | Make a minimal, nonclinical provider request directly from browser; never use an application-owned key. Show success/failure, selected provider/model, and safe error category without echoing secret or request content. |
| Replacement/removal | Replacement takes effect only after explicit save/test as product design allows; removal clears in-memory key and connection state. No removal or rotation can be silently undone by fallback. |
| Invalid key | Provider authentication failure is explicit; do not call another provider or backend AI. |
| Timeout/network/CORS | Provide distinct retryable/nonretryable feedback; retry only the same selected provider after explicit or documented user action. |
| Rate limit/quota | Show provider rate/quota error and retry guidance; no use of application account or pooled key. |
| Malformed/refused output | Treat as invalid/unavailable; do not display as generated summary or plan. Preserve deterministic safety separately. |
| Consent/disclosure | Explain what caregiver data is transmitted, to which provider/endpoint, and that provider terms/retention apply. Consent is distinct from Web login and Android BYOK consent. |
| Nonsecret configuration persistence | Provider/model/base URL persistence policy is not yet approved. Keep implementation out of ordinary API responses; do not infer that key persistence is allowed. |

### 9.5 Engineering/security approval checklist

Before final technical selection or implementation:

- [ ] Security reviewer reviews the browser-key threat model, XSS/CSP posture, dependencies, memory-only lifecycle, telemetry, support tooling, and consent boundary.
- [ ] Engineering validates actual browser CORS preflight and authenticated API calls from each deployed origin and for each supported endpoint/model.
- [ ] No credential or note content is observed in app API requests, logs, analytics, error reports, browser storage, or traces during tests.
- [ ] Key invalidation, timeout, 429, provider outage, browser abort, malformed output, and CORS failure all produce explicit states with no provider substitution.
- [ ] Provider endpoint/key restrictions, terms, user billing/quota, data retention, and data-processing disclosure are reviewed.
- [ ] Custom URL policy is explicit and validated; unsupported/arbitrary endpoints are rejected.
- [ ] The present server environment-key failover is identified for later removal/replacement; no implementation is included in WP-01.
- [ ] Product owner approves the final reviewed architecture record. Until then, PO-24 remains approved but execution architecture stays conditional.

## 10. Clinical, security, privacy, and operations review requirements

| Review area | Required reviewer/evidence | Current status and effect |
|---|---|---|
| Clinical safety rules (PO-07) | Qualified clinical reviewer must review each rule's inputs, thresholds, text patterns, severity, missing-data behavior, explanation, and fixtures. | **Pending; reviewer not named.** WP-04 rule activation/claims remain blocked. Product owner selected Android logic only as candidate baseline. |
| BYOK security architecture (PO-24) | Security reviewer and Web engineering review as specified in §9.5. | **Pending formal sign-off.** Direct browser call is conditionally preferred, not implementation-approved. |
| AI privacy/consent | Privacy/legal review of provider disclosures, data minimization, provider terms/retention, consent, and revocation. | **Pending.** No provider data-processing guarantee is inferred from source code. |
| PO-13 durability | Deployment owner supplies durable DB, backup schedule, restore-drill result, retention/deletion policy, and recovery objectives. | **Pending evidence.** Production durability claims and production release remain blocked. |
| Medication semantics (PO-18) | Product/engineering review of free-text schedule, dose index, intake mark, verification, DST/timezone, and absent-row semantics; caregiver usability review. | **Pending WP-06 design.** PO-09 transfer decisions are not prerequisites. |
| Backup/restore (PO-15) | Product/security/operations review of archive scope, encryption/access, restore preview, merge/replace semantics, purge, failure recovery. | **Pending WP-08 design.** User archive does not replace operational backups. |
| CSV/reports | Product/privacy review of data fields, CSV formula safety, report scope, export controls, and sharing behavior. | **Pending feature-specific contracts.** No keys/prompts/private config in artifacts. |

## 11. Dependency and readiness matrix

“May proceed” below means research, UX shell, test planning, or diagnostics only unless the approved semantic contracts and review gates are complete. It does not grant application implementation authority.

| Work package | Decision state | May proceed now | Blocked work and reason |
|---|---|---|---|
| WP-01 | Owner decision capture recorded; exit is conditional on review gates being tracked. | Documentation review; schedule named clinical/security/deployment-owner reviews. | No unrestricted implementation; review records must remain honest and linked. |
| WP-02 | PO-01–PO-06 approved; PO-04 timezone basis approved. | Read-only duplicate/lifecycle inventory, field mapping, migration design, fixture preparation. | Schema constraints/backfills blocked until duplicate analysis, ambiguous legacy mapping policy, and reviewed migration plan are complete. |
| WP-03 | Product direction is capability parity. | Non-data-dependent navigation/accessibility discovery. | Data-integrated core workflow changes depend on WP-02 semantics and regression plan. |
| WP-04 | PO-07 direction and PO-21 behavior approved; PO-24 policy approved. | Evidence/error UX research; prepare clinical/security review materials. | Safety rule activation blocked on clinical approval; provider integration blocked on final engineering/security approval of direct BYOK architecture. |
| WP-05 | PO-08 approved. | Catalogue/legacy-plan inventory and UX research. | Data changes depend on WP-02 and evidence/safety semantics from WP-04; exact action mapping must be reviewed. |
| WP-06 | PO-18 Web tracker approved. | Define Web-only domain alternatives and collect caregiver requirements. | Implementation waits for observation/date contracts, schedule/intake/verification semantics, and WP-04 if medication signals are used. PO-09 transfer scope does not block this work. |
| WP-07 | PO-14 approved. | Draft data inventory and CSV security/preview design. | Import/export implementation waits for stable WP-02 semantics, field/unknown representation, and duplicate handling. |
| WP-08 | PO-15 approved; PO-13 production policy approved. | Define user archive scope and restore modes; obtain deployment evidence. | Restore implementation waits for stable schema and tested failure/recovery behavior. Production durability claims wait for deployment-owner evidence. |
| WP-09 | PO-16 approved. | Report use-case and privacy discovery. | Implementation waits for stable WP-02 data, WP-04 evidence/AI labels, and approved report content/privacy rules. |
| WP-10 | PO-17 and PO-19 approved. | Accessibility, Arabic/English/RTL, responsive, and theme design/research. | BYOK settings/generation wait for §9 technical security approval; key storage remains memory-only under the conditional direction. |
| WP-11 | Depends on WP-01–WP-10 as applicable. | Define release evidence format. | Release/parity claim blocked on required implementations/tests, clinical/security reviews, and PO-13 deployment evidence. |

### 11.1 Critical blockers

1. **WP-02 schema freeze:** duplicate preflight plus safe disposition for historical duplicate dates and ambiguous observation/lifecycle values.
2. **WP-04 clinical behavior:** qualified review of the Android rule baseline; no clinical approval is recorded.
3. **Web BYOK execution:** final security/engineering selection, provider/origin tests, and privacy disclosure review.
4. **Production readiness:** deployment-owner evidence for PO-13.
5. **Medication implementation:** WP-06 medication schedule/intake/verification contract; no transfer dependency.
6. **Optional data features:** field-specific acceptance and security contracts for CSV, archive/restore, reports, and sharing.

## 12. Outstanding questions and assigned actions

No product-owner question among the requested Web parity decisions remains silently assumed. The following actions remain open and must be completed by the indicated role; no individual's name was provided:

| Action / exact question | Owner role | Status and blocking impact |
|---|---|---|
| Inventory `(recipient, noteDate)` duplicates. For each collision, should the records remain as explicit historical conflicts, and what non-destructive rule allows future unique notes without hiding either record? | Web data/engineering provides evidence; product owner decides disposition after review. | Pending evidence/decision; blocks uniqueness migration and WP-02 schema constraint. |
| Which existing `active=false` records are archived versus merely deactivated, and what should happen to ambiguous records? | Web data/engineering reports evidence; product owner approves ambiguous mapping. | Pending; blocks lifecycle backfill and deletion behavior. |
| For each old observation field, does the source UI/version prove empty, `false`, or zero was explicitly chosen, or must it remain `LEGACY_AMBIGUOUS`? | Android/Web engineering supplies capture-path fixtures; product owner approves exceptions. | Pending; blocks observation backfill and WP-02 implementation. |
| Which Android safety rules, exact thresholds/text patterns, severity, explanation, and missing-data behavior pass clinical review? | Qualified clinical reviewer (not named) and product owner. | Pending; blocks WP-04 activation and clinical claims. |
| Does direct browser-to-provider pass review for each supported provider, model, deployed origin, XSS/CSP posture, telemetry, custom URL policy, and key lifecycle? | Security reviewer (not named) and Web engineering. | Pending; blocks BYOK integration/settings. |
| What consent/disclosure language and provider retention/processing statements are approved for each supported provider? | Privacy/legal reviewer (not named) and product owner. | Pending; blocks provider-facing generation release. |
| Does deployed infrastructure meet PO-13 persistence, backup schedule, restore-drill, retention/deletion, RPO/RTO, and recovery evidence requirements? | Deployment owner (not named). | Pending evidence; blocks production durability claims and release gate. |
| Which CSV columns and import collision actions are allowed? | Product owner and WP-07 owner. | Pending feature contract; blocks WP-07 implementation. |
| Which Web archive entity types are included, and is restore merge, replace, or restore-to-new-workspace? | Product owner and WP-08 owner. | Pending feature contract; blocks WP-08 implementation. |
| Which report templates, fields, periods, and share/download destinations are supported? | Product owner and WP-09 owner; privacy review. | Pending feature contract; blocks WP-09 implementation. |
| Does WP-06 retain raw schedule text only, or add normalized schedule/dose occurrences/reminders; how are dose index, local date, mark, and verification defined? | Product owner, WP-06 engineering, caregiver review. | Pending domain contract; blocks medication implementation. PO-09 transfer scope is not a dependency. |
| Which nonsecret provider/model/base-URL preferences, if any, may persist in Web browser settings, and which exact provider endpoints are supported? | Product owner after security/engineering review. | Pending; no key persistence is allowed. Blocks final Web BYOK settings contract. |

## 13. WP-01 completion and exit assessment

**Assessment:** WP-01 decision capture and contract documentation are complete, with explicit review and implementation gates. This is not a claim that all later work is unblocked or that the architecture is production-ready.

- PO-01–PO-08, PO-13–PO-19, PO-21, and PO-24 have explicit owner statuses and effective date.
- PO-07 remains clinically gated; PO-13 remains evidence-gated; Web BYOK execution remains security/engineering-gated.
- PO-09–PO-12, PO-20, PO-22, and PO-23 remain transfer-only and undecided; they are not Web parity blockers.
- The domain contract distinguishes values, unknown, not recorded, not applicable, legacy ambiguity, verification, corrections, effective projections, dates, and instants.
- Legacy migration rules forbid silent deletion, inference, merge, overwrite, and premature uniqueness constraints.
- Minimum Web workflows and cross-WP dependencies are documented.
- No historical Phase 1 parity classification was edited.
- WP-02 may proceed with read-only inventories and contract/fixture preparation; schema changes remain blocked until duplicate and ambiguous-data handling gates pass.
- WP-04 may prepare review artifacts; clinical rule activation and provider integration remain blocked pending review.

**Implementation authorization:** None. No application source, API, database schema, migration, Android source, or transfer mechanism was changed or authorized.

## 14. Documentation consistency notes

1. Phase 1/2/3A and other sections of the existing roadmap describe Web AI as backend-managed. Those descriptions record the prior architecture/baseline and are superseded for future product requirements by approved PO-24. They were not rewritten as historical audit evidence.
2. The current backend environment-key Gemini/OpenRouter failover conflicts with PO-24 and must be addressed in a separately authorized implementation phase. WP-01 does not change it.
3. `application.yml` describes a template fallback while the inspected production provider wiring is a primary/backup failover ending in `AI_UNAVAILABLE`; reconcile the description against the actual future BYOK implementation rather than preserving an ambiguous fallback claim.
4. The Phase 1 parity matrix remains the historical 33-row baseline and has not been modified.
5. No deployment durability, clinical approval, or formal security approval is claimed.

---

**End of WP-01 Product Decisions and Web Parity Contract.**
