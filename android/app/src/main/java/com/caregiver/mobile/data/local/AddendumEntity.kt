package com.caregiver.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.caregiver.mobile.data.api.AddendumDto

@Entity(tableName = "addenda")
data class AddendumEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val createdAt: String,
    val text: String,
)

fun AddendumEntity.toDto(): AddendumDto =
    AddendumDto(id = id, noteId = noteId, createdAt = createdAt, text = text)

fun AddendumDto.toEntity(): AddendumEntity =
    AddendumEntity(id = id, noteId = noteId, createdAt = createdAt, text = text)