package com.spellapp.core.data.local

import com.spellapp.core.data.CatalogAutomationStatus
import com.spellapp.core.data.CatalogRecordDetail
import com.spellapp.core.data.CatalogRecordLink
import com.spellapp.core.data.CatalogRecordQuery
import com.spellapp.core.data.CatalogRecordRepository
import com.spellapp.core.data.CatalogRecordSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCatalogRecordRepository(
    private val catalogDao: CatalogDao,
) : CatalogRecordRepository {
    override fun observeRecords(query: CatalogRecordQuery): Flow<List<CatalogRecordSummary>> {
        return catalogDao.observeCatalogRecordSummaries(
            recordType = query.recordType.cleaned(),
            category = query.category.cleaned(),
            query = query.text.cleaned(),
            sourceTitle = query.sourceTitle.cleaned(),
            rarity = query.rarity.cleaned(),
            maxLevel = query.maxLevel,
            limit = query.limit.coerceIn(1, MAX_QUERY_LIMIT),
        ).map { rows -> rows.map { row -> row.toCatalogRecordSummary() } }
    }

    override suspend fun getRecordDetail(recordIdOrUuid: String): CatalogRecordDetail? {
        return catalogDao.getCatalogRecordDetail(recordIdOrUuid.trim())?.toCatalogRecordDetail()
    }

    override suspend fun getRecordLinks(recordIdOrUuid: String): List<CatalogRecordLink> {
        return catalogDao.getCatalogLinksFromRecord(recordIdOrUuid.trim())
            .map { row -> row.toCatalogRecordLink() }
    }

    override suspend fun getRecordBacklinks(recordIdOrUuid: String): List<CatalogRecordLink> {
        return catalogDao.getCatalogBacklinksToRecord(recordIdOrUuid.trim())
            .map { row -> row.toCatalogRecordLink() }
    }

    private fun CatalogRecordSummaryRow.toCatalogRecordSummary(): CatalogRecordSummary {
        return CatalogRecordSummary(
            id = id,
            uuid = uuid,
            packName = packName,
            recordType = recordType,
            category = category,
            name = name,
            level = level,
            rarity = rarity,
            sourceTitle = sourceTitle,
            imagePath = imagePath,
            imageMissing = imageMissing,
            automationStatus = CatalogAutomationStatus.fromStoredValue(automationStatus),
        )
    }

    private fun CatalogRecordDetailRow.toCatalogRecordDetail(): CatalogRecordDetail {
        return CatalogRecordDetail(
            id = id,
            uuid = uuid,
            packName = packName,
            packLabel = packLabel,
            recordType = recordType,
            category = category,
            name = name,
            level = level,
            rarity = rarity,
            sourceTitle = sourceTitle,
            sourceLicense = sourceLicense,
            sourcePage = sourcePage,
            imagePath = imagePath,
            imageMissing = imageMissing,
            automationStatus = CatalogAutomationStatus.fromStoredValue(automationStatus),
            detailText = detailText,
            normalizedJson = normalizedJson,
            rawJsonGzip = rawJsonGzip,
        )
    }

    private fun CatalogRecordLinkRow.toCatalogRecordLink(): CatalogRecordLink {
        return CatalogRecordLink(
            fromRecordId = fromRecordId,
            toUuid = toUuid,
            toRecordId = toRecordId,
            linkType = linkType,
            sourcePath = sourcePath,
            label = label,
            resolved = resolved,
            relatedName = relatedName,
            relatedRecordType = relatedRecordType,
        )
    }

    private fun String?.cleaned(): String = orEmpty().trim()

    private companion object {
        private const val MAX_QUERY_LIMIT = 500
    }
}
