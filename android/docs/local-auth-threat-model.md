# Local-auth threat model

Status: informational. This document describes the standalone Android app's
LOCAL_MODE local accounts as they are implemented for epic #581. It records
what the design does and does not protect; it does not add new guarantees.
For the storage layout decision, see
[ADR 001: Per-account databases](adr/001-per-account-databases.md).

## What a local account is

A local account is an email-style login key plus a password that gates the app
UI and selects one account's data file. There is no server, no remote
authentication, and no escrowed recovery key. The password is a *login* factor,
not a data-encryption key.

The implementation facts this model relies on:

- `accounts.db` stores only a password **hash**, never the password. Hashing is
  PBKDF2-HMAC-SHA256 with 600,000 iterations, a fresh 16-byte `SecureRandom`
  salt per account, and a 256-bit derived key. The stored iteration count is
  per row so costs can be raised later without locking accounts out. Verification
  uses a constant-time comparison over decoded bytes, and `HashedPassword`'s
  `toString()` is redacted so hashes and salts do not reach logs.
- `PasswordPolicy` requires 8–128 Unicode code points with at least one letter
  and one digit. `SignInThrottle` locks an email after 5 consecutive failures
  with a growing delay capped at 5 minutes, persisting only a counter and a
  deadline. Both raise the cost of guessing; neither protects stored data.
- Care data lives in `notes-<accountId>.db`; the `session` preferences hold only
  an opaque active-account id. Account separation is per file, so switching
  accounts swaps databases rather than filtering rows.
- BYOK provider keys are a separate concern: they are encrypted with a
  per-account key in the Android Keystore (`AndroidKeyStoreCipher`) and excluded
  from backup rules. Keystore protection of API keys must not be mistaken for
  encryption of the note databases.

## The central limitation

**The databases are not encrypted at rest.** `accounts.db` and every
`notes-<accountId>.db` are ordinary unencrypted SQLite/Room files. Anyone who
can read the app's files can read recipients, notes, care plans, and summaries
directly — without knowing any account's password and without going through the
login flow. The password protects the login flow; it does not protect the data
at rest.

The app declares `android:allowBackup="false"` and excludes AI settings from
backup/device-transfer rules, which reduces ordinary backup exposure. That is a
configuration choice, not a cryptographic boundary: on a rooted device the
files can be read regardless, and backup/extraction tooling on affected Android
versions, OEM tooling, or custom ROMs can copy app data off the device. ADB
backup extraction is therefore in scope as an adversary, not something this
design rules out.

## Adversary 1: local attacker with the unlocked device

Someone who can use the unlocked app (a family member, a coworker, a caregiver
sharing the phone) can try passwords, but:

- the stored hash is slow and salted, so offline guesses from a copied hash are
  expensive, and the sign-in throttle limits online guessing;
- accounts are separated by database file, so signing in as one account does
  not expose another account's data through the app;
- AI keys are per account and stored in the Keystore, not in a database row.

What this adversary still gets: if they can reach the app's files (for example
via a file manager with the right permissions, a debug build, or a connected
workstation), they read the notes directly. The login screen is a casual
privacy boundary against someone casually opening the app, not a lock on the
data files.

## Adversary 2: device loss or theft

The thief gets the physical device and any files on it. The local account does
not encrypt those files, so device storage controls (Android's full-disk or
file-based encryption tied to the device lock screen) are the only thing
standing between the thief and the note databases while the device is off.
Once the device is unlocked — or if it is rooted — the app's password adds no
protection to the data at rest. There is no remote wipe, no server-side revocation,
and no recovery key for a forgotten local password.

The practical claim is therefore narrow: losing the device is no worse than
losing any other unencrypted file on it, and the app should not be presented as
protecting notes from a determined device holder.

## Adversary 3: backup or filesystem extraction

An attacker with filesystem-level access — root, a custom recovery, `adb backup`
where the platform or tooling permits it, or an extracted image — can copy the
database files and open them with any SQLite tool. This bypasses the password
entirely: hashes and salts can be read but are not needed to read the notes.
`allowBackup="false"` and the AI-settings backup exclusions reduce the chance
that this happens through a normal backup path; they do not make extraction
impossible, and they do not protect care data if the files are obtained some
other way.

## What is protected, and what is not

| Protected by the local-account design | Not protected |
| --- | --- |
| The plaintext password is never stored; only a slow, salted, constant-time-verified hash. | The note databases at rest — SQLite files are readable without a password. |
| Online guessing is throttled, and policy rejects weak passwords. | Filesystem access from root, ADB backup, or an extracted image. |
| One account's data is in its own database file; switching accounts swaps files. | A device thief who can unlock or root the device. |
| BYOK keys are encrypted in the Android Keystore and scoped per account. | Backup/device-transfer guarantees beyond the declared `allowBackup` and exclusion rules. |

## Implication for the caregiver-facing claim

Local accounts are a **convenience feature and a casual-privacy boundary**, not
encryption. Copy that wording into user-facing text: the login screen keeps a
casual user from opening someone else's notes, and separate accounts keep each
caregiver's data in its own file, but it does not make stored notes private from
someone who has the device's files. Do not describe notes as "encrypted",
"secure at rest", or "password-protected on disk" — none of those are true
today. The accurate statement is that the password protects the login flow,
not the data at rest.

## Future work (recommended, not implemented)

- **SQLCipher or equivalent** to encrypt `notes-<accountId>.db` (and, where
  needed, `accounts.db`) at rest, with keys held in the Android Keystore. This
  would be the actual fix for the limitation above; it needs a key-derivation
  and migration plan for existing unencrypted databases and a decision on what
  happens when the Keystore key is invalidated.
- A documented credential-recovery story, since a forgotten local password
  currently has no recovery path.
- Revisit backup and export rules once encrypted database files exist, so that
  encrypted files are backed up or restored consistently with the keys that
  open them.
- Keep this document in sync with the storage and crypto code; if any of the
  statements above stop being true, update it in the same change.
