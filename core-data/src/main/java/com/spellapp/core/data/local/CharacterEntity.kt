package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "characters",
    indices = [
        Index("name"),
    ],
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val level: Int,
    val characterClass: String,
    val keyAbility: String,
    val spellDc: Int,
    val spellAttackModifier: Int,
    val legacyTerminologyEnabled: Boolean,
)
