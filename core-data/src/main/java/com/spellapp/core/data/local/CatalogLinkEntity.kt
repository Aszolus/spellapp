package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_links",
    indices = [
        Index("from_record_id", name = "index_catalog_links_from_record_id"),
        Index("to_record_id", name = "index_catalog_links_to_record_id"),
    ],
)
data class CatalogLinkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "from_record_id")
    val fromRecordId: String,
    @ColumnInfo(name = "to_uuid")
    val toUuid: String,
    @ColumnInfo(name = "to_record_id")
    val toRecordId: String?,
    @ColumnInfo(name = "link_type")
    val linkType: String,
    @ColumnInfo(name = "source_path")
    val sourcePath: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "resolved")
    val resolved: Boolean,
)
