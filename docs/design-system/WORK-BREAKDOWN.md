# Design System Parity — Work Breakdown

## Epic: Design system parity

**Goal:** Bring the web application's visual system, theme behavior, shared components and principal screen patterns into documented parity with the Android design system, subject to the product decisions in [DECISIONS.md](DECISIONS.md).

**Outcome:** A web UI that uses the selected palette consistently, supports System/Light/Dark modes, provides accessible contrast, and follows the documented Android layout, typography, interaction, RTL and motion patterns.

**Boundary:** This is an implementation work breakdown, not authorization to change Android behavior or invent unextracted design values. Trends chart design remains out of scope under [DS-08](DECISIONS.md#ds-08). Status-pill and plan-status semantics remain blocked under [DS-07](DECISIONS.md#ds-07).

## Sequencing and prerequisite gate

1. **DSP-01 through DSP-03 come first.** Establish the token/font foundation, implement theme resolution, and make contrast behavior measurable before building the shared component layer.
2. **Shallow-audit caveat:** The original web inspection was shallow and covered package metadata and base CSS/theme entry points; it was not a feature-by-feature audit of the web application.
3. **Mandatory audit gate before DSP-04:** Before implementing DSP-04, someone must inspect the existing web CSS and components in scope. Record which components and styles already exist, what can be reused, and where existing behavior conflicts with the parity requirements. This audit is a prerequisite, not an assumption that the current web UI lacks those components.
4. **DS-07 gate:** The shared component structure may be prepared, but status-pill mappings and plan-status behavior must not be finalized or marked complete until DS-07 is reviewed and closed.

## Child tasks

### DSP-01 — Foundation

**Goal:** Establish the web design-token foundation and shared typography, spacing and shape primitives.

**Scope:**
- Import tokens.css into the web application's global styling entry point.
- Use IBM Plex Sans Arabic for both English and Arabic, following DS-06; remove DM Sans and Noto Sans Arabic from the active web font stack.
- Verify the font license and document the self-hosting decision before shipping the font assets.
- Expose and consistently consume the existing spacing and radius tokens. Do not invent replacement values.

**Dependencies:** None. This is the first implementation task.

**Acceptance criteria:**
- The global web stylesheet loads tokens.css exactly once.
- A runtime style check confirms the documented semantic CSS variables are available on representative application screens.
- Computed font-family for both English and Arabic content resolves to IBM Plex Sans Arabic.
- DM Sans and Noto Sans Arabic are no longer selected by active application styles.
- Shared spacing and radius references resolve to the extracted token variables rather than duplicated component-specific values.
- The font license and self-hosting check is recorded before the font assets are released.

**Related parity references:** Table rows “Light neutrals”, “IBM Plex Sans Arabic supplies Latin and Arabic glyphs on Android”, “Spacing uses 4/8/16/24/32/48dp rhythm”, “Cards use 14dp radius”, and “Buttons use 12dp radius”.

**Related decisions:** [DS-06](DECISIONS.md#ds-06), [DS-10](DECISIONS.md#ds-10).

### DSP-02 — Theme engine

**Goal:** Implement deterministic System, Light and Dark theme resolution and a complete set of semantic dark-mode tokens.

**Scope:**
- Support System, Light and Dark modes.
- Persist an explicit user choice and resolve System from the operating-system color preference.
- Apply the complete dark token set to application surfaces and components. The extracted tokens document Android dark values; the inspected web base currently has no equivalent dark theme block.
- Apply the selected palette consistently in line with DS-01.
- Keep pure-black mode deferred in line with DS-05.

**Dependencies:** DSP-01.

**Acceptance criteria:**
- System mode follows the operating-system light/dark preference.
- Selecting Light or Dark overrides the operating-system preference.
- An explicit selection survives a full page reload.
- Changing the operating-system preference updates the UI only while System is selected.
- Every semantic surface, text, border, alert, control and skeleton role used by the web UI has a defined value in both Light and Dark modes.
- No pure-black control is exposed in this release.

**Related parity references:** Table rows “Dark mode uses charcoal surfaces”, “System, Light and Dark modes are supported”, “Pure-black changes Material 3 background/surface only”, and “Twenty accent swatches exist”.

**Related decisions:** [DS-01](DECISIONS.md#ds-01), [DS-03](DECISIONS.md#ds-03), [DS-05](DECISIONS.md#ds-05), [DS-09](DECISIONS.md#ds-09).

### DSP-03 — Accessibility and palette foregrounds

**Goal:** Ensure readable contrast for every palette and effective theme mode, using a computed foreground color per palette.

**Scope:**
- Add automated contrast checks for each palette supported by the web selector in Light and Dark modes.
- Compute the foreground used on each palette's accent surface rather than assuming white.
- Include text and applicable non-text UI pairs in the contrast review.
- Keep the accessible result tied to the palette and effective mode.

**Dependencies:** DSP-01 and DSP-02.

**Acceptance criteria:**
- An automated contrast check runs for every shipped palette in Light and Dark modes.
- Every normal-text/background pair passes WCAG AA at 4.5:1 or greater.
- Large-text and applicable non-text UI pairs meet the relevant WCAG AA 3:1 minimum.
- The foreground for each accent is computed or selected from contrast measurement; white is not applied as a universal foreground.
- A failing palette/mode pair fails the check and cannot be marked release-ready.
- The contrast results identify the palette, mode, foreground, background and measured ratio.

**Related parity references:** Table row “Standard accent schemes use white onPrimary/onPrimaryContainer, even for light accents” and the Accessibility contrast discussion in DESIGN-STYLE.md.

**Related decisions:** [DS-02](DECISIONS.md#ds-02).

### DSP-04 — Core components

**Goal:** Build or adapt the reusable component layer after auditing existing web CSS and components.

**Scope:**
- Buttons and inputs, including focused, disabled and validation states.
- Cards and status pills.
- Safety and information alerts.
- Error and loading states, including recovery actions.
- Skeletons that match the geometry of the content they replace.
- Keep status-pill and plan-status semantics gated by DS-07.

**Dependencies:** DSP-01, DSP-02 and DSP-03, plus completion of the mandatory shallow-audit follow-up described above. The DS-07 dependency applies specifically to status-pill mappings and plan-status behavior; it does not prevent work on unrelated shared components.

**Acceptance criteria:**
- The prerequisite CSS/component audit is recorded before component implementation begins.
- Each listed component has a documented reusable implementation and a test or story that exercises its default and relevant interactive states.
- Buttons and inputs expose visible focus and disabled states.
- Input validation and error states include readable text, not color alone.
- Safety alerts include the warning icon and explanatory text; information alerts include an information cue and explanatory text.
- Loading skeletons match the dimensions and structure of their final content rows.
- Status-pill colors are not finalized and plan-status behavior is not marked complete while DS-07 remains open.

**Related parity references:** Table rows “Buttons use 12dp radius”, “Status pills use 999dp radius”, “Safety alert uses danger surface/border/text plus warning icon”, “Info alert uses blue-tinted surface”, “Reusable error/warning components”, “CSV/backup notices”, “Sync has synced/syncing/offline/failed icons”, and “Loading skeletons follow list row geometry”.

**Related decisions:** [DS-02](DECISIONS.md#ds-02), [DS-07](DECISIONS.md#ds-07).

### DSP-05 — App shell

**Goal:** Reproduce the four-tab application shell and its distinct tab-root and detail-header patterns.

**Scope:**
- Four-tab navigation: Home, People, Notes and History.
- Tab top bar with centered title, avatar and sync status.
- Detail header with a visible back affordance.
- Tab-level add action whose destination/action follows the active tab.
- Keep nested destinations, such as plans, separate from the four primary tabs.

**Dependencies:** DSP-01 through DSP-04 for the foundation and reusable components. The non-blocked component work from DSP-04 may be used while DS-07 remains open.

**Acceptance criteria:**
- The primary navigation displays exactly Home, People, Notes and History.
- Selecting each tab opens its corresponding tab root and preserves the active-tab indication.
- Tab-root screens display the tab top bar and sync status.
- Detail screens display a back affordance that returns to the correct parent destination.
- The add action is available at tab level and invokes the correct action for the active tab.
- Plans and other nested destinations do not appear as additional primary tabs.
- Navigation controls have accessible names and keyboard-operable focus states.

**Related parity references:** Table rows “Main navigation has Home, People, Notes and History tabs”, “Bottom navigation is a 64dp strip”, “Tab top bar has centered title, avatar and sync status”, and “Tab routes show a 56dp FAB”.

**Related decisions:** DS-01 applies to the selected palette used by shell controls; no separate navigation decision is recorded.

### DSP-06 — Note timeline row

**Goal:** Implement a reusable full-width note timeline row that preserves the Android information hierarchy and expansion behavior.

**Scope:**
- Full-width row layout rather than a card-per-note list.
- Avatar, author, time, note body, metadata pills/actions and divider.
- Collapse note body after four lines and provide a Show more action.
- Preserve pending-sync icon and accessible description where applicable.

**Dependencies:** DSP-04 non-blocked shared components and DSP-05 app shell.

**Acceptance criteria:**
- Timeline entries render as full-width rows separated by dividers, not individually elevated cards.
- Each row displays the avatar, author, time and note body in the documented order.
- Bodies longer than four lines are collapsed to four lines and expose a working Show more control.
- Short bodies do not display a redundant Show more control.
- Pending-sync state has both a visible icon and an accessible description.
- Row content remains readable without horizontal overflow at supported viewport widths.
- Any semantic status-pill treatment remains subject to DS-07 and is not assigned an unapproved safe/success meaning.

**Related parity references:** Table rows “Note timeline uses full-width rows”, “Note body collapses after four lines with Show more”, and “Sync has synced/syncing/offline/failed icons”.

**Related decisions:** [DS-07](DECISIONS.md#ds-07) governs status semantics if a row pill represents a status; it does not approve a mapping while open.

### DSP-07 — Note editor

**Goal:** Recreate the note-entry form structure and persistent save action.

**Scope:**
- Group related form fields into clear sections.
- Allow selectable chips to wrap naturally onto multiple lines.
- Preserve the pain-level control geometry and visible selection state.
- Keep the save action in a persistent bottom bar.
- Provide visible and announced field validation.

**Dependencies:** DSP-04 non-blocked shared components and DSP-05 app shell.

**Acceptance criteria:**
- Related fields appear under their corresponding group headings.
- Chips wrap onto additional lines at narrow widths without clipping or horizontal scrolling.
- Chip selection can be changed with pointer and keyboard input and is conveyed beyond color alone.
- The pain-level control exposes its selected value in text or an accessible name.
- The save bar remains visible while the form scrolls and does not obscure the final field or validation message.
- Invalid fields show a written explanation associated with the relevant input.
- Saving exposes a perceivable success or failure result.

**Related parity references:** Table row “Note editor uses grouped fields, wrapping chips, 48dp pain controls and persistent bottom save”; also “Buttons use 12dp radius” and “Reusable error/warning components”.

**Related decisions:** DS-02 applies to component foreground contrast; DS-07 applies only if a future editor element displays a semantic plan/status value.

### DSP-08 — Summary screen

**Goal:** Preserve the summary's reading-first hierarchy and distinguish disclaimers, evidence and safety information.

**Scope:**
- Separate disclaimer, evidence and safety groups.
- Preserve evidence content as evidence rather than decorative status.
- Render the safety group with the warning icon and a written explanation.
- Keep follow-up actions accessible and visually distinct.

**Dependencies:** DSP-04 non-blocked alert/card components and DSP-05 app shell.

**Acceptance criteria:**
- Disclaimer, evidence and safety content appear in separate, visibly labelled groups.
- Evidence items retain their associated explanatory content.
- The safety group displays a warning icon and written explanation together.
- The warning meaning is available to assistive technology and is not conveyed by red alone.
- The reading order is logical for keyboard and screen-reader users.
- Red styling is not described or presented as a validated clinical severity score.

**Related parity references:** Table rows “Safety alert uses danger surface/border/text plus warning icon”, “Summary is reading-first with disclaimers, evidence, safety flags and follow-up actions”, and “Info alert uses blue-tinted surface”.

**Related decisions:** DS-02 applies to contrast for the summary's chosen palette/mode; DS-07 must be consulted before representing any unresolved plan status.

### DSP-09 — Settings and theme screen

**Goal:** Provide grouped settings and a theme screen that previews the active mode and initial palette choices.

**Scope:**
- Group settings into the established section pattern.
- Provide a theme preview that reflects current selections.
- Provide System, Light and Dark mode controls.
- Expose only the small initial palette set chosen for the first release.
- Keep Metrolist and pure-black outside the initial selector.

**Dependencies:** DSP-01, DSP-02, DSP-03 and DSP-05.

**Acceptance criteria:**
- Settings are displayed in named groups with consistent spacing and surface treatment.
- The theme screen exposes System, Light and Dark controls.
- Selecting a mode updates the preview and application appearance.
- Selecting a palette updates the preview and the application accent consistently.
- The selector contains only the explicitly approved initial palette set; the complete 20-swatch Android set is not exposed in this release.
- The initial palette names are documented before implementation and are not inferred by the implementer.
- Metrolist and pure-black are not selectable in this release.

**Related parity references:** Table rows “System, Light and Dark modes are supported”, “Twenty accent swatches exist”, “Metrolist is a separate Material 3 scheme”, and “Settings uses grouped low-elevation surfaces”.

**Related decisions:** [DS-03](DECISIONS.md#ds-03), [DS-04](DECISIONS.md#ds-04), [DS-05](DECISIONS.md#ds-05), [DS-09](DECISIONS.md#ds-09).

### DSP-10 — RTL pass

**Goal:** Make the web parity implementation work correctly in Arabic RTL and English LTR, including mixed-script values.

**Scope:**
- Use logical CSS properties for layout and spacing.
- Isolate mixed-script names, identifiers, numbers and dates.
- Mirror only directional icons.
- Verify tab shell, forms, timeline rows, alerts and theme controls in both directions.

**Dependencies:** DSP-01 through DSP-09 for the surfaces and screens that need RTL verification.

**Acceptance criteria:**
- Layout styles use logical inline/block properties rather than hard-coded left/right positioning for directional layout.
- Mixed-script values remain in the intended order when embedded in Arabic text.
- Directional icons mirror in RTL and non-directional icons do not.
- Arabic RTL and English LTR checks pass for navigation, note rows, editor chips, alerts and settings.
- No tested screen has clipped text, overlapping controls or unintended horizontal scrolling solely due to direction changes.

**Related parity references:** Table row “RTL includes auto-mirrored icons, logical positions and bidi isolation”.

**Related decisions:** DS-06 establishes the shared font direction; there is no separate RTL decision beyond the requirements already documented here.

### DSP-11 — Motion

**Goal:** Apply the documented transitions where they improve continuity and respect the user's reduced-motion preference.

**Scope:**
- Match relevant top-bar and route transitions to the Android reference.
- Avoid adding motion to controls or screens that do not need it.
- Honor the browser's prefers-reduced-motion setting.

**Dependencies:** DSP-04 through DSP-10, so motion is applied consistently across the implemented components and screens.

**Acceptance criteria:**
- Top-bar and route transitions use the documented Android timing pattern where the corresponding transition exists.
- With prefers-reduced-motion set to reduce, non-essential route, fade, scale and shimmer motion is removed or reduced.
- With the default motion preference, transitions complete without blocking input or navigation.
- Motion does not change the final layout, hide content, or communicate status solely through animation.
- A manual verification record covers at least one tab change, one detail navigation and one loading state under both motion preferences.

**Related parity references:** Table rows “Top-bar fades are 300/200ms; route transitions use 200ms slide/fade” and “Loading skeletons follow list row geometry”.

**Related decisions:** No DS entry defines a motion policy; this task implements the motion parity and accessibility requirements recorded in the guide.

## Constraints

- Status colors must never be the only cue. Include visible text or an icon as an additional cue.
- Red is not a validated clinical severity score and must not be presented as one.
- Missing or unknown values must never be displayed as a normal or safe state.
- Do not implement an unapproved status mapping. DS-07 remains a blocker for status-pill and plan-status work.

## Ready-to-copy GitHub issue body template

Use the following body when creating an implementation issue from one of the child tasks:

### Goal
[State the outcome this issue must deliver.]

### Scope
- [List the included UI, behavior and states.]
- [List the files or components expected to be touched after the repository audit.]

### Out of scope
- [List excluded behavior and explicitly deferred work.]

### Dependencies and blockers
- Epic: Design system parity
- Work item: [DSP-xx]
- Dependencies: [DSP-xx items]
- Decision references: [DS-xx entries]
- Blocking review: [State whether DS-07 or another decision blocks this work.]

### Related documentation
- Parity gaps: [Relevant table rows or sections in parity-gaps.md]
- Decisions: [Relevant DS entries in DECISIONS.md]
- Design guide: [Relevant section in DESIGN-STYLE.md]

### Acceptance criteria
- [ ] [Add a measurable behavior or visual criterion.]
- [ ] [Add an accessibility or responsive criterion.]
- [ ] [Add a test or verification criterion.]
- [ ] [Confirm that no unapproved token values or status meanings were introduced.]

### Verification evidence
[Describe the tests, screenshots, contrast report, or manual checks that demonstrate completion.]

### Constraints
- Status colors are not the only cue.
- Red is not a clinical severity score.
- Missing or unknown values are not shown as normal or safe.
