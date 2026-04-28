package com.spellapp.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uuid_index")
data class CatalogUuidIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "record_type")
    val recordType: String,
    @ColumnInfo(name = "pack_name")
    val packName: String,
)
