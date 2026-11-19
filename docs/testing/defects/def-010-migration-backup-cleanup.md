# DEF-010 — Legacy-migration backup of the main database is never deleted

- **Severity:** Minor (a stale backup of the legacy database lingers on device after a successful migration; privacy/hygiene issue, no data loss)
- **Component:** `android/.../data/accounts/LegacyDataMigrator.kt` (`backupFiles()`)
- **Environment:** Android JVM suite (Robolectric), `LegacyDataMigratorTest`
- **Discovered:** 2026-11-19, first full JVM suite execution after the import (`backupSurvivesSuccessAndIsDeletedOnNextLaunch` fails: `assertFalse(backup.exists())`)

## Root cause

Backups are created at `file.path + BACKUP_SUFFIX` (main db, `-wal`, `-shm`). Cleanup enumerated them with the suffix list `{LEGACY_DATABASE_NAME, WAL, SHM}` applied to a base path that **already ends in the database name** — producing `caregiver-dbcaregiver-db.migration-backup` for the main file. `cleanupBackupIfDue()` reported success but never deleted the real `caregiver-db.migration-backup`. `recoverIfInterrupted` uses the correct `{"", WAL, SHM}` pattern for the same paths, proving the intent.

## Fix

`backupFiles()` now uses `listOf("", WAL, SHM)`, mirroring the creation path and the recovery path.

## Verification

`LegacyDataMigratorTest` green including the backup-lifecycle test; full JVM suite green.

**Status: fixed.**
