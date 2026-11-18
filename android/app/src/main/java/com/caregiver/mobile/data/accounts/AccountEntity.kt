package com.caregiver.mobile.data.accounts

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per local caregiver account in `accounts.db` (epic #581).
 *
 * This table holds credentials only — recipients, notes, plans and every
 * other domain row live in that account's own database file, so nothing
 * here ever carries care data. [passwordHash] and [salt] are sensitive:
 * the auto-generated `toString` would print them, so it is overridden to
 * leave them out.
 *
 * [kdfIterations] is stored per row so the PBKDF2 cost can be raised later
 * with rehash-on-login. [createdAt] and [lastUsedAt] are epoch millis,
 * consistent with the rest of the app.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["email"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val salt: String,
    val kdfIterations: Int,
    val createdAt: Long,
    val lastUsedAt: Long,
) {
    override fun toString(): String =
        "AccountEntity(id=$id, email=$email, displayName=$displayName, " +
            "createdAt=$createdAt, lastUsedAt=$lastUsedAt)"
}
