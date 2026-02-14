package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "casting_tracks",
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
        Index(value = ["characterId", "trackKey"], unique = true),
    ],
)
data class CastingTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val sourceType: String,
    val sourceId: String,
    val progressionType: String,
)
