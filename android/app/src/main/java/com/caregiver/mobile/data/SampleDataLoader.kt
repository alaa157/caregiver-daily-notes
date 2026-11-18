package com.caregiver.mobile.data

import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.AppDatabase
import com.caregiver.mobile.data.local.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object SampleDataLoader {
    suspend fun load(db: AppDatabase, dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext(dispatcher) {
        val dao = db.recipientDao()
        if (dao.all().isNotEmpty()) return@withContext
        val names = listOf("Sample Recipient A", "Sample Recipient B")
        dao.insertAll(names.map { name ->
            RecipientDto(id = UUID.randomUUID().toString(), name = name, active = true).toEntity()
        })
    }
}
