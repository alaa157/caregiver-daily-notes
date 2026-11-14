# Landing Page Audit — `caregiver-daily-notes`

**Target:** `web/src/routes/LandingRoute.tsx` (route `/`), styles in `web/src/features/landing/landing.css`
**Branch audited:** `main` @ `0df588e` — *feat(web): add bilingual public landing page (#246)*
**Audit date:** 2026-11-13
**Auditor:** Saffron (Perch operator)

**Method.** Rendered live with Vite dev server + Playwright/Chromium at eight viewport/locale/mode combinations: 1440×900, 1280×800, 768×1024, 390×844, 320×700 (English); 1440×900 and 390×844 (Arabic, `dir="rtl"`); 1440×900 (`prefers-color-scheme: dark`). axe-core run on all eight. Computed-style probes for measured values. Interaction probes for the mobile menu, anchor targets, and external links. `vite build` for bundle figures. Network capture for request counts and font payloads. WCAG contrast ratios computed from the token values in `docs/design-system/tokens.css`.

**No source files were modified.** All probe scripts were written to a throwaway worktree and deleted; `git status` on the audited tree is clean.

---

## Scope note — read this first

An earlier draft of this audit was written against `feat/home-12-dashboard-accessibility`, which contains an older `HomePage` component in `web/src/app/App.tsx`. **That is not what is on `main`.** `main` ships a completely different, substantially better landing page: `web/src/routes/LandingRoute.tsx`, added in PR #246, with its own stylesheet, its own copy deck, mock product screens, a safety section, and OG/favicon metadata.

The two pages share almost nothing. Every finding below was re-derived from the `main` code. Where a finding from the old draft does not apply to `main`, it has been dropped rather than carried over.

**Verdict on `main`:** This is a good landing page. It has a real argument, real specifics, and it renders correctly in both languages and both themes. The defects below are genuine but they are the last 10%, not a rewrite. The most serious one is an accessibility violation that ships on every desktop viewport.

---

## Summary table

| # | Defect | Severity | Confidence | Where |
|---|---|---|---|---|
| 1 | Language toggle fails `label-content-name-mismatch` (axe serious) | **High** | Verified | `LandingRoute.tsx` header + footer |
| 2 | Footer language toggle has no accessible name at all | **High** | Verified | `LandingRoute.tsx` footer |
| 3 | Arabic sample renders in a Latin-only font on the English page | **High** | Verified | `LandingRoute.tsx` `MockScreen` |
| 4 | Mobile menu button is 30×32px, below the 44px touch target | Medium | Verified | `landing.css` `.landing-menu` |
| 5 | `Escape` does not close the mobile menu | Medium | Verified | `LandingRoute.tsx` |
| 6 | Desktop nav links are 26px tall, below touch target | Medium | Verified | `landing.css` `.landing-header nav` |
| 7 | `og:image` is a relative SVG — most platforms won't render it | Medium | Verified | `web/index.html` |
| 8 | No `og:url`, no `canonical`, no `og:site_name` | Medium | Verified | `web/index.html` |
| 9 | No privacy or terms link anywhere on the page | **High** | Verified | `LandingRoute.tsx` |
| 10 | Whole authenticated app ships in one chunk to anonymous visitors | Medium | Verified | `web/src/main.tsx` |
| 11 | 162 KB Arabic font loaded on the English page | Medium | Verified | `web/src/main.tsx` |
| 12 | Hardcoded focus-ring colour, not theme-aware | Low | Verified | `landing.css` |
| 13 | `--landing-green` custom property defined and never used | Low | Verified | `landing.css` |
| 14 | `min-height: 100vh` instead of `100dvh` | Low | Verified | `landing.css` |
| 15 | Mobile menu has no focus management or focus trap | Low | Verified | `LandingRoute.tsx` |
| 16 | `:has()` selector used to hide the app shell — fragile | Low | Verified | `landing.css` |
| 17 | Two React Router v7 future-flag warnings per load | Low | Verified | `web/src/main.tsx` |
| 18 | Landing page is not covered by the Playwright a11y suite | Medium | Verified | `e2e/navigation-accessibility.spec.ts` |
| 19 | `landing.css` is a single minified line — unreviewable | Low | Verified | `landing.css` |
| 20 | APK download link opens in the same tab with no `rel` | Low | Verified | `LandingRoute.tsx` |

---

## Part 1 — Accessibility defects

### 1. Language toggle violates `label-content-name-mismatch` — HIGH

axe-core, severity **serious**, reproduced on **5 of 8** runs (all desktop viewports, both languages, both themes):

```
label-content-name-mismatch  × 1
  button[aria-label="التبديل إلى العربية"]
  Fix: Text inside the element is not included in the accessible name
```

The button's visible text is `AR`. Its `aria-label` is `التبديل إلى العربية` ("Switch to Arabic"). The accessible name does not contain the visible text, so voice-control users cannot activate the button by saying what they see ("click AR").

Source, `LandingRoute.tsx` header:

```jsx
<button className="language-toggle"
  aria-label={language === 'ar' ? 'Switch to English' : 'التبديل إلى العربية'}
  onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}>
  {language === 'ar' ? 'EN' : 'AR'}
</button>
```

**Why it did not fail on mobile:** at ≤760px the header `<nav>` is `display: none` until the menu is opened, so the button is not in the accessibility tree during the scan. The defect is present in the DOM on every viewport; it is only *detected* on desktop. Fixing it fixes both.

**Fix:** make the visible text part of the name, e.g. `aria-label="AR — التبديل إلى العربية"`, or drop `aria-label` and use a visually-hidden span inside the button carrying the full sentence.

### 2. Footer language toggle has no accessible name — HIGH

The footer renders a second toggle:

```jsx
<button className="language-toggle" onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}>
  {language === 'ar' ? 'EN' : 'AR'}
</button>
```

Measured accessible-name inventory on the English page at 1440px:

| Visible text | `aria-label` |
|---|---|
| `AR` | `التبديل إلى العربية` |
| `AR` | **`null`** |

The footer button's only content is the two-character string `AR`. A screen reader announces "AR, button" with no indication that it changes language. axe did not flag this one because a two-letter name is technically non-empty — it is a real usability defect that the automated scan misses.

**Fix:** same treatment as #1.

### 3. Arabic sample text renders in a Latin-only font — HIGH

This is the most interesting defect on the page, and it is a direct hit on the section that exists to prove bilingual quality.

The "Arabic and English, built in" section renders two `MockScreen` figures — one with `dir="rtl"` and Arabic strings, one English. On the **English** page, measured:

```
document.documentElement.lang = "en"
arabicMockText  = "ملاحظات اليوم"
arabicMockFont  = "DM Sans Variable", system-ui, -apple-system, ...
arabicMockHasLangAttr = "en"          ← inherited from <html lang="en">
loadedFonts     = ["DM Sans Variable"] ← Noto Sans Arabic never loaded
```

DM Sans has no Arabic glyphs. The Arabic sample therefore falls back to whatever the operating system supplies — DejaVu Sans, Noto Naskh, or worse. The visual result is a visibly different typeface from the rest of the page, at a different weight and with different letterform proportions, in the one section whose entire purpose is to demonstrate that Arabic is a first-class citizen.

The root cause is that the Arabic strings live inside a document whose `lang` is `en`, and the Arabic font is gated on `:lang(ar)` in `base.css`:

```css
:lang(ar) { font-family: 'Noto Sans Arabic Variable', 'DM Sans Variable', sans-serif; }
```

`MockScreen` sets `dir="rtl"` but never sets `lang`. `dir` is not `lang`. So the selector never matches.

**Measured consequence:** on the English page the browser loads **only** DM Sans (36 KB) — the 162 KB Noto Sans Arabic payload is never requested, because no element claims to be Arabic. On the Arabic page it loads both.

**Fix:** add `lang="ar"` to the Arabic `MockScreen` root (and `lang="en"` to the English one). One attribute. It fixes the rendering and, as a side effect, makes the font strategy honest.

### 4. Mobile menu button is 30×32px — MEDIUM

Measured at 390px and 320px:

```
BUTTON  "☰Menu"  30×32
```

WCAG 2.5.5 (AAA) wants 44×44. WCAG 2.5.8 (AA, 2.2) wants 24×24 minimum — so this passes AA, but it is the primary navigation control on mobile and it is the smallest interactive element on the page. Every other control on the page meets 44px.

`landing.css`:
```css
.landing-menu{display:none}
@media(max-width:760px){.landing-menu{display:block}}
```

No size is ever set, so it inherits the glyph box.

### 5. `Escape` does not close the mobile menu — MEDIUM

Interaction probe at 390px:

```
closed:      navDisplay="none",  aria-expanded="false", btn 30×32
after click: navDisplay="flex",  aria-expanded="true"
after Escape: navDisplay="flex", aria-expanded="true"   ← still open
```

The menu is a disclosure widget with `aria-expanded` and `aria-controls` correctly wired, and it closes on link click (`onClick={() => setMenuOpen(false)}`). But there is no `keydown` handler, so `Escape` — the standard dismissal gesture — does nothing. A keyboard user who opens the menu must tab back to the button to close it.

### 6. Desktop nav links are 26px tall — MEDIUM

Measured at 1440px:

```
A "Caregiver Daily Notes"  168×26
A "How it works"            94×26
A "Safety"                  47×26
A "Languages"               79×26
A "For reviewers"           98×26
A "GitHub repository"      108×21
```

Six targets below 44px. The brand link and the four nav links are the page's primary navigation. `landing.css` sets `gap: 20px` and no padding or `min-height`, so each link is exactly its text line box.

This is a real-world problem on touch laptops and tablets in landscape (768–1280px), where these are the visible nav rather than the hamburger.

### 7. Landing page is not in the Playwright a11y suite — MEDIUM

`e2e/navigation-accessibility.spec.ts:84` scans six widths:

```js
for (const width of [1280, 1000, 768, 540, 390, 320]) {
```

but the only navigation it performs is `page.goto('/app')` (line 31). The landing route is never scanned. The project's own README and the landing page copy both advertise "Playwright with axe accessibility scans at 1280, 768, 390, and 320px in both languages" — that claim is true for the authenticated app and false for the public landing page.

This is why defect #1 shipped: the harness exists, it just does not point at this route.

**Fix:** add `page.goto('/')` to the same loop. It would have caught #1 and #2 on the first run.

---

## Part 2 — Metadata and sharing defects

### 8. `og:image` is a relative SVG — MEDIUM

`web/index.html`:

```html
<meta property="og:image" content="/caregiver-notes-preview.svg" />
```

Two problems:

1. **Relative URL.** The Open Graph spec requires an absolute URL. Slack, WhatsApp, LinkedIn, Facebook, and X all fail to resolve relative `og:image` values. The preview image will not appear anywhere.
2. **SVG.** Even with an absolute URL, most social crawlers reject SVG for `og:image` — Facebook and LinkedIn both require raster (JPEG/PNG/WebP). Twitter/X is inconsistent.

The file itself is correct — `web/public/caregiver-notes-preview.svg` is a proper 1200×630 card, the right aspect ratio, with the right background. It just cannot be consumed by the platforms it was built for.

**Fix:** export the same artwork to `caregiver-notes-preview.png` at 1200×630 and reference it absolutely, e.g. `https://<domain>/caregiver-notes-preview.png`.

### 9. Missing `og:url`, `canonical`, `og:site_name` — MEDIUM

Present: `og:title`, `og:description`, `og:type`, `og:image`, `twitter:card`, `description`, `theme-color`, `icon`.

Missing: `og:url` (grep count: 0), `canonical` (grep count: 0), `og:site_name`, `twitter:title`, `twitter:description`, `twitter:image`.

Without `og:url`, platforms cannot deduplicate shares of the same page. Without `canonical`, any query-string or trailing-slash variant is treated as a distinct URL. Without `twitter:*` beyond `card`, X falls back to `og:*` — which mostly works, but `twitter:image` is worth setting explicitly given #8.

Also worth noting: `twitter:card` is `summary_large_image`, which is correct for a 1200×630 asset — but only once that asset is a raster at an absolute URL.

---

## Part 3 — Content and trust defects

### 10. No privacy or terms link anywhere — HIGH

Grep across `LandingRoute.tsx` for `privacy|Privacy|terms|Terms`: **0 matches**.

The page collects nothing itself, but it is the front door to an app that stores, per the page's own copy:

> Mood, appetite, sleep, mobility, medication taken, pain, falls, and free-text notes in Arabic, English, or both.

That is health-adjacent data about named individuals. The page is scrupulous about *AI* data handling — it devotes eight bullet points to it, including the genuinely careful disclosure that free-text notes may contain identifying details and are sent as written — and then says nothing at all about who holds the data, for how long, or under what terms.

The page does carry a prominent capstone notice:

> This is a capstone project. It uses synthetic data only, is not medical advice, and must not be used with real patient data.

That notice is doing a lot of load-bearing work, and it is the reason this is not a *critical* finding. But "synthetic data only" is a statement about the demo, not a privacy policy, and a reviewer or a cautious caregiver will look for the latter. The `docs/privacy/` directory exists in the repo — the landing page does not link to it.

**Fix:** add a footer link to the privacy documentation, and a one-line statement of what happens to notes.

### 11. The safety section is the strongest asset and it is buried below the fold — MEDIUM (recommendation)

The page's best content is the "Safety comes first" list. Eight specific, verifiable, unusual claims:

- Red flags come from fixed rules, not AI.
- AI cannot add, remove, or change medications or doses.
- Each observation links back to notes and supporting quotes.
- Unclear or conflicting information stays marked as uncertain.
- Saving a note never depends on AI being available.
- Structured profile fields are excluded from AI requests; free-text notes are sent as written.
- You decide whether to accept, edit, or dismiss each plan; previous versions are kept.
- Only you can see your recipients, enforced by the server.

This is the differentiator. Almost every AI health product makes vague safety noises; this one makes eight falsifiable statements, and the codebase backs them up. It sits third on the page, after "How it works" and "What you can record."

For an audience of caregivers and capstone reviewers, the safety argument is the reason to trust the product at all. It belongs directly under the hero, or at minimum as a link in the hero's own copy.

### 12. Hero disclaimer and the capstone notice are separated by four sections — LOW

The hero carries:

> Care-support suggestions generated from your notes. Not medical advice.

The capstone notice ("synthetic data only, must not be used with real patient data") appears much further down, after the apps section. A visitor who reads only the hero and leaves has seen the weaker of the two disclaimers. Given that the stronger one is the one that actually constrains use, consider surfacing it in the hero or immediately beneath it.

---

## Part 4 — Performance defects

### 13. Whole authenticated app ships to anonymous visitors — MEDIUM

`vite build` on `main`:

```
dist/assets/index-DnDtosx9.css   126.55 kB │ gzip:  23.98 kB
dist/assets/index-BTR4AU1g.js    485.96 kB │ gzip: 141.74 kB
✓ 120 modules transformed
```

Single JS chunk, single CSS chunk. No `React.lazy`, no `Suspense` (grep across `App.tsx` and `main.tsx`: zero hits). A visitor who lands on `/` and leaves downloads the notes editor, the plans flow, the summary renderer, the settings page, and the AI-settings dialog.

Measured network on the English landing page at 1440px:

| | |
|---|---|
| Requests | 106 |
| Total transferred | 3,805 KB |
| Of which JS | 3,767 KB |
| Fonts | DM Sans 36 KB |

**3.8 MB and 106 requests** to read a landing page. The dev-server figure overstates production (unbundled modules), but the single-chunk build means production still ships the entire app before the hero paints.

**Fix:** route-level `React.lazy` for everything under `/app/*`. The landing route needs `react-router-dom`, `Icon`, and `landing.css` — nothing else. Expect the initial chunk to drop to roughly a third.

### 14. 162 KB Arabic font on the English page — MEDIUM

`web/src/main.tsx` imports the Arabic font unconditionally:

```js
import '@fontsource-variable/noto-sans-arabic';
```

Measured font payloads:

| Page | Fonts requested |
|---|---|
| English | `dm-sans-latin-wght-normal.woff2` 36 KB |
| Arabic | `noto-sans-arabic-latin-wght-normal.woff2` 31 KB + `noto-sans-arabic-arabic-wght-normal.woff2` 162 KB |

On the English page the 162 KB Arabic face is *declared* but never *fetched* — the browser is smart enough not to download a font whose glyphs no element needs. So the English page does not currently pay for it.

But the moment defect #3 is fixed — the moment `lang="ar"` is added to the Arabic mock screen — the English page **will** start fetching 162 KB to render four short Arabic strings in a decorative sample. That is the trap: fixing the rendering bug naively creates a performance bug.

**Fix both together:** add `lang="ar"` *and* subset the Arabic face, or lazy-load the Arabic font only when `language === 'ar'`. The `@fontsource-variable` package ships per-subset files already (`arabic`, `latin`, `latin-ext`, `math`, `symbols`) — importing only `arabic` and `latin` rather than the full family would cut the Arabic page's font cost substantially.

---

## Part 5 — Code-quality defects

### 15. Hardcoded focus-ring colour — LOW

`landing.css`:

```css
.landing-header a:focus-visible, .landing-header button:focus-visible,
.landing-page a:focus-visible, .landing-page button:focus-visible {
  outline: 3px solid #b45b16;
  outline-offset: 3px;
}
```

Every other colour in this stylesheet is a token. This one is a literal, and it is the only non-token colour in the file (grep for hex: `#285b45` as a fallback, `#b45b16` here).

Measured contrast for the ring against each surface:

| Surface | Ratio | Verdict |
|---|---|---|
| Light surface `#FAF9F5` | 4.48 | fine for a 3px ring |
| Light background `#F7F6F3` | 4.37 | fine |
| Dark surface `#1A1F1F` | 3.53 | passes 3:1, but muddy |
| Dark background `#121616` | 3.86 | passes 3:1 |

It technically passes the 3:1 non-text threshold everywhere, so this is not a violation. But a burnt-orange ring on a charcoal dark theme is visually off-brand, and it will not track if the palette changes. The rest of the app uses `var(--color-outline)`.

### 16. `--landing-green` is defined and never used — LOW

```css
.landing-page{--landing-green:var(--web-color-green,#285b45); ...}
```

Grep for `var(--landing-green` across the stylesheet: **0 uses**. Dead declaration, and it depends on `--web-color-green`, which is part of the vestigial token layer described below.

### 17. `:has()` used to hide the app shell — LOW

```css
.site-shell:has(.landing-page) > .site-header,
.site-shell:has(.landing-page) > .site-footer { display: none }
```

The landing page renders inside `AppShell`, so the shell's own header and footer have to be suppressed. `:has()` is the mechanism. It works in all current evergreen browsers (Chrome 105+, Safari 15.4+, Firefox 121+), and the measured result is correct — the shell header and footer are `display: none`, the landing header and footer are visible, and there is exactly one `<main>`.

But it means the landing page's chrome depends on a CSS parent selector rather than on the route not rendering the shell at all. Verified landmark inventory at 1440px:

```
HEADER .site-header      visible: false   ← suppressed by :has()
NAV    .site-nav         visible: false   ← suppressed
MAIN   .main-content     visible: true
HEADER .landing-header   visible: true
NAV    (landing)         visible: true
ASIDE  .landing-notice   visible: true
FOOTER .landing-footer   visible: true
FOOTER .site-footer      visible: false   ← suppressed
```

Two headers and two footers are in the DOM on every landing page load; one of each is hidden by CSS. Cleaner: render `LandingRoute` outside `AppShell` in `App.tsx`, which is a routing change rather than a CSS trick.

### 18. `landing.css` is five minified lines — LOW

The file is 5,367 bytes across **5 lines**, each several thousand characters. Every rule is on one line. This is unreviewable in a diff: any change to any rule shows as a whole-line replacement of several thousand characters, so a reviewer cannot see what actually changed.

Every other stylesheet in the repo (`base.css` 305 lines, `ui.css` 244 lines) is conventionally formatted. This one is an outlier, and it is the newest file.

### 19. `min-height: 100vh` — LOW

```css
.landing-page{min-height:100vh}
```

`100vh` on mobile Safari and Chrome for Android includes the collapsing URL bar, so the page is taller than the visible viewport on first paint. The rest of the app uses `100dvh` (`base.css`: `.site-shell { min-height: 100dvh }`). One-word fix.

### 20. APK link opens in the same tab, no `rel` — LOW

Measured:

```
apk: { href: ".../releases/download/v0.1.1/app-minified.apk",
       target: null, rel: null, download: false }
```

All six external links on the page (APK ×2, repo, ARCHITECTURE.md, releases, repo again) have `target: null` and `rel: null`. Two consequences:

- Navigating to GitHub replaces the landing page; there is no way back except the browser's back button.
- No `rel="noopener"` — harmless without `target="_blank"`, but it becomes a live issue the moment someone adds `target="_blank"` to fix the first point.

The APK link is verified live: `HTTP 200`, redirects to `release-assets.githubusercontent.com`, correct `application/vnd.android.package-archive` content type. The URL is good; the link behaviour is not.

### 21. Two React Router v7 future-flag warnings per load — LOW

Reproduced on all eight runs:

```
⚠️ React Router Future Flag Warning: React Router will begin wrapping state updates
   in `React.startTransition` in v7 ... (v7_startTransition)
⚠️ React Router Future Flag Warning: Relative route resolution within Splat routes
   is changing in v7 ... (v7_relativeSplatPath)
```

Console is otherwise clean — **zero** 404s, zero page errors, zero failed requests on all eight runs. That is a real improvement over the feature-branch version, which 404'd on `/favicon.ico` on every load. `main` has a proper `<link rel="icon" type="image/svg+xml" href="/caregiver-notes-preview.svg">`.

The two warnings are migration debt, not defects.

---

## Part 6 — What is genuinely good (do not touch)

Stated explicitly, because the instinct on "this is awful" is to rewrite what already works.

**The copy is specific.** "A clearer picture of each day, in a minute." names a time cost. "Write down what you notice. Review a simple summary and a care suggestion you stay in control of." names the workflow and the control boundary in two sentences. The three-step "How it works" is concrete: record → review a 7/14/30-day summary → accept, edit, or dismiss a suggested plan. This is not the anonymous mood-copy of the feature-branch version.

**The safety section is the best thing on the page** (see #11). Eight falsifiable claims, all of which the codebase can back.

**The mock screens are a real improvement over illustration.** `MockScreen` renders an actual note form — mood, sleep, notes, a save button — in both directions. It shows the product instead of a blank notebook. The only flaw is the font (#3).

**RTL is correct.** `dir="rtl"` on the Arabic mock, `dir` set on `<html>` by the i18n provider, logical CSS properties throughout (`inset-inline`, `margin-inline`, `padding-inline`), and the Arabic page measured `lang="ar" dir="rtl"` with no horizontal overflow at 1440 or 390.

**Dark mode is correct.** Unlike the feature-branch version, `landing.css` uses `--caregiver-*` tokens throughout. Measured dark-mode values: surface `rgb(26,31,31)`, background `rgb(18,22,22)`, heading `rgb(227,231,234)`. The mock screens, cards, and safety panel all re-theme. No white-on-dark artifacts.

**Zero horizontal overflow** at 1440 / 1280 / 768 / 390 / 320, in both languages.

**Zero 404s, zero failed requests, zero page errors** across all eight runs.

**Contrast passes.** Primary button `#000` on `#00897B` = **4.86:1** (AA). Muted `#5B6B70` on `#F7F6F3` = **5.14:1** (AA). Ink on background = **13.6:1** (AAA). Warning notice `#1F2A2E` on `#FBF0D9` = **12.99:1** light, `#E3E7EA` on `#3A2A0A` = **11.14:1** dark.

**Structure is sound.** Exactly one `<h1>`. One `<main>`. Skip link present and visible. All decorative SVGs `aria-hidden="true"` with `focusable="false"`. All four anchor targets (`#how-it-works`, `#safety`, `#languages`, `#reviewers`) exist and resolve. `aria-expanded` and `aria-controls` correctly wired on the mobile menu button.

**TypeScript and build are clean.** `tsc --noEmit` exit 0. `vite build` 1.10s.

---

## Part 7 — Recommended order of work

**Ship-blocking (accessibility, small diffs):**

1. **#1 + #2** — fix both language toggles' accessible names. Two lines. Clears the only axe violation on the page.
2. **#3 + #14 together** — add `lang="ar"` to the Arabic `MockScreen`, and subset or lazy-load the Arabic font in the same change so the fix does not add 162 KB to the English page.
3. **#18** — point the existing Playwright a11y loop at `/`. One line, and it prevents regressions of #1 and #2.

**High value, small effort:**

4. **#4 + #6** — give `.landing-menu` and the desktop nav links a 44px minimum. Pure CSS.
5. **#5** — add an `Escape` keydown handler to close the mobile menu.
6. **#8 + #9** — export the OG card as PNG at 1200×630, reference it absolutely, add `og:url` and `canonical`.
7. **#10** — add a privacy link to the footer. The docs already exist in `docs/privacy/`.

**Structural:**

8. **#13** — route-level `React.lazy` for `/app/*`. Biggest single win: stops shipping the whole product to anonymous visitors.
9. **#17** — render `LandingRoute` outside `AppShell` instead of hiding the shell with `:has()`.
10. **#11** — move the safety section up, or link it from the hero.

**Cleanup:**

11. **#15, #16, #19, #20, #21** — token the focus ring, delete the dead custom property, `100dvh`, `rel="noopener noreferrer"` on external links, enable the two v7 future flags.
12. **#18 (formatting)** — reformat `landing.css` to match `base.css`/`ui.css` conventions so it can be reviewed.

---

## Appendix A — Measurements

**Viewports:** 1440×900, 1280×800, 768×1024, 390×844, 320×700 (EN); 1440×900, 390×844 (AR); 1440×900 dark.

**Document height:** 3,657px desktop EN · 3,976px tablet · 6,225px mobile 390 · 6,880px mobile 320 · 3,695px desktop AR · 6,064px mobile AR.

**Hero heading size:** 64px desktop · 38.4px tablet · 36px mobile · 54px desktop AR · 34px mobile AR.

**Build:** 120 modules · `index.js` 485.96 kB (141.74 kB gzip) · `index.css` 126.55 kB (23.98 kB gzip) · 1.10s.

**Network:** EN 106 requests / 3,805 KB (JS 3,767 KB, fonts 36 KB) · AR 107 requests / 3,962 KB (fonts 193 KB). Zero non-200 responses.

**axe-core:** 1 violation × 5 runs (`label-content-name-mismatch`, serious), 0 × 3 runs (mobile — button not in a11y tree).

**Console:** 0 errors · 0 404s · 2 React Router v7 warnings per load.

**Contrast (computed from `docs/design-system/tokens.css`):**

| Pair | Ratio | Level |
|---|---|---|
| `#000` on `#00897B` (primary button) | 4.86 | AA |
| `#5B6B70` on `#F7F6F3` (muted) | 5.14 | AA |
| `#1F2A2E` on `#F7F6F3` (ink) | 13.60 | AAA |
| `#1F2A2E` on `#FBF0D9` (warning notice, light) | 12.99 | AAA |
| `#E3E7EA` on `#3A2A0A` (warning notice, dark) | 11.14 | AAA |
| `#B0BEC5` on `#121616` (dark muted) | 9.56 | AAA |
| `#b45b16` on `#FAF9F5` (focus ring, light) | 4.48 | passes 3:1 |
| `#b45b16` on `#1A1F1F` (focus ring, dark) | 3.53 | passes 3:1 |

**Touch targets below 44px (desktop 1440):** 6 — brand link 168×26, four nav links 26px tall, GitHub link 108×21. **(mobile 390):** 3 — brand link 168×26, menu button 30×32, GitHub link 108×21.

**Accessible-name inventory (EN, 1440):** `AR`/`التبديل إلى العربية` (header) · `AR`/`null` (footer).

---

## Appendix B — Verification log

Every claim above traces to one of these.

| Claim | Evidence |
|---|---|
| axe violation, 5 of 8 runs | axe-core 4.x via Playwright, `resultTypes:['violations']` |
| Toggle visible text vs aria-label | `document.querySelectorAll('.language-toggle')` → `{visible, aria}` |
| Arabic mock font | `getComputedStyle(strong).fontFamily` + `document.fonts` loaded set |
| Arabic mock `lang` | `strong.closest('[lang]').getAttribute('lang')` → `"en"` |
| Menu button 30×32 | `getBoundingClientRect()` at 390px and 320px |
| Escape does not close menu | `keyboard.press('Escape')` → `aria-expanded` still `"true"` |
| Nav links 26px | `getBoundingClientRect()` on `.landing-header nav a` |
| `og:image` relative | `grep 'og:image' web/index.html` |
| No canonical/og:url | `grep -c 'canonical\|og:url' web/index.html` → `0` |
| No privacy/terms | `grep -c "privacy\|terms" src/routes/LandingRoute.tsx` → `0` |
| Single chunk, no lazy | `vite build` output + `grep -c "lazy\|Suspense"` → `0` |
| Font payloads | Playwright `response` listener, `resourceType() === 'font'` |
| Focus ring hex | `grep -o '#[0-9a-fA-F]\{3,6\}' landing.css` |
| `--landing-green` unused | `grep -o 'var(--landing-green[^)]*)'` → 0 matches |
| `100vh` | `grep 'min-height:100'` |
| `:has()` shell suppression | `landing.css` + landmark visibility probe |
| a11y suite skips `/` | `e2e/navigation-accessibility.spec.ts:31,84` |
| APK link live | `curl -sIL` → 200, `application/vnd.android.package-archive` |
| External links no rel | `querySelectorAll('a[href^="http"]')` → `rel: null` ×6 |
| Contrast ratios | WCAG 2.1 relative-luminance formula over `tokens.css` values |
| Zero overflow | `documentElement.scrollWidth > clientWidth` at 8 configs |
| One `<h1>`, one `<main>` | `querySelectorAll('h1'/'main').length` |
