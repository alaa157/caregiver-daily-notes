# Android release process

Two release lines exist. This document covers both, plus the checks that
keep the artifacts identifiable.

## Versioning rule (enforced)

- `versionCode` in `android/app/build.gradle.kts` **increases by one for
  every published APK**. Android uses it (not `versionName`) to decide
  whether an install is an update; a duplicate value means the installer
  cannot tell the builds apart.
- `versionName` must equal the tag (`vX.Y.Z` ⇔ `versionName = "X.Y.Z"`).
  The `Android Release` workflow fails before building if they differ, so
  a mislabeled release (e.g. the `v0.3.0 (demo)` APK that reported `0.2.0`)
  cannot ship again.

Current version: `versionCode 3`, `versionName "0.3.1"` → next tag `v0.3.1`.

## New line: `v*` tags (signed release + demo prerelease)

`.github/workflows/android-release.yml` runs on any `v*` tag push:

1. Fails if the tag does not match `versionName`.
2. If the release keystore secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`) exist, builds `:app:assembleRelease`,
   verifies its certificate fingerprint against `EXPECTED_CERT_SHA256`,
   and creates release `vX.Y.Z` with the signed APK.
3. Always builds `:app:assembleMinified`, runs
   `.github/scripts/verify-demo-apk.sh` on it (version, Data & Backup
   actions, Arabic strings, signer), and publishes prerelease
   `vX.Y.Z-demo` with the APK. The release notes carry the source commit,
   version, APK SHA-256, and signer fingerprint.

Exact commands for the next demo:

```bash
# 1. Make sure versionCode/versionName already match the intended tag.
grep -E 'version(Code|Name)' android/app/build.gradle.kts

# 2. Tag and push (the tag is what triggers the workflow).
git tag v0.3.1
git push origin v0.3.1
```

## Legacy line: `demo-v*` tags (offline demo app)

`.github/workflows/android.yml`'s `release-apk` job rebuilds
`:app:assembleMinified` from the tagged commit and publishes a prerelease
with generated notes. Same build, older app line.

## Building the demo APK locally

```bash
cd android
./gradlew :app:assembleMinified
# artifact: android/app/build/outputs/apk/minified/app-minified.apk
```

Verify it before handing it out:

```bash
ANDROID_HOME=$ANDROID_HOME bash .github/scripts/verify-demo-apk.sh \
  android/app/build/outputs/apk/minified/app-minified.apk
```

## Signing

- **Demo APKs** are signed with the checked-in public demo keystore
  (`android/keystore/demo.keystore`, see its README). CI previously
  generated a fresh debug key per run, so every demo refused to install
  over the previous one (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and testers
  silently stayed on old builds. Keep using the checked-in key.
  One-time migration: demos published before the shared key existed
  require one uninstall before the next demo installs.
- **Production releases** use the `release` signing config fed from CI
  secrets; the demo key never signs a release.

## Manual publishing (no tag)

If a tag cannot be pushed, publish from a branch CI artifact — this is how
`v0.3.0-demo` was originally produced:

```bash
# Download the artifact from the successful "Android" workflow run,
# then verify it locally with .github/scripts/verify-demo-apk.sh first.
gh release create v0.3.1-demo app-minified.apk \
  --prerelease --title "v0.3.1 (demo)" \
  --notes "Offline demo build for testers — not for production updates.
- Source commit: <sha>
- App version: 0.3.1 (versionCode 3)"
```

## Identifying the build installed on a device

- Settings → bottom row shows `Version <versionName>`.
- `adb shell dumpsys package com.caregiver.mobile | grep versionName`
  (also prints `versionCode`).
- APK assets: compare the release asset's `sha256sum` against the digest
  in the release notes.
