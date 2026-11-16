package com.caregiver.mobile.data.accounts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room access for the accounts table in `accounts.db`.
 *
 * The unique index on [AccountEntity.email] is the final guard against
 * duplicate accounts: a concurrent sign-up race surfaces as a SQLite
 * constraint violation for the caller to map to `EmailTaken`, never a
 * silent overwrite — so inserts use [OnConflictStrategy.ABORT].
 */
@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE email = :email")
    suspend fun findByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: String): AccountEntity?

    /** Most recently used first — the order the switcher shows accounts in. */
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC")
    suspend fun listAll(): List<AccountEntity>

    @Query("UPDATE accounts SET lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateLastUsed(id: String, lastUsedAt: Long): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
