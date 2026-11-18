package com.caregiver.mobile.presentation.auth

import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountDao
import com.caregiver.mobile.data.accounts.toAccount

/** Read-only account list for the local login screen's switch entry (#607). */
interface LocalAccountDirectory {
    suspend fun listAccounts(): List<Account>
}

class RoomLocalAccountDirectory(private val accountDao: AccountDao) : LocalAccountDirectory {
    override suspend fun listAccounts(): List<Account> = accountDao.listAll().map { it.toAccount() }
}
