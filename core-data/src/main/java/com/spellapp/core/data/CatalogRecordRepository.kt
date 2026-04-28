package com.spellapp.core.data

import kotlinx.coroutines.flow.Flow

interface CatalogRecordRepository {
    fun observeRecords(query: CatalogRecordQuery = CatalogRecordQuery()): Flow<List<CatalogRecordSummary>>
    suspend fun getRecordDetail(recordIdOrUuid: String): CatalogRecordDetail?
    suspend fun getRecordLinks(recordIdOrUuid: String): List<CatalogRecordLink>
    suspend fun getRecordBacklinks(recordIdOrUuid: String): List<CatalogRecordLink>
}

data class CatalogRecordQuery(
    val recordType: String? = null,
    val category: String? = null,
    val text: String? = null,
    val sourceTitle: String? = null,
    val rarity: String? = null,
    val maxLevel: Int? = null,
    val limit: Int = 100,
)

data class CatalogRecordSummary(
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
    val automationStatus: CatalogAutomationStatus,
)

data class CatalogRecordDetail(
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
    val automationStatus: CatalogAutomationStatus,
    val detailText: String,
    val normalizedJson: String,
    val rawJsonGzip: ByteArray,
)

data class CatalogRecordLink(
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

enum class CatalogAutomationStatus(
    val storedValue: String,
) {
    AUTOMATED("automated"),
    PARTIALLY_AUTOMATED("partially_automated"),
    REFERENCE_ONLY("reference_only"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromStoredValue(value: String?): CatalogAutomationStatus {
            return entries.firstOrNull { status -> status.storedValue == value } ?: UNKNOWN
        }
    }
}

object CatalogRecordTypes {
    const val ACTION = "action"
    const val ANCESTRY = "ancestry"
    const val BACKGROUND = "background"
    const val CLASS = "class"
    const val CONDITION = "condition"
    const val DEITY = "deity"
    const val EFFECT = "effect"
    const val EQUIPMENT = "equipment"
    const val FEAT = "feat"
    const val HERITAGE = "heritage"
    const val SPELL = "spell"
}
