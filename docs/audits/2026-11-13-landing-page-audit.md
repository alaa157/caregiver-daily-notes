# Landing page audit — 2026-11-13

**Scope:** the public landing page at `/` (`web/src/routes/LandingRoute.tsx`, added in PR #246, commit `0df588e`), plus the shell, metadata, styles, and e2e coverage that affect it.

**Method:** static code review + live rendering. The app was built and served locally (`vite dev` / `vite preview` + production `vite build`), then exercised with headless Chromium via Playwright across EN/AR × light/dark × 1440/820/390/320 px viewports. axe-core 4.x was injected for WCAG scans, WCAG contrast ratios were computed from rendered computed styles, layout boxes were measured for overflow/overlap, and the repo's own Playwright suite (`e2e/foundation.spec.ts`) was executed against the production build.

**Result summary:** the new landing page is a large improvement over the previous one — real content sections, safety disclosures, reviewer info, APK download, OG tags, and a favicon. However, **the e2e suite is currently red on `main`** (6 failures in `foundation.spec.ts`), there is one confirmed WCAG 2.1 AA violation (label-in-name on the language toggle), the mobile nav menu has interaction defects, and roughly half of `styles/public.css` is dead CSS left over from the removed `HomePage`.

---

## 1. Defects

### 1.1 E2E suite fails on `main` — `e2e/foundation.spec.ts` is stale (severity: high)

`npm run test:e2e` (which builds with `--mode e2e` and runs `e2e/**/*.spec.ts` per `playwright.config.ts`) fails 6 tests on `desktop` alone; the same specs run under tablet/mobile/compact-mobile projects, so the real failure count is higher.

Verified by running:

```
npm run build -- --mode e2e
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium \
  npx playwright test e2e/foundation.spec.ts --project=desktop
# → 6 failed, 6 passed
```

Failures, all in `web/e2e/foundation.spec.ts`:

| Test | Fails because |
|---|---|
| `landing page is accessible and responsive in both directions` (en + ar) | Line 48 expects `getByRole('navigation').locator('[aria-current="page"]')` with `href="/"`. The new landing `<nav>` contains only in-page anchors (`#how-it-works`, `#safety`, `#languages`, `#reviewers`), the language toggle, and the CTA — nothing carries `aria-current`. Element not found. |
| `disables reveal animation and smooth scrolling for reduced motion` (en + ar) | Lines 88–90 select `.hero-copy, .care-illustration` and expect `animation-name` `['none','none']`. Neither class exists on the landing page anymore (`.care-illustration` survives only on `/login`), so `evaluateAll` returns `[]`. |
| `keeps normal motion when reduced motion is not requested` (en + ar) | Same selectors, lines 127–129, expects `['reveal','reveal']`, gets `[]`. |

Because these assertions fail before `checkAccessibility()` runs, the axe violation in §1.2 is currently masked — fixing the stale selectors will surface it.

**Fix direction:** rewrite the three stale tests against the new landing DOM (e.g. assert `aria-current` on the brand link or drop the assertion; retarget the reduced-motion checks at `.landing-hero`/`.landing-page` or drop them if the new page has no reveal animations — `landing.css` only disables animations, it defines none).

### 1.2 WCAG 2.1 AA violation — `label-content-name-mismatch` on the language toggle (severity: high, a11y)

axe-core flags a **serious** violation in every EN and AR run:

```
button[aria-label="التبديل إلى العربية"]   (visible text: "AR")
button[aria-label="Switch to English"]     (visible text: "EN", in AR mode)
```

In `web/src/routes/LandingRoute.tsx` line 41 the header nav toggle is:

```tsx
<button className="language-toggle"
  aria-label={language === 'ar' ? 'Switch to English' : 'التبديل إلى العربية'}
  onClick={...}>{language === 'ar' ? 'EN' : 'AR'}</button>
```

The `aria-label` does not contain the visible label text ("AR"/"EN"), violating WCAG 2.5.3 Label in Name — voice-control users who say "click AR" will not activate it. The footer toggle (line 50) has **no** `aria-label` at all and is compliant only by accident (its accessible name *is* its visible text).

**Fix direction:** drop the `aria-label` (visible "AR"/"EN" is already a sufficient name and matches the footer toggle), or make the label contain the visible text, e.g. `aria-label={... + ' (AR)'}`.

### 1.3 Mobile nav menu: Escape does not close it; inner controls don't close it (severity: medium)

`LandingRoute.tsx` line 37–41: `menuOpen` state toggles `.is-open` on `#landing-nav`. Verified at 390 px:

- Pressing **Escape** leaves `class="is-open"` — no keydown handler exists.
- The four anchor links call `setMenuOpen(false)` on click, but the **language toggle and the primary CTA `<Link>` inside the same `<nav>` do not**. Tapping "AR" inside the open menu switches language and leaves the menu open (it only closes incidentally when the CTA navigates away and unmounts the route).
- No outside-click dismissal and no scroll-lock; the absolutely-positioned dropdown (`top: 100%` of the sticky header) stays open while the page scrolls.

For a disclosure-style nav this is survivable, but Escape-to-close is the expected keyboard contract, and the inconsistent close behavior between sibling controls is a defect.

### 1.4 Public landing page is still gated on `config.json` (severity: medium)

`web/src/main.tsx` is unchanged in this respect: `loadRuntimeConfig()` must succeed before `<App/>` renders at all. `config.json` is fetched with `cache: 'no-store'` on **every** page load. If the file 404s, contains malformed JSON, or has a non-HTTPS `apiBaseUrl`, every visitor — including people who only want the marketing page — gets `ConfigError` instead of the landing page. The landing route needs no API configuration whatsoever.

The e2e spec `foundation.spec.ts:95–121` ("keeps loading feedback text visible…") actually encodes this dependency by holding `config.json` and asserting a loading state on `/`, so the coupling is known but still wrong for a public page. Consider rendering the landing route without waiting for runtime config (e.g. move `ApiProvider`/`AuthProvider` inside the authed subtree, or let `/` render optimistically and hydrate config lazily).

### 1.5 Language flash for returning Arabic visitors (severity: low–medium)

`web/src/i18n/I18nProvider.tsx` lines 30–34 sets `document.documentElement.lang`/`dir` in a `useEffect`. `index.html` hardcodes `<html lang="en" dir="ltr">`. A stored `ar` preference therefore paints one frame of LTR/English layout, then flips to RTL — visible flash plus layout shift on every visit. (`ThemeProvider` already does this correctly with `useLayoutEffect`.) Fix: set `lang`/`dir` in `useLayoutEffect`, or inline a tiny pre-paint script in `index.html` that reads `localStorage['caregiver-language']`.

### 1.6 `og:image` is an SVG — unsupported by most crawlers (severity: medium, SEO/sharing)

`index.html` declares `<meta property="og:image" content="/caregiver-notes-preview.svg" />` and `twitter:card=summary_large_image`. Facebook, X/Twitter, LinkedIn, iMessage, WhatsApp, and most chat apps do not render SVG OG images — link previews will show no image or a broken card. Ship a 1200×630 PNG (and ideally `og:image:width`/`og:image:height`/`og:url`/`og:locale`).

Also missing: `rel="canonical"`, `og:locale`/`og:locale:alternate`, and `twitter:image`. `theme-color` is still the static light value `#fafaf6` — mobile browsers show a light toolbar in dark mode.

### 1.7 Dead code: `FeatureCard` component + ~half of `public.css` (severity: low, maintainability)

PR #246 removed `HomePage` (which lived inline in `App.tsx` on the previous branch) but left behind:

- `web/src/components/FeatureCard.tsx` — **zero imports** anywhere in `src/` (verified by grep). Dead component.
- `web/src/styles/public.css` — still contains the full old landing stylesheet: `.hero`, `.hero-copy`, `.hero-eyebrow`, `.hero-description`, `.hero-actions`, `.hero-details`, `.disclaimer`, `.foundation-section`, `.section-heading`, `.section-description`, `.features-grid`, `.feature-card*`, `.feature-icon`, `.feature-status*`, `.foundation-notice`, `.home-invitation`, `.invitation-icon`, plus their media queries and `:lang(ar)` overrides — roughly 100 of 165 lines render nothing on any current route. (`.care-illustration`/`.journal-*`/`.illustration-*` must stay — `CareIllustration` is still used by `LoginPage`'s aside.)
- `.visually-hidden` in `features/landing/landing.css` duplicates `.app-visually-hidden` in `components/AppShell.css` — two utilities for the same thing.

Risk: future edits to "landing styles" will hit dead rules and produce no visible change.

### 1.8 Minor defects and smells

- **`AuthStatus` includes `'checking'`** (`features/auth/AuthProvider.tsx:5`) but the provider only ever returns `'anonymous' | 'authenticated'` — dead union member.
- **`LandingRoute` calls `useI18n()` twice** (lines 33 and 36) — harmless but sloppy; merge into one destructure.
- **Nav labels and hrefs are parallel arrays** (`c.nav.map` zipped with a hardcoded `['#how-it-works', …]` literal, line 41) — adding a nav item requires editing two places in sync; a `{label, href}` tuple in `copy` would be safer.
- **`document.title` on `/` is "Home · Caregiver Daily Notes"** (set by `AppShell`'s pathname table, `components/AppShell.tsx`) — generic for a marketing page; consider a dedicated landing title.
- **APK link is hardcoded to `releases/download/v0.1.1/app-minified.apk`** (`LandingRoute.tsx:25`) — it will silently go stale on the next release; no version/date/file-size is shown to the user before a ~large binary download starts. (Verified the URL currently returns 200.)
- **Hero eyebrow repeats the brand name** ("Caregiver Daily Notes") directly under the brand link in the header — redundant.
- **`landing.css` is a single minified line** (~5 KB) while every other stylesheet in the repo is formatted multi-line CSS — inconsistent with project conventions and hostile to review/diff.
- **Hidden duplicate chrome:** `AppShell` still renders the full `site-header` (with its own nav, theme select, language toggle, logout) and `site-footer` for `/`; `landing.css` hides them via `.site-shell:has(.landing-page) > .site-header { display:none }`. Works, but it's dead DOM relying on `:has()` — a `bare`/`chrome="none"` prop on `AppShell` would be more robust (e.g. `:has()` support is fine in 2026 browsers, but the pattern is fragile if classes change).

---

## 2. Verified non-issues (checked, OK)

- **No horizontal overflow** at 1440/1024/820/768/640/480/390/360/320 px in EN or AR — `scrollWidth === clientWidth` everywhere.
- **No console errors or page errors** on any tested viewport/language/theme combo (excluding a font 403 that was an artifact of my symlinked `node_modules`, not the repo).
- **Contrast:** all measured text/background pairs pass WCAG AA — body text ~5.1:1 light / ~8.8–9.6:1 dark; the only axe violation is §1.2. (An earlier apparent `.landing-safety li` failure was a measurement artifact of `color(srgb …)` parsing — verified actual colors are `rgb(31,42,46)` on a light tint, ~13:1.)
- **Dark mode** works correctly — the landing page uses `var(--caregiver-*)`/`var(--ui-tint)` tokens throughout and re-themes cleanly.
- **RTL** flips correctly (`dir="rtl"`, logical properties, `dir` prop on `MockScreen`).
- **Reduced motion** is honored (`landing.css` has a `prefers-reduced-motion` block; base `scroll-behavior` also disabled).
- **Landing renders without a backend** — auth is token-in-localStorage based; no `/api` call is needed for `/`. (Only `config.json` is required — see §1.4.)
- **APK download URL is live** (HTTP 200 from the GitHub release asset).
- **Skip link** still works on `/` (it's outside the hidden header/footer and targets `#main-content`).
- **`aria-expanded`/`aria-controls`** are correctly wired on the menu button; `nav` is truly `display:none` when closed (not just visually hidden).

---

## 3. Enhancement suggestions

Ordered roughly by impact:

1. **Fix the red e2e suite first** (§1.1) — `main` currently ships a failing accessibility gate; the stale specs also mask the real axe violation.
2. **Replace `MockScreen` CSS mockups with real screenshots.** `features/landing/README.md` already documents the intended path (Playwright captures of synthetic accounts at desktop/mobile in both languages, sanitized, localized alt text). Real UI beats a drawn approximation for a product whose pitch is "see the bigger picture" — and `aria-hidden` decorative mocks currently convey zero information to screen-reader users beyond the captions.
3. **Add a privacy/data-handling link.** The safety section makes strong claims ("structured fields excluded from AI requests", "only you can see your recipients, enforced by the server") — `docs/privacy/` exists in the repo; link it or summarize it on-page. For a health-adjacent app this is the strongest trust signal available.
4. **Fix social metadata** (§1.6): PNG OG image, canonical, og:url/locale, twitter:image, dynamic `theme-color` (or `media` variants for light/dark).
5. **Decouple `/` from `config.json`** (§1.4) so the marketing page is immune to API config problems and doesn't pay a render-blocking fetch.
6. **Menu polish** (§1.3): Escape to close, close on outside click, close on any control inside, consider `inert`/focus management, and reflect open state in the button's accessible name ("Menu, expanded").
7. **Content gaps:** no FAQ, no "who is this for" (family caregivers vs. professional), no demo video/GIF, no changelog/what's-new, no contact/support channel, no link to the Android APK's version/date/size or its privacy policy. The "For reviewers" section is great for graders but reads like internal documentation on a public page — consider splitting reviewer info behind a details/summary or a dedicated `/about` or `/reviewers` route.
8. **Trim dead CSS/component** (§1.7) and normalize `landing.css` formatting to match repo conventions.
9. **Consider a real `<nav>` landmark structure**: the landing `<nav>` mixes section anchors, a language toggle button, and a CTA link. Splitting into "section links" + "utilities" groups (or `<nav aria-label>` for each) improves screen-reader navigation.
10. **i18n of `document.title`/meta**: title is set post-hydration in the UI language already via `AppShell`, but `index.html`'s static title/description are English-only — acceptable for SPA, worth noting if SSR/prerender is ever added.

---

## Appendix — reproduction notes

- Dev server: `cd web && npm run dev -- --port 5299` (vite 5.4.21).
- Production check: `npm run build -- --mode e2e && PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npx playwright test e2e/foundation.spec.ts`.
- axe was run with default ruleset (all tags) on `/` in EN/AR × light/dark; only `label-content-name-mismatch` (serious) was reported, on the header language toggle.
- All findings verified against `origin/main` @ `0df588e` ("feat(web): add bilingual public landing page (#246)").
