# Rafeek Caregiver Guide — دليل مقدّم الرعاية

A complete, bilingual walkthrough of Rafeek (رفيق) for family caregivers,
professional caregivers and care teams — every workflow on the web app and the
Android app, illustrated with screenshots captured from the running apps.

| Language | Guide |
|---|---|
| **English** | [caregiver-guide.en.md](caregiver-guide.en.md) |
| **العربية** (RTL) | [caregiver-guide.ar.md](caregiver-guide.ar.md) |

Both versions cover the same ground, in the app's own terminology (labels are
taken verbatim from `web/src/i18n/` and the Android `strings.xml` resources):

1. What Rafeek does — and what it deliberately is not
2. Web vs Android: accounts, storage, AI providers, key handling
3. Getting started (sign-up, local accounts, offline demo)
4. Home, navigation, language and theme
5. Care recipients — details, care details, archive/restore
6. Medications — records, intake events, quick dose checks, conflict warnings
7. Recording a daily note — fields, observation states, verification, safe retry
8. Reading a saved note — the locked original and effective values
9. Corrections — addenda and structured correction events
10. Notes history — filters and pagination
11. AI summaries — deterministic safety signals, evidence, uncertainty, trends
12. Care plans — request, accept / edit and accept / dismiss, history, drafts
13. BYOK AI setup — providers, models, connection tests, data-use notices
14. Safety and privacy essentials
15. Troubleshooting
16. Glossary (English ↔ Arabic)
17. Related documentation

## Screenshot conventions

- All screenshots live in [`docs/screenshots/`](../screenshots/) and were
  captured from the running apps with **synthetic data** (web against a local
  Spring Boot + PostgreSQL backend; Android from the offline demo). The people
  shown are not real.
- Paths in the guides are relative to this folder (`../screenshots/…`), so the
  guides render correctly on GitHub and in any Markdown viewer.
- `web/en-10-note-edit.png` captures a *page-not-found* error state; it is
  intentionally **not** referenced by the guides.

### Screenshot map

| Workflow | Web (EN) | Web (AR) | Android |
|---|---|---|---|
| Sign in / sign up | `en-01-login`, `en-02-signup` | — | — |
| Overview / home | `en-03-home` | `ar-01-home` | `12-home-medication-dose`, `39-home-ar`, `10-needs-attention`, `17-home-attention` |
| Recipients | `en-04-recipients`, `en-05-recipient-new`, `en-06-recipient-laila`, `en-07-recipient-john` | `ar-04-recipient-laila` | `16-people-list`, `add-person-laila`, `add-person-john`, `03-recipient-laila-empty`, `13-care-details-form`, `14-recipient-care-details`, `41-recipient-ar` |
| Note editor | `en-12-note-new` | `ar-07-note-new` | `04-note-editor-laila-top`, `04-note-editor-laila-filled`, `09-note-editor-john-top`, `09-note-editor-john-filled`, `05-note-saved` |
| Note detail & corrections | `en-09-note-fall`, `en-11-addendum-new` | `ar-03-note-fall` | `06-note-detail`, `07-correction-form`, `08-note-with-correction` |
| Medications | recipient page (see `en-06`) | — | `11-add-medication`, `11-add-medication-filled`, `12-home-medication-dose` |
| History | `en-08-history` | `ar-02-history` | `15-history`, `40-history-ar` |
| AI summary | `en-14-summary`, `en-16-recipient-summaries` | `ar-08-summary`, `ar-05-recipient-summaries` | — |
| Care plans | `en-13-plans`, `en-17-recipient-plans` | — | — |
| Settings & AI setup | `en-15-settings` | `ar-06-settings` | `18-account-menu`, `19-settings`, `20-ai-settings`, `38-settings-ar` |
| Mobile web | `mobile-01-home`, `mobile-02-note-fall`, `mobile-03-history`, `mobile-04-recipients` | — | — |

## Maintaining this guide

- UI labels come from the i18n catalogs — when strings change, update both
  language versions together (the project enforces EN/AR parity by tests).
- When screens change, recapture into `docs/screenshots/` keeping the naming
  scheme, then update the map above and the affected figures.
- Keep the safety statements in sync with `docs/privacy/` and the backend
  safety rules; they are the product's core promise, not boilerplate.

## Related

- [Project README](../../README.md) · [Privacy](../privacy/README.md) ·
  [API contracts](../api/) ·
  [Test strategy](../testing/README.md) · [Design style](../design-system/DESIGN-STYLE.md)

*Rafeek · DEPI Capstone 2026 · Care-support information only — not medical advice.*