# Screen Specification

Every visible string must come from `copy.en.json` or `copy.ar.json`. Every color, size, and spacing must come from `tokens.json`. The screens below are shared across web and Android, with RTL/LTR parity.

## 1. Sign in
- Components: app name display text, two TextFields, PrimaryButton, inline ErrorState.
- Content: app name placeholder, email, password, sign-in action.
- Behavior: submitting changes the button label/state; do not show a screen-level spinner. On success, navigate to Home.
- States: idle, submitting, error, success (go to Home). Empty/loading states are not separately meaningful for this form.

## 2. Home
- Components: AppBar with greeting, Card with today’s status, PrimaryButton (`home.addNote`), SecondaryButton (`home.summaryCta`), BottomNav, recent TimelineItems where present, AlertSafety when a latest safety alert exists.
- Content: greeting, today’s status for the selected recipient, recent notes, latest safety alert if any.
- Behavior: add-note opens Daily note editor; summary CTA opens Summary. Safety alert content is displayed as provided by the backend.
- States: loading, empty (no notes/status yet), error, success.

## 3. Care recipients
- Components: AppBar, one Card per recipient, initials Avatar, name, last note date, FAB (`people.add`), BottomNav, EmptyState.
- Content: care-recipient list.
- Behavior: selecting a card opens Recipient detail; FAB opens the add-recipient flow only if that flow is already defined by the product. Do not invent additional screens in this specification.
- States: loading, empty (`people.empty`), error, success.

## 4. Recipient detail
- Components: AppBar with back, profile Card, PrimaryButton for new note, list of recent notes as TimelineItems.
- Content: recipient profile details and recent notes.
- Behavior: back returns to the prior screen; new note opens Daily note editor for this recipient; tapping a note opens Note detail.
- States: loading, empty (no recent notes), error, success.

## 5. Daily note editor
- Components: AppBar (`note.new.title`); ChoiceRow for mood, appetite, sleep, mobility, and medication taken; PainScale; falls toggle; MultilineField (`note.freeText.label`); footer with PrimaryButton (`note.save`); AlertSafety preview before save when falls or high pain triggers the defined preview.
- Content: structured care observations and free text.
- Behavior: saving works even if summary generation fails. Save the original note as immutable; subsequent corrections are separate appended entries. Display safety previews without letting AI determine safety; backend-provided red flags must be shown exactly.
- States: idle, saving, saved (`note.saved` toast), error (preserve entered text). The screen's data-loading behavior, if any, uses the shared loading state.

## 6. Note detail
- Components: AppBar, Card with structured fields, original free text, list of addenda, SecondaryButton (`note.addendum`) opening a correction form, correction hint (`note.addendum.hint`).
- Content: original saved note and appended corrections.
- Behavior: no edit action exists for the original note. A correction creates a new entry and does not replace or alter the original.
- States: loading, empty only when there is no note data to display, error, success.

## 7. History
- Components: AppBar, filter row (date range, recipient), TimelineItems grouped by day, BottomNav.
- Content: notes filtered by date range and recipient.
- Behavior: filters update the timeline while preserving RTL/LTR layout.
- States: loading, empty, error, success.

## 8. Summary
- Components: AppBar, segmented control for 7/14/30 days, PrimaryButton (`summary.generate`), result Card, AlertSafety when applicable, AlertInfo (`plan.disclaimer`).
- Content: selected period and generated summary, with safety alert content exactly as supplied by backend.
- Behavior: when generation is unavailable, notes remain accessible and recordable. Do not let AI infer or decide safety.
- States: idle, generating (Skeleton; button label changes), unavailable (`summary.unavailable`, note list still accessible), success; errors use the shared error copy/state where applicable.

## 9. Plan proposal
- Components: AppBar, a Card per proposed action, PrimaryButton (`plan.accept`), SecondaryButton (`plan.edit`), SecondaryButton (`plan.dismiss`), AlertInfo (`plan.disclaimer`).
- Content: proposed care-support actions.
- Behavior: medication items are read-only and cannot be edited through the plan suggestion UI. The AI does not decide safety; show backend red flags exactly as supplied.
- States: loading, empty, error, success.

## 10. Plan history
- Components: AppBar, Timeline of versions (v1, v2, ...), Pill per version status.
- Content: historical plan versions and their statuses.
- Behavior: show versions in chronological order with clear status labels.
- States: loading, empty, error, success.

## 11. Saved
- Components: AppBar, list of saved notes or summaries, BottomNav, TimelineItems where applicable.
- Content: saved notes and summaries.
- Behavior: selecting a saved note opens Note detail; saved notes remain immutable.
- States: loading, empty, error, success.

## 12. Settings
- Components: AppBar, list rows for language and account, DangerOutlineButton for sign out.
- Content: language, account, sign-out action.
- Behavior: changing language applies RTL or LTR immediately on both platforms. Keep all screen content and component behavior in parity.
- States: success; language/account data loading or failure uses the shared loading and error states where applicable.

## Intentional differences
None yet.
