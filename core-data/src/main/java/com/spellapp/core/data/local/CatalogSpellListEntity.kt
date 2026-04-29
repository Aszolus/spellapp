package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_spell_list",
    indices = [
        Index("rank", "name", name = "index_catalog_spell_list_rank_name"),
    ],
)
data class CatalogSpellListEntity(
    @PrimaryKey
    @ColumnInfo(name = "spell_id")
    val spellId: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "rank")
    val rank: Int,
    @ColumnInfo(name = "traditions_csv")
    val traditionsCsv: String,
    @ColumnInfo(name = "tradition_summary")
    val traditionSummary: String,
    @ColumnInfo(name = "traits_csv")
    val traitsCsv: String,
    @ColumnInfo(name = "rarity")
    val rarity: String,
    @ColumnInfo(name = "source_book")
    val sourceBook: String,
    @ColumnInfo(name = "is_cantrip")
    val isCantrip: Boolean,
)
