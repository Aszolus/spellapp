package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_records",
    indices = [
        Index("name", name = "index_catalog_records_name"),
        Index("record_type", "name", name = "index_catalog_records_type_name"),
        Index("pack_name", "name", name = "index_catalog_records_pack_name"),
        Index("category", "name", name = "index_catalog_records_category_name"),
        Index("level", name = "index_catalog_records_level"),
        Index("automation_status", name = "index_catalog_records_automation"),
    ],
)
data class CatalogRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "uuid")
    val uuid: String?,
    @ColumnInfo(name = "pack_name")
    val packName: String,
    @ColumnInfo(name = "pack_label")
    val packLabel: String,
    @ColumnInfo(name = "pack_path")
    val packPath: String,
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
    @ColumnInfo(name = "source_license")
    val sourceLicense: String?,
    @ColumnInfo(name = "source_page")
    val sourcePage: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "image_missing")
    val imageMissing: Boolean,
    @ColumnInfo(name = "automation_status")
    val automationStatus: String,
    @ColumnInfo(name = "detail_text")
    val detailText: String,
    @ColumnInfo(name = "raw_json_gzip")
    val rawJsonGzip: ByteArray,
    @ColumnInfo(name = "normalized_json")
    val normalizedJson: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
)
