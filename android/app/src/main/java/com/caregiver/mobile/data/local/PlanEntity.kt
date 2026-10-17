package com.caregiver.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val versionsJson: String,
    val updatedAt: Long,
)
