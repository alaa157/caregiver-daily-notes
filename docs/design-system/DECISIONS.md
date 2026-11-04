# Design System Parity Decisions

Date: 2026-11-04

This register records product and implementation direction for web parity with the Android design system. It supplements the Android extraction; it does not rewrite or normalize the source token values. The status column records decision maturity: Proposed items are the stated direction but remain proposed, Deferred items are outside the current release scope, and Open items require review before the affected work proceeds.

## Status overview

| ID | Decision area | Status | Owner |
|---|---|---|---|
| DS-01 | Primary color source | Proposed | TBD |
| DS-02 | Foreground color on light accents | Proposed | TBD |
| DS-03 | Metrolist | Proposed | TBD |
| DS-04 | Dynamic accent naming | Proposed | TBD |
| DS-05 | Pure-black mode | Deferred | TBD |
| DS-06 | Fonts | Proposed | TBD |
| DS-07 | Status color mappings | Open — blocks status pill and plan status work | TBD |
| DS-08 | Trends chart | Deferred | TBD |
| DS-09 | First release scope | Proposed | TBD |
| DS-10 | Tailwind | Proposed | TBD |

## Decision details

### DS-01 — Primary color source

**Question:** Should web CaregiverColors.Primary follow the selected palette or mirror Android's current teal fallback?

**Decision:** Web follows the selected palette everywhere. Do not mirror Android's teal fallback in CaregiverColors.

**Rationale:** The Android discrepancy appears to be an implementation inconsistency rather than intentional product behavior. Web components should not reproduce that inconsistency.

**Status:** Proposed

**Owner:** TBD

### DS-02 — Foreground color on light accents

**Question:** Should light accent palettes use a dark on-primary foreground, or should accent colors be darkened to retain white labels?

**Decision:** Compute an accessible foreground color for each palette instead of always using white. Every supported palette and effective theme-mode pair must pass WCAG AA.

**Rationale:** The extracted contrast results show that several light accent colors do not provide sufficient contrast with white text. The foreground must be selected based on the actual palette and mode rather than a universal white value.

**Status:** Proposed

**Owner:** TBD

### DS-03 — Metrolist

**Question:** Should Metrolist become a visible option in the web theme selector?

**Decision:** Do not include Metrolist in the web theme selector for now.

**Rationale:** Android defines Metrolist as a separate Material 3 scheme rather than one of its visible accent swatches. It is not part of the initial web selector scope.

**Status:** Proposed

**Owner:** TBD

### DS-04 — Dynamic accent naming

**Question:** Is Android's Dynamic accent derived from the operating system or wallpaper, or is it a fixed palette?

**Decision:** Android's Dynamic accent is a fixed coral color, not OS-derived. On web, either rename it (for example, Coral) or omit it. The web UI must not imply wallpaper or operating-system color adaptation.

**Rationale:** The existing name can imply dynamic system color behavior that the Android implementation does not provide.

**Status:** Proposed

**Owner:** TBD

### DS-05 — Pure-black mode

**Question:** Should pure-black mode change every surface consistently?

**Decision:** Defer pure-black mode.

**Rationale:** Android currently changes only Material 3 background and surface values to black while custom CaregiverColors remain charcoal. Reproducing this partial behavior would preserve an inconsistent surface system.

**Status:** Deferred

**Owner:** TBD

### DS-06 — Fonts

**Question:** Should web use IBM Plex Sans Arabic for both English and Arabic?

**Decision:** Use IBM Plex Sans Arabic for both English and Arabic to match Android. Replace the web use of DM Sans and Noto Sans Arabic.

**Rationale:** Android uses one IBM Plex Sans Arabic family for both Latin and Arabic glyphs. A shared family provides closer cross-platform typography parity.

**Follow-up:** Verify the font license and confirm the self-hosting approach before distributing font assets. This check is not represented as completed by this decision.

**Status:** Proposed

**Owner:** TBD

### DS-07 — Status color mappings

**Question:** Are the current status mappings approved, including the Android behavior that falls back to green for unknown plan statuses?

**Decision:** Do not approve the current mapping. Android currently falls back to green for unknown plan statuses, but unknown values must not be treated as a negative or safe value. The mobile developer and a clinical reviewer must review the status semantics and agree on the mapping before status-pill and plan-status work proceeds. Do not implement or document the current green fallback as an approved success state.

**Rationale:** A green fallback can communicate success or safety when the value is actually unknown. Status semantics require product and clinical review; the extracted behavior alone is not approval.

**Status:** Open — blocks status pill and plan status work

**Owner:** TBD

### DS-08 — Trends chart

**Question:** What chart visualization is intended for Trends?

**Decision:** Trends chart work is out of scope. Android currently has only a placeholder, so no chart tokens or visualization rules will be defined or inferred.

**Rationale:** There is no implemented Android chart from which to extract a parity specification.

**Status:** Deferred

**Owner:** TBD

### DS-09 — First release scope

**Question:** Should the first web parity release include every Android accent choice and pure-black mode?

**Decision:** The first release includes System, Light and Dark modes plus a small initial set of palettes. The full set of 20 Android accent swatches follows later.

**Rationale:** A smaller initial palette set limits first-release scope while preserving the three theme modes. Pure-black is separately deferred under DS-05.

**Implementation detail still to specify:** The names of the initial palettes have not been selected in this decision record and must not be guessed.

**Status:** Proposed

**Owner:** TBD

### DS-10 — Tailwind

**Question:** Is Tailwind planned for the web application?

**Decision:** Tailwind is not adopted. The web package does not declare it. tokens.css is the source of truth for web design tokens; tailwind.theme.ts is retained as optional and unused.

**Rationale:** The inspected web package uses CSS and does not declare Tailwind. Maintaining a parallel active token source would create unnecessary duplication.

**Status:** Proposed

**Owner:** TBD

## How to change a decision

Propose a change in a pull request that updates the relevant DS entry, explains the reason and impact, and updates any linked parity or work-breakdown references. Do not silently change an implementation direction or overwrite the recorded status. Keep the ID stable so related documentation and work items remain traceable.

## Blocking items

- **DS-07:** Status color mappings are not approved. Status-pill and plan-status implementation must remain blocked until the mobile developer and clinical reviewer complete their review.
