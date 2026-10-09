# Caregiver Daily Notes — Shared Design Specification

## 1. Purpose
This specification defines the shared visual system, bilingual copy, reusable components, and screen behavior for Caregiver Daily Notes on Android and web. It is intended for family caregivers supporting elderly parents at home and hospital caregivers, with a calm, warm, accessible interface that supports quick note-taking in English and Modern Standard Arabic.

## 2. Files
- `tokens.json` — the single source of truth for colors, typography, spacing, radii, elevation, component dimensions, motion, and hard rules.
- `copy.en.json` — English UI strings keyed for use across both platforms.
- `copy.ar.json` — Modern Standard Arabic UI strings with matching keys and order for RTL support.
- `components.md` — shared component specifications, including dimensions, token colors, radii, and states.
- `screens.md` — the defined screens, their components, content, behavior, and states.

## 3. Principles
1. Caregivers are tired and under time pressure, so recording a note must take the fewest practical taps.
2. The interface is calm, not clinical, using warm cream and sage.
3. Arabic is first-class: every screen works in RTL and LTR with the same content.
4. Notes are append-only. A saved note is never edited; users can only add a correction.
5. The AI never decides safety. Red flags are displayed exactly as supplied by the backend.
6. No people appear in the UI: no people, faces, elderly figures, people illustrations, medical crosses, or hospital imagery.

## 4. Parity rule
Web and Android implement the same screens, components, copy keys, and states. Any difference must be listed under “Intentional differences” in `screens.md`.

## 5. Acceptance checks
- Every color, size, and spacing value comes from `tokens.json`.
- Every visible string comes from copy keys; do not hard-code user-facing text in screen implementations.
- Every screen has loading, empty, error, and success states where applicable, as defined in `screens.md`.
- Every interactive element is at least 48 px on web and 48 dp on Android.
- Arabic renders correctly in RTL with no clipped text at 1.3× font scale.
- Saved notes remain immutable; corrections are separate appended entries.
- Safety red flags are rendered exactly as the backend provides them; the AI does not infer, rewrite, suppress, or decide safety.
- Medication items in plan suggestions are read-only.
- No gradients, neon colors, people, faces, medical crosses, or hospital imagery are used.
- The owner-supplied app name placeholder is used until the owner supplies the name.
