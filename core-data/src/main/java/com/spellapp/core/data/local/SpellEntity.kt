package com.spellapp.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spells",
    indices = [
        Index("name"),
        Index("rank"),
    ],
)
data class SpellEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val rank: Int,
    val traditionSummary: String,
    val rarity: String,
    val traitsCsv: String,
    val castTime: String,
    val rangeText: String,
    val targetText: String,
    val durationText: String,
    val areaText: String?,
    val defenseText: String?,
    val description: String,
    val license: String,
    val sourceBook: String,
    val sourcePage: Int?,
)
