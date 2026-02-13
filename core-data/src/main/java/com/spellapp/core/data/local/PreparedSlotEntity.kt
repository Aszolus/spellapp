package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prepared_slots",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "trackKey", "rank", "slotIndex"], unique = true),
    ],
)
data class PreparedSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String = "primary",
    val rank: Int,
    val slotIndex: Int,
    val preparedSpellId: String?,
    val isExpended: Boolean,
)
