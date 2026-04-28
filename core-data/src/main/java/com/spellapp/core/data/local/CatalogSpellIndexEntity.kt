package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_spell_index",
    indices = [
        Index("spell_id", name = "index_catalog_spell_index_spell_id"),
        Index("rank", name = "index_catalog_spell_index_rank"),
        Index("traditions_csv", name = "index_catalog_spell_index_traditions"),
        Index("traits_csv", name = "index_catalog_spell_index_traits"),
    ],
)
data class CatalogSpellIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "spell_id")
    val spellId: String,
    @ColumnInfo(name = "rank")
    val rank: Int,
    @ColumnInfo(name = "traditions_csv")
    val traditionsCsv: String,
    @ColumnInfo(name = "traits_csv")
    val traitsCsv: String,
    @ColumnInfo(name = "cast_time")
    val castTime: String,
    @ColumnInfo(name = "range_text")
    val rangeText: String,
    @ColumnInfo(name = "target_text")
    val targetText: String,
    @ColumnInfo(name = "duration_text")
    val durationText: String,
    @ColumnInfo(name = "area_text")
    val areaText: String?,
    @ColumnInfo(name = "defense_text")
    val defenseText: String?,
)
