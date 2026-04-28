package com.spellapp.core.data.local

data class CatalogRecordDetailRow(
    val id: String,
    val uuid: String?,
    val packName: String,
    val packLabel: String,
    val recordType: String,
    val category: String?,
    val name: String,
    val level: Int?,
    val rarity: String?,
    val sourceTitle: String?,
    val sourceLicense: String?,
    val sourcePage: String?,
    val imagePath: String?,
    val imageMissing: Boolean,
    val automationStatus: String,
    val detailText: String,
    val normalizedJson: String,
    val rawJsonGzip: ByteArray,
)
