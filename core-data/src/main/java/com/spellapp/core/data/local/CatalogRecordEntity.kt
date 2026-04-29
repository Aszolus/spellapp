package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_records")
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
