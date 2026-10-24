package com.caregiver.mobile.data.local

import com.caregiver.mobile.data.api.RecipientDto

/**
 * Local-only recipient archive/delete administration. Implemented by the
 * standalone Room-backed graph and the in-memory demo graph; graphs that do
 * not own a local database do not implement it, so the UI hides the actions.
 */
interface RecipientAdmin {
    suspend fun archive(id: String): RecipientDto

    suspend fun unarchive(id: String): RecipientDto

    suspend fun deleteArchived(id: String)

    suspend fun counts(id: String): RecipientDeletionCounts
}

/** Deleting a recipient that is not archived is never allowed. */
class ActiveRecipientDeleteException(val recipientId: String) :
    IllegalStateException("Recipient $recipientId is not archived")

/** A write targeted an archived recipient; archived records are read-only. */
class ArchivedRecipientException(val recipientId: String) :
    IllegalStateException("Recipient $recipientId is archived")
