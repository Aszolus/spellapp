package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_record_summaries",
    indices = [
        Index("record_type", "name", name = "index_catalog_record_summaries_type_name"),
        Index("level", "name", name = "index_catalog_record_summaries_level_name"),
    ],
)
data class CatalogRecordSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "uuid")
    val uuid: String?,
    @ColumnInfo(name = "pack_name")
    val packName: String,
    @ColumnInfo(name = "record_type")
    val recordType: String,
    @ColumnInfo(name = "category")
    val category: String?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "level")
    val level: Int?,
    @ColumnInfo(name = "rarity")
    val rarity: String?,
    @ColumnInfo(name = "source_title")
    val sourceTitle: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "image_missing")
    val imageMissing: Boolean,
    @ColumnInfo(name = "automation_status")
    val automationStatus: String,
)
