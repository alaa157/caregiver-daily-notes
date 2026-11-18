# ADR 002: No external deep links in the standalone build

Status: accepted for the standalone (LOCAL_MODE) build. Tracked in
[#919](https://github.com/alaa157/caregiver-daily-notes/issues/919).

## Context

QA found that opening an external URI such as
`android-app://androidx.navigation/settings` does not reach any screen of the
standalone app; the Android resolver falls through to the browser/Play Store.
The app declares only a `MAIN`/`LAUNCHER` intent filter on `MainActivity`, with
no custom URI scheme and no registered `navDeepLink`s. That is expected:
`android-app://androidx.navigation/<route>` URIs are internal
`NavDeepLinkRequest` values that Compose Navigation generates for programmatic
`navigate(route)` calls; they are not OS-level deep links and are not meant to
be handled by another app.

There is no crash and no broken in-app screen — in-app navigation is entirely
programmatic through `AppRoutes` and the `NavController`. The gap only matters
if something outside the app needs to open a specific screen (a notification,
widget, QR/link, docs, or deep-link-based UI tests). No current feature needs
that.

The standalone app is local-first and multi-account: care data lives in one
Room database per account ([ADR 001](001-per-account-databases.md)), selected by
the active account. An external link into a personal screen (for example a
recipient or a note) would have to resolve *which* local account the link
belongs to. Getting that wrong risks showing one account's sensitive care data
in another account's context. Local accounts and their databases are not
addressed by any URI scheme today.

## Decision

The standalone build does **not** support external deep links:

- No app-owned URI scheme (for example `caregiver://`) is declared, and no
  browsable/custom-scheme `<intent-filter>` is added to `MainActivity`. The
  manifest keeps only the `MAIN`/`LAUNCHER` filter.
- No `navDeepLink` is registered for any destination. In-app navigation stays
  programmatic via `AppRoutes` and `NavController`.
- Typed links are not advertised in documentation, notifications, or UI copy.
- External route URIs are intentionally left to the OS resolver; on a device
  with no matching handler they may fall through to the browser/Play Store.
  This is accepted behavior, not a defect to work around.

This is a documentation-only decision: no production code, manifest, or
navigation structure changes.

## Alternatives considered

### App-owned scheme with a deep link per public destination

Rejected. Declaring `caregiver://` and registering `navDeepLink`s for the
public destinations (home, people, notes, history, settings, person detail)
would require editing the `NavHost` in `MainScaffold.kt` and would expose
personal screens to external callers. Each such link must resolve the correct
local account and an unknown route must fall back safely to Home; neither
rule exists today, and a mistake could cross the per-account isolation that
[ADR 001](001-per-account-databases.md) establishes. There is no product need
that justifies this surface now.

### Handle incoming intents in `MainActivity` and navigate programmatically

Rejected for the same account-resolution and isolation reasons. It would also
duplicate the route table outside `AppRoutes`, creating a second place where
destination templates and arguments must be kept in sync.

## Consequences

Positive:

- No external entry point into account-scoped care data, so no new
  cross-account data-exposure surface.
- The navigation contract has a single source of truth (`AppRoutes`), and the
  in-app back stack is unchanged.
- No extra manifest attack surface (no exported custom scheme or browsable
  filter).

Costs:

- External tooling cannot deep-link into a specific screen; UI tests must
  drive the app through its own interface rather than by URI. In particular,
  `revyl device navigate --url "android-app://androidx.navigation/<route>"`
  will not open an app screen.
- If a future feature genuinely needs external entry points, this decision
  must be revisited with explicit rules for account resolution, an unknown
  route falling back safely to Home, and verification of back-stack and
  per-account isolation.

No code changed, so there is no regression to the in-app back stack or to
per-account isolation from this decision.

## Related decisions

- [#919](https://github.com/alaa157/caregiver-daily-notes/issues/919): QA
  finding recorded by this ADR (closed as `wontfix`).
- [ADR 001](001-per-account-databases.md): per-account databases and the
  isolation any future deep link would have to respect.
- [#581](https://github.com/alaa157/caregiver-daily-notes/issues/581): local
  accounts / per-account databases.
- [#878](https://github.com/alaa157/caregiver-daily-notes/issues/878): a
  separate in-app navigation crash; not part of this decision.
