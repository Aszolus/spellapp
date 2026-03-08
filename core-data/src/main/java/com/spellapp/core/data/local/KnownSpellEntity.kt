package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_spells",
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
        Index(value = ["characterId", "trackKey", "spellId"], unique = true),
    ],
)
data class KnownSpellEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val spellId: String,
)
