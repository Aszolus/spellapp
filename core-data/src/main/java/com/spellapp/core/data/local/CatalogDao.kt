package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Query
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT value FROM catalog_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadataValue(key: String): String?

    @Query("SELECT COUNT(*) FROM catalog_builder_assets")
    suspend fun getBuilderAssetCount(): Int

    @Query(
        """
        SELECT DISTINCT COALESCE(source_title, '')
        FROM catalog_record_summaries
        WHERE TRIM(COALESCE(source_title, '')) != ''
          AND pack_name IN (
              'classes',
              'ancestries',
              'heritages',
              'backgrounds',
              'classfeatures',
              'ancestryfeatures',
              'feats-srd'
          )
        ORDER BY COALESCE(source_title, '') ASC
        """,
    )
    suspend fun getAvailableBuilderSourceTitles(): List<String>

    @Query(
        """
        SELECT
            name,
            builder_type,
            category,
            record_count,
            payload_json_gzip
        FROM catalog_builder_assets
        WHERE name IN (:names)
        ORDER BY name ASC
        """,
    )
    suspend fun getCatalogBuilderAssets(names: List<String>): List<CatalogBuilderAssetEntity>

    @Query(
        """
        SELECT id, detail_text AS detailText
        FROM catalog_records
        WHERE id IN (:recordIds)
        """,
    )
    suspend fun getCatalogRecordTexts(recordIds: List<String>): List<CatalogRecordTextRow>

    @Query(
        """
        SELECT
            id,
            uuid,
            pack_name AS packName,
            record_type AS recordType,
            category,
            name,
            level,
            rarity,
            source_title AS sourceTitle,
            image_path AS imagePath,
            image_missing AS imageMissing,
            automation_status AS automationStatus
        FROM catalog_record_summaries
        WHERE (:recordType = '' OR record_type = :recordType)
          AND (:category = '' OR COALESCE(category, '') = :category)
          AND (:query = '' OR name LIKE '%' || :query || '%')
          AND (:sourceTitle = '' OR COALESCE(source_title, '') = :sourceTitle)
          AND (:rarity = '' OR LOWER(COALESCE(rarity, '')) = LOWER(:rarity))
          AND (:maxLevel IS NULL OR level IS NULL OR level <= :maxLevel)
        ORDER BY
            level ASC,
            name ASC
        LIMIT :limit
        """,
    )
    fun observeCatalogRecordSummaries(
        recordType: String,
        category: String,
        query: String,
        sourceTitle: String,
        rarity: String,
        maxLevel: Int?,
        limit: Int,
    ): Flow<List<CatalogRecordSummaryRow>>

    @Query(
        """
        SELECT
            id,
            uuid,
            pack_name AS packName,
            pack_label AS packLabel,
            record_type AS recordType,
            category,
            name,
            level,
            rarity,
            source_title AS sourceTitle,
            source_license AS sourceLicense,
            source_page AS sourcePage,
            image_path AS imagePath,
            image_missing AS imageMissing,
            automation_status AS automationStatus,
            detail_text AS detailText,
            normalized_json AS normalizedJson,
            raw_json_gzip AS rawJsonGzip
        FROM catalog_records
        WHERE id = :recordIdOrUuid
           OR uuid = :recordIdOrUuid
           OR id = (
               SELECT record_id FROM uuid_index
               WHERE uuid = :recordIdOrUuid
               LIMIT 1
           )
        LIMIT 1
        """,
    )
    suspend fun getCatalogRecordDetail(recordIdOrUuid: String): CatalogRecordDetailRow?

    @Query(
        """
        SELECT
            l.from_record_id AS fromRecordId,
            l.to_uuid AS toUuid,
            l.to_record_id AS toRecordId,
            l.link_type AS linkType,
            l.source_path AS sourcePath,
            l.label AS label,
            l.resolved AS resolved,
            target.name AS relatedName,
            target.record_type AS relatedRecordType
        FROM catalog_links l
        LEFT JOIN catalog_records target ON target.id = l.to_record_id
        WHERE l.from_record_id = :recordIdOrUuid
           OR l.from_record_id = (
               SELECT record_id FROM uuid_index
               WHERE uuid = :recordIdOrUuid
               LIMIT 1
           )
           OR l.from_record_id = (
               SELECT id FROM catalog_records
               WHERE uuid = :recordIdOrUuid
               LIMIT 1
           )
        ORDER BY COALESCE(l.label, target.name, l.to_uuid) COLLATE NOCASE ASC
        """,
    )
    suspend fun getCatalogLinksFromRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow>

    @Query(
        """
        SELECT
            l.from_record_id AS fromRecordId,
            l.to_uuid AS toUuid,
            l.to_record_id AS toRecordId,
            l.link_type AS linkType,
            l.source_path AS sourcePath,
            l.label AS label,
            l.resolved AS resolved,
            source.name AS relatedName,
            source.record_type AS relatedRecordType
        FROM catalog_links l
        LEFT JOIN catalog_records source ON source.id = l.from_record_id
        WHERE l.to_record_id = :recordIdOrUuid
           OR l.to_uuid = :recordIdOrUuid
           OR l.to_record_id = (
               SELECT record_id FROM uuid_index
               WHERE uuid = :recordIdOrUuid
               LIMIT 1
           )
           OR l.to_record_id = (
               SELECT id FROM catalog_records
               WHERE uuid = :recordIdOrUuid
               LIMIT 1
           )
        ORDER BY COALESCE(source.name, l.from_record_id) COLLATE NOCASE ASC
        """,
    )
    suspend fun getCatalogBacklinksToRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow>

    @Query("SELECT COUNT(*) FROM catalog_spell_index")
    suspend fun getSpellIndexCount(): Int

    @Query(
        """
        SELECT DISTINCT COALESCE(source_book, '') FROM catalog_spell_list
        WHERE TRIM(COALESCE(source_book, '')) != ''
        ORDER BY COALESCE(source_book, '') ASC
        """,
    )
    fun observeAvailableSpellSources(): Flow<List<String>>

    @Query(
        """
        SELECT traits_csv FROM catalog_spell_list
        WHERE TRIM(traits_csv) != ''
        """,
    )
    fun observeSpellTraitRows(): Flow<List<String>>

    @Query(
        """
        SELECT
            spell_id AS id,
            name AS name,
            rank AS rank,
            tradition_summary AS tradition,
            rarity AS rarity,
            source_book AS sourceBook,
            is_cantrip AS isCantrip
        FROM catalog_spell_list
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
          AND (:rank IS NULL OR rank = :rank)
          AND (
              :tradition = ''
              OR LOWER(traditions_csv) = LOWER(:tradition)
              OR LOWER(traditions_csv) LIKE LOWER(:tradition) || ',%'
              OR LOWER(traditions_csv) LIKE '%,' || LOWER(:tradition)
              OR LOWER(traditions_csv) LIKE '%,' || LOWER(:tradition) || ',%'
          )
          AND (:rarity = '' OR LOWER(COALESCE(rarity, '')) = LOWER(:rarity))
          AND (
              :trait = ''
              OR LOWER(traits_csv) = LOWER(:trait)
              OR LOWER(traits_csv) LIKE LOWER(:trait) || ',%'
              OR LOWER(traits_csv) LIKE '%,' || LOWER(:trait)
              OR LOWER(traits_csv) LIKE '%,' || LOWER(:trait) || ',%'
          )
        ORDER BY rank ASC, name ASC
        """,
    )
    fun observeSpellList(
        query: String,
        rank: Int?,
        tradition: String,
        rarity: String,
        trait: String,
    ): Flow<List<SpellListItem>>

    @Query(
        """
        SELECT spell_id AS id, rank
        FROM catalog_spell_list
        WHERE spell_id IN (:spellIds)
        """,
    )
    suspend fun getSpellRanks(spellIds: List<String>): List<SpellRankRow>

    @Query(
        """
        SELECT
            s.spell_id AS id,
            r.name AS name,
            s.rank AS rank,
            s.tradition_summary AS traditionSummary,
            COALESCE(r.rarity, '') AS rarity,
            s.traits_csv AS traitsCsv,
            i.cast_time AS castTime,
            i.range_text AS rangeText,
            i.target_text AS targetText,
            i.duration_text AS durationText,
            i.area_text AS areaText,
            i.defense_text AS defenseText,
            r.detail_text AS description,
            COALESCE(r.source_license, '') AS license,
            COALESCE(r.source_title, '') AS sourceBook,
            r.source_page AS sourcePageText
        FROM catalog_spell_list s
        INNER JOIN catalog_spell_index i ON i.record_id = s.record_id
        INNER JOIN catalog_records r ON r.id = s.record_id
        WHERE s.spell_id IN (:spellIds)
        """,
    )
    suspend fun getSpellDetails(spellIds: List<String>): List<CatalogSpellDetailRow>

    @Query(
        """
        SELECT
            s.spell_id AS id,
            r.name AS name,
            s.rank AS rank,
            REPLACE(s.traditions_csv, ',', ', ') AS traditionSummary,
            COALESCE(r.rarity, '') AS rarity,
            s.traits_csv AS traitsCsv,
            s.cast_time AS castTime,
            s.range_text AS rangeText,
            s.target_text AS targetText,
            s.duration_text AS durationText,
            s.area_text AS areaText,
            s.defense_text AS defenseText,
            r.detail_text AS description,
            COALESCE(r.source_license, '') AS license,
            COALESCE(r.source_title, '') AS sourceBook,
            r.source_page AS sourcePageText
        FROM catalog_spell_index s
        INNER JOIN catalog_records r ON r.id = s.record_id
        WHERE s.spell_id = :spellId OR s.record_id = :spellId
        LIMIT 1
        """,
    )
    suspend fun getSpellDetail(spellId: String): CatalogSpellDetailRow?
}
