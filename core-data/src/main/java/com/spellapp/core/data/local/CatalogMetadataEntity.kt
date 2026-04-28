package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)
