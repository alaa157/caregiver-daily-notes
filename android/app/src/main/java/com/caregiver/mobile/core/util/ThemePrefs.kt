package com.caregiver.mobile.core.util

/**
 * Synchronous, in-process mirror of the persisted theme settings.
 *
 * The DataStore values in [SettingsStore][com.caregiver.mobile.data.SettingsStore]
 * stay the source of truth the UI reads. This mirror exists for process start:
 * the window colour must be chosen before the first Compose frame, but the
 * `themeMode` / `pureBlack` flows are collected asynchronously and the Android
 * resource qualifiers know nothing about the in-app preference. The mirror is
 * seeded once from DataStore in `CaregiverApp.onCreate` and refreshed by the
 * settings setters, so a launch frame can read a decided value with no
 * coroutine and no disk access. It is deliberately not persisted: a fresh
 * process starts at the defaults until it is seeded.
 */
object ThemePrefs {
    @Volatile
    private var mode: String? = null

    @Volatile
    private var pureBlack: Boolean = false

    /**
     * Seeds the mirror from a stored read. Idempotent: seeding the same values
     * twice leaves the same state. A null [mode] means no stored mode was
     * read — the current value is kept and [cachedOr] supplies the default,
     * so a failed read cannot replace a mirrored choice with a default.
     */
    fun seed(mode: String?, pureBlack: Boolean) {
        mode?.let { this.mode = it }
        this.pureBlack = pureBlack
    }

    /** The mirrored theme mode, or [default] when nothing has been seeded. */
    fun cachedOr(default: String): String = mode ?: default

    /** The mirrored pure-black flag; false until something is seeded. */
    fun cachedPureBlack(): Boolean = pureBlack

    /** Test hygiene: returns the mirror to its unseeded default state. */
    fun resetForTest() {
        mode = null
        pureBlack = false
    }
}
