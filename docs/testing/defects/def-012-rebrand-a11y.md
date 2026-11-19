# DEF-012 — Rebrand palette and brand label fail WCAG AA on landing/auth

- **Severity:** Major (accessibility: 145 axe violations on the landing page, 28 on auth, across all four viewports)
- **Component:** `web/src/features/landing/landing.css`, `web/src/features/auth/auth.css`, `web/src/components/AppShell.tsx`
- **Environment:** Playwright + axe-core, CI run 44592625049 (first full browser run after re-enablement)
- **Discovered:** 2026-11-19

## Findings

1. `color-contrast` — the rebrand's muted sage `#68756b` measured **4.28:1** on the landing cream `#f5f1e8` (AA requires 4.5:1 for 10px text), and clay `#bd7057` measured **3.67:1** on the auth surface `#fffdf8`. Both tokens repeat across the landing/auth surfaces → 145/28 violating nodes.
2. `label-content-name-mismatch` (WCAG 2.5.3 Label in Name) — the `.brand` link's accessible name did not include its full visible text (brand + tagline).

## Fix

- `--muted` → `#626f65` (4.68:1 on cream, 5.19:1 on white).
- `--clay` → `#a05a3c` (≥4.5:1 on both surfaces; focus outline follows the token).
- Brand link gains `aria-label` composed of the localized brand + tagline, so the accessible name contains all visible text in both EN and AR.

## Verification

Browser suite re-run in CI (axe scans assert zero violations on every screen/viewport).

**Status: fixed (verification in flight).**
