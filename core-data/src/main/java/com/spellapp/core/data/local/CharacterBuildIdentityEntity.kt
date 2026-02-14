package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "character_build_identity",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["characterId"],
)
data class CharacterBuildIdentityEntity(
    val characterId: Long,
    val ancestryId: String?,
    val heritageId: String?,
    val backgroundId: String?,
)
