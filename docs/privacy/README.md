# Privacy documentation — status and gap

> **Status: this repository has no approved privacy policy and no terms document.**
> This file records that gap, summarizes what the code verifiably does, and lists
> what must exist before a legal link is added to the public landing footer.
> It is **not** a privacy policy and makes no legal claim.

## Decision (issue #961)

The landing footer gets **no privacy link and no terms link** in this change.

At the time of this change `docs/privacy/` contained only `.gitkeep`, and no
terms document exists anywhere in the repository. A link to a placeholder or a
404 would be worse than no link, and writing policy text without owner/legal
approval would invent terms nobody agreed to. The footer therefore stays as it
is: GitHub repository, Android releases, and sign-in only
(`web/src/features/landing/LandingFooter.tsx`).

This gap was already flagged in `docs/landing-page-audit-2026-11-13.md` (finding
10, "No privacy or terms link anywhere"). Issue #255 asked for a link to the
repo's privacy documentation but its first step was to verify the target exists;
it does not, so that approach was never safe to implement.

## What the code actually does (verified from source)

The following is a factual inventory of the implementation, **not** a statement
of policy. It is provided so that whoever writes the real policy can start from
what the software does rather than from guessed wording.

### What is stored

The backend persists owner-scoped records in PostgreSQL (JPA entities under
`backend/src/main/java/com/caregiver/`, migrations `V1`–`V17` under
`backend/src/main/resources/db/migration/`):

- **Accounts** (`auth/Caregiver.java`): email (unique, normalized lowercase),
  password hash, creation time. No plaintext password is stored.
- **Care recipients** (`recipient/CareRecipient.java`): name, relationship, date
  of birth, allergies, conditions, emergency-contact name and phone. These are
  health-adjacent details about identifiable people; `docs/api/recipients.md`
  explicitly calls the care-detail fields health data.
- **Daily notes** (`notes/DailyNote.java`, `notes/ObservationColumns.java`):
  note date, mood, appetite, sleep, mobility, medication status/verification,
  pain score, fall report, and free text (`TEXT`, up to 20,000 characters per
  `notes/NoteRequest.java`), plus append-only addenda and correction events
  (the latter store changed values as `value_json`).
- **Care plans** (`plans/CarePlan.java`): versioned plan text (`content`) with
  links to the notes it was derived from.
- **Medications** (`medications/`): display name, dose text, schedule text,
  intake states (taken/missed/unsure), and void reasons.
- **Audit events** (`audit/AuditEvent.java`): actor ID, event type, target, and
  caller-supplied `safe_metadata`; `AuditService` redacts password/token/key
  patterns, but the metadata field is otherwise free-form.
- **Safety acknowledgements** and idempotency/request-digest records.

### Who can see it

- All API routes except register and login require a bearer token
  (`backend/src/main/java/com/caregiver/config/SecurityConfig.java`,
  `auth/JwtAuthenticationFilter.java`). The JWT subject is the caregiver UUID
  (`auth/JwtService.java`).
- Ownership is enforced in every service through `AuthContext.currentCaregiverId()`
  and owner-scoped repository queries; another caregiver's IDs return `404`,
  never `403` (for example `recipient/RecipientService.java`,
  `notes/DailyNoteService.java`, `plans/PlanService.java`). Clients never submit
  a caregiver ID.
- There are no roles or `@PreAuthorize` rules in the backend; "owner" is the
  only boundary. BYOK AI routes are additionally rate-limited per caregiver
  (`ratelimit/RateLimitFilter.java`).
- [Unverified from code] Any operator, backup, or support access to the
  production database is a deployment/process question, not visible in this
  repository.

### Retention and deletion

- **No retention schedule, purge job, TTL, or data-export feature exists in the
  code.** There are no `@Scheduled` cleanup jobs and no repository delete
  methods in backend main sources.
- The one `DELETE` endpoint voids a medication intake event: it sets
  `voided_at` / `voided_by` / `void_reason` and leaves the original row intact
  (`medications/MedicationIntakeService.java`; see `medications/README.md`).
- Archiving a recipient is reversible and non-destructive
  (`recipient/CareRecipient.java`, migration `V9`).
- [Unverified from code] Whether the deployed environment has backup rotation,
  a retention policy, or a legal hold cannot be determined from this repository.
  Roadmap docs treat physical purge and retention as pending privacy/legal
  decisions (`docs/product-alignment/web-parity-roadmap/wp-01/README.md`).

### Client-side storage and external AI flows

- **Web:** the session JWT lives in `sessionStorage` (not `localStorage`) under
  `caregiver-auth-session-v1` (`web/src/features/auth/session.ts`). Other
  localStorage keys hold language, theme, and AI provider/model preference —
  never the BYOK API key, which stays in React memory
  (`web/src/features/ai/aiPreferences.ts`, `ByokContext.tsx`).
- **BYOK AI:** the user chooses a provider and supplies their own key. When a
  summary or plan is requested, the backend sends per-note transcripts — note
  ID, date, and all observation values **including free text verbatim** — to
  that provider (`ai/SummarySourceLoader.java`, `ai/SummaryDataBuilder.java`,
  `ai/ChatCompletionsLlmClient.java`). Recipient name and date of birth are not
  included in those transcripts. The connection test sends no patient data.
- The UI discloses this before the user enables BYOK: the key is kept only in
  the tab's memory, note text is sent to the chosen provider, and some providers'
  free tiers may use submitted content for product improvement
  (`web/src/i18n/translations.ts`, `web/src/features/ai/AiSettingsDialog.tsx`,
  `docs/architecture/byok-execution.md`).
- [Unverified from code] No data-processing agreement or retention promise from
  any AI provider is inferred from source code.

### The synthetic-data caveat

The repository states that the project is a capstone using synthetic data only,
is not medical advice, and must not be used with real patient or care-recipient
data (`README.md`, `web/src/features/landing/landingCopy.ts` project notice).
This is a project caveat, not a privacy policy. [Unverified from code] Whether
real data has ever been entered cannot be established from source.

## What must exist before a footer link is added

1. An approved privacy policy, owned and signed off by the project owner (and
   reviewed for legal adequacy), committed under `docs/privacy/` as a real
   document. It should cover what is stored, purposes, retention and deletion,
   who can see it, third parties/processors (including BYOK providers), user
   rights, and a contact point. The verified facts above are input to that
   document, not a substitute for it.
2. An approved terms document, if a terms link is wanted. None exists today; a
   terms link must not be added until one does.
3. A stable URL that actually resolves to the published document, checked before
   shipping — never a guessed path.
4. Localized labels in `web/src/features/landing/landingCopy.ts` for both `en`
   and `ar`, using the footer's existing external-link pattern:
   `target="_blank"`, `rel="noopener noreferrer"`, and the existing visually
   hidden `opensNewTab` announcement from #830. The Arabic footer is RTL and
   must mirror the English layout.

Until all of those hold, the correct state is this written decision and no link.
