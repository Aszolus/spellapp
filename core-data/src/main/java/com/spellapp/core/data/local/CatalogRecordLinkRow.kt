package com.spellapp.core.data.local

data class CatalogRecordLinkRow(
    val fromRecordId: String,
    val toUuid: String,
    val toRecordId: String?,
    val linkType: String,
    val sourcePath: String,
    val label: String?,
    val resolved: Boolean,
    val relatedName: String?,
    val relatedRecordType: String?,
)
