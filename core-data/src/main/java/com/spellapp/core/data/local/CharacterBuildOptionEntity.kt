package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "character_build_options",
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
        Index(value = ["characterId", "optionType"]),
        Index(value = ["characterId", "optionType", "optionId"], unique = true),
    ],
)
data class CharacterBuildOptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterId: Long,
    val optionType: String,
    val optionId: String,
    val levelAcquired: Int?,
    val metadataJson: String,
)
