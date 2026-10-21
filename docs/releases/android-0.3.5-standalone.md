# Android 0.3.5 — standalone build

> The current release line: `versionCode 5`, `versionName "0.3.5"` → next tag `v0.3.5`.
> Signing, tagging and publishing are covered in
> [android-release-process.md](./android-release-process.md).

## What this build is

A **standalone** app. No account, no backend, no login — Room on the device is the
source of truth, and every workflow (notes, addendums, history, medication, AI summary,
care plan, backup) works with the network off.

AI is **bring-your-own-key**: you pick a provider in Settings → AI, paste your key, and
the app calls that provider directly from the device. Your key is encrypted in the
Android Keystore in its own per-provider slot and never reaches our servers. No key
means no AI — everything else still works.

## What's in this build

- **People** — care recipients with Arabic and English names, relationship, and a
  per-recipient medication list (`dosesPerDay`, one toggle per dose per day).
- **Notes** — structured observations (mood, appetite, sleep, mobility, medication, pain
  0–10, falls) plus free text in Arabic, English or mixed. Notes are append-only:
  a correction is an addendum, the original is never edited in place.
- **History** — filter by person and date.
- **Summary** — 7 / 14 / 30-day grounded summary, deterministic safety flags, trends and
  an explicit *uncertainty* section. Every claim cites a note and quotes it.
- **Plans** — AI-suggested care plan from a fixed action catalogue: accept, edit-and-accept
  or dismiss, versioned, with a closed transition table and previous versions archived.
- **Trends** — computed over the structured observations, not by the model.
- **Settings → AI** — provider, model (free model offered first where one exists), key,
  *Test connection*, and a clear consent dialog before the first send.
- **Data** — JSON backup/restore, CSV import/export, PDF summary export, and an optional
  outbox that syncs to a backend when one is configured.
- **Arabic / English** with `supportsRtl=true`, bidi isolation, and IBM Plex Sans Arabic.

## Verify on device (ar + en)

1. Install the APK from the release page on an arm64 device or an x86_64 emulator.
2. **Settings → AI** — pick a provider, paste a key, tap **Test connection**. Confirm the
   consent dialog appears on first send and that the key is never shown in full.
3. Add a person (Arabic + English name) → detail → add a medication with two doses a day.
4. Add a note with a fall and a pain score of 8 → **Save**. Saving must succeed with the
   device in airplane mode.
5. **History** — the new note appears; add an addendum and confirm the original is
   unchanged.
6. **Summary** — 7/14/30 days. The fall flag appears from the deterministic layer even
   with no key configured. With a key, the narrative cites note ids and quotes.
7. **Plans** — accept a suggestion, then accept a second one and confirm the first is
   archived rather than overwritten.
8. Switch the app language to Arabic — the layout mirrors, names stay in the right order,
   and quoted fragments are not reversed.

## Not in this build

- The APK needs no backend: the web client and the `notes/`, `recipient/` and `audit/` backend
  modules are in the repository, but they are server-side and are not part of the app.
- Play Store release — this line ships as a GitHub Release APK.
- The app is **not a medical device** and is not HIPAA-compliant.
