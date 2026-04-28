package com.spellapp.core.data.local

data class CatalogRecordSummaryRow(
    val id: String,
    val uuid: String?,
    val packName: String,
    val recordType: String,
    val category: String?,
    val name: String,
    val level: Int?,
    val rarity: String?,
    val sourceTitle: String?,
    val imagePath: String?,
    val imageMissing: Boolean,
    val automationStatus: String,
)
