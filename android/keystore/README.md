# Demo signing keystore

`demo.keystore` signs the **offline demo** APKs (`minified` build type). It is
deliberately committed and public — it is debug-grade and must never sign a
production build.

| | |
| --- | --- |
| Alias | `androiddebugkey` |
| Store / key password | `android` |
| Type | PKCS12, RSA 2048 |
| SHA-256 fingerprint | `26:E2:90:BC:D3:73:81:D5:D6:DD:31:F1:9F:33:28:A7:51:CA:36:BA:4D:A5:72:9E:A1:C9:EB:3C:B3:68:B3:7B` |

## Why it exists

Android refuses to update an installed app when the new APK is signed with a
different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). CI runners generate a
fresh throwaway debug key for every run, so demos published straight from CI
could never install over one another and testers silently kept old builds.
Signing every demo with this checked-in key keeps the signature stable across
CI runs and local builds.

`app/build.gradle.kts` falls back to the local debug key when this file is
missing, so forks and machines that strip it can still build.

Transition note: demos published before the keystore was added were signed
with per-run keys, so the first install of a new demo over one of those needs
one uninstall. Every later demo updates in place.

Production releases use the `release` signing config (env-provided
`KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`), never
this key.
