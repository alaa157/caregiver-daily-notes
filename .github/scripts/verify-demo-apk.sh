#!/usr/bin/env bash
# Structural gate for the demo APK: the bytes users install must carry the
# version the release claims, the Settings actions (incl. Arabic strings),
# and the shared demo signature. This is the check that would have caught
# the "v0.3.0 demo build reports itself as 0.2.0" drift.
#
# Usage: verify-demo-apk.sh <path-to-apk> [path-to-build.gradle.kts]
set -euo pipefail

APK="${1:?usage: verify-demo-apk.sh <apk> [build.gradle.kts]}"
GRADLE_FILE="${2:-android/app/build.gradle.kts}"

test -f "$APK" || { echo "ERROR: APK not found: $APK"; exit 1; }
test -f "$GRADLE_FILE" || { echo "ERROR: Gradle file not found: $GRADLE_FILE"; exit 1; }

AAPT2=$(find "${ANDROID_HOME:?ANDROID_HOME is not set}" -type f -name aapt2 | head -1)
APKSIGNER=$(find "$ANDROID_HOME" -type f -name apksigner | head -1)
test -n "$AAPT2" || { echo "ERROR: aapt2 not found under ANDROID_HOME"; exit 1; }
test -n "$APKSIGNER" || { echo "ERROR: apksigner not found under ANDROID_HOME"; exit 1; }

# Tool output is dumped to files before matching: piping into `grep -q`
# closes the pipe as soon as the pattern matches, and with `pipefail` the
# producer's SIGPIPE then looks like a failed check even though it matched.
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

EXPECTED_NAME=$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -1)
EXPECTED_CODE=$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE" | head -1)
echo "Expected: versionName=$EXPECTED_NAME versionCode=$EXPECTED_CODE"

"$AAPT2" dump badging "$APK" > "$WORK_DIR/badging.txt"
grep -E '^package:' "$WORK_DIR/badging.txt" || true
CODE=$(sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" "$WORK_DIR/badging.txt" | head -1)
NAME=$(sed -nE "s/.*versionName='([^']+)'.*/\1/p" "$WORK_DIR/badging.txt" | head -1)
[ "$CODE" = "$EXPECTED_CODE" ] || { echo "ERROR: APK versionCode=$CODE, expected $EXPECTED_CODE"; exit 1; }
[ "$NAME" = "$EXPECTED_NAME" ] || { echo "ERROR: APK versionName=$NAME, expected $EXPECTED_NAME"; exit 1; }

# The Settings contract: the Data & Backup section and its four actions,
# each with an Arabic value so the language switch has something to show.
"$AAPT2" dump resources "$APK" > "$WORK_DIR/resources.txt"
for key in settings_data_backup csv_import_title csv_export_title backup_create_title backup_restore_title; do
  grep -q "string/$key\$" "$WORK_DIR/resources.txt" || { echo "ERROR: missing string/$key"; exit 1; }
done
for key in settings_data_backup csv_import_title backup_restore_title; do
  CONTEXT=$(grep -A4 "string/$key\$" "$WORK_DIR/resources.txt")
  case "$CONTEXT" in
    *"(ar) "*) ;;
    *) echo "ERROR: missing Arabic value for string/$key"; exit 1 ;;
  esac
done

# The demo keystore is checked in (android/keystore/demo.keystore) so every
# demo build shares one signature and installs over the previous one. If the
# file goes missing the Gradle build silently falls back to a per-machine
# debug key, which is how earlier demos ended up refusing to update — so the
# fingerprint is enforced, not just printed. apksigner reports the digest as
# bare lowercase hex; android/keystore/README.md carries the same certificate
# with colons.
EXPECTED_DEMO_SHA256="26e290bcd37381d5d6dd31f19f3328a751ca36ba4da5729ea1c9eb3cb368b37b"
"$APKSIGNER" verify --print-certs "$APK" > "$WORK_DIR/certs.txt"
CERT_LINE=$(awk 'tolower($0) ~ /sha-256/ { print; exit }' "$WORK_DIR/certs.txt")
CERT_HEX=$(printf '%s' "$CERT_LINE" | tr '[:upper:]' '[:lower:]' | grep -oE '[0-9a-f]{64}' || true)
echo "Signer: $CERT_LINE"
if [ "$CERT_HEX" != "$EXPECTED_DEMO_SHA256" ]; then
  echo "ERROR: APK is not signed with the shared demo key."
  echo "Expected SHA-256: $EXPECTED_DEMO_SHA256"
  echo "Got:              ${CERT_HEX:-(no SHA-256 digest found)}"
  echo "Check android/keystore/demo.keystore exists before publishing."
  exit 1
fi

echo "OK: $APK carries version $EXPECTED_NAME ($EXPECTED_CODE) with the Data & Backup actions."
