## 5. Target Web Product Capabilities

### 5.1 Recipients and caregiver workspace

**Preserve:** Authenticated accounts, caregiver ownership checks, recipient creation, recipient editing, and current list/detail workflows.

**Improve:**

- Define recipient lifecycle semantics in coordination with Android.
- Present active and archived/inactive recipients consistently once PO-02 is approved.
- Make destructive actions explicit and distinguish archive/deactivate from permanent deletion.
- Preserve recipient history and show the consequences/counts of destructive operations.
- Align recipient field validation and relationship handling.

### 5.2 Daily notes, observations, addenda, and history

**Preserve:** Web note CRUD boundaries, date selection, note detail, filters, pagination, server-side ownership checks, and append-only correction history.

**Improve:**

- Define the observation state model for recorded, unknown, not recorded, not applicable, and legacy ambiguous values.
- Keep verification separate from the observation value.
- Align date and timezone meaning without shifting a calendar date through UTC conversion.
- Decide and enforce the original-note cardinality rule only after duplicate analysis and approval.
- Preserve original note content and append-only history.
- Show correction events and their effects without hiding the original values.

### 5.3 Home, trends, and safety

**Preserve:** Web dashboard and summary entry points, server-derived signals, and current summary workflow.

**Improve:**

- Establish shared deterministic safety-rule definitions and fixtures.
- Make signal evidence, date windows, rule version, and source records traceable.
- Avoid silently changing thresholds or interpreting free text as a confirmed clinical event.

### 5.4 AI summaries and care-plan generation

**Preserve:** Web backend-managed AI provider chain, server-side secrets, summary endpoint, plan-generation endpoint, and Web-specific prompt ownership.

**Improve:**

- Align the meaning of summary fields, evidence references, uncertainty, and source transcript selection.
- Make provider unavailable, timeout, malformed response, and validation failure explicit states.
- Never label an unavailable/error message as an AI-generated summary.

**Remain platform-specific:** Android BYOK stays on Android.

### 5.5 Care plans

**Preserve:** Web plan generation and version persistence.

**Improve/add:** Create/edit/review flows, structured action catalogue, historical versions, controlled activation.

### 5.6 Medication management

**Add only after approval:** Medication identity, schedule, and per-dose intake events with clear history and audit trail. Do not infer ingestion from a reminder or schedule alone.

### 5.7 Portability, reports, and settings

**Proposed additions subject to decisions:** CSV import/export, full Web backup/restore, PDF reports, print-friendly output, theme modes, complete settings hub, sample/demo data (if approved).
