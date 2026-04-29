package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_builder_assets",
    indices = [
        Index("builder_type", "category", name = "index_catalog_builder_assets_type_category"),
    ],
)
data class CatalogBuilderAssetEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "builder_type")
    val builderType: String,
    @ColumnInfo(name = "category")
    val category: String?,
    @ColumnInfo(name = "record_count")
    val recordCount: Int,
    @ColumnInfo(name = "payload_json_gzip")
    val payloadJsonGzip: ByteArray,
)
