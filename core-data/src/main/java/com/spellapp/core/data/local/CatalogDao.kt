package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Query
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT value FROM catalog_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadataValue(key: String): String?

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
        FROM catalog_records
        WHERE (:recordType = '' OR record_type = :recordType)
          AND (:category = '' OR COALESCE(category, '') = :category)
          AND (:query = '' OR name LIKE '%' || :query || '%' OR detail_text LIKE '%' || :query || '%')
          AND (:sourceTitle = '' OR COALESCE(source_title, '') = :sourceTitle)
          AND (:rarity = '' OR LOWER(COALESCE(rarity, '')) = LOWER(:rarity))
          AND (:maxLevel IS NULL OR level IS NULL OR level <= :maxLevel)
        ORDER BY
            CASE WHEN level IS NULL THEN 0 ELSE 1 END ASC,
            level ASC,
            name COLLATE NOCASE ASC
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
        SELECT DISTINCT COALESCE(r.source_title, '') FROM catalog_spell_index s
        INNER JOIN catalog_records r ON r.id = s.record_id
        WHERE TRIM(COALESCE(r.source_title, '')) != ''
        ORDER BY COALESCE(r.source_title, '') ASC
        """,
    )
    fun observeAvailableSpellSources(): Flow<List<String>>

    @Query(
        """
        SELECT traits_csv FROM catalog_spell_index
        WHERE TRIM(traits_csv) != ''
        """,
    )
    fun observeSpellTraitRows(): Flow<List<String>>

    @Query(
        """
        SELECT
            s.spell_id AS id,
            r.name AS name,
            s.rank AS rank,
            REPLACE(s.traditions_csv, ',', ', ') AS tradition,
            COALESCE(r.rarity, '') AS rarity,
            COALESCE(r.source_title, '') AS sourceBook,
            (s.rank = 0) AS isCantrip
        FROM catalog_spell_index s
        INNER JOIN catalog_records r ON r.id = s.record_id
        WHERE (:query = '' OR r.name LIKE '%' || :query || '%')
          AND (:rank IS NULL OR s.rank = :rank)
          AND (
              :tradition = ''
              OR LOWER(s.traditions_csv) = LOWER(:tradition)
              OR LOWER(s.traditions_csv) LIKE LOWER(:tradition) || ',%'
              OR LOWER(s.traditions_csv) LIKE '%,' || LOWER(:tradition)
              OR LOWER(s.traditions_csv) LIKE '%,' || LOWER(:tradition) || ',%'
          )
          AND (:rarity = '' OR LOWER(COALESCE(r.rarity, '')) = LOWER(:rarity))
          AND (
              :trait = ''
              OR LOWER(s.traits_csv) = LOWER(:trait)
              OR LOWER(s.traits_csv) LIKE LOWER(:trait) || ',%'
              OR LOWER(s.traits_csv) LIKE '%,' || LOWER(:trait)
              OR LOWER(s.traits_csv) LIKE '%,' || LOWER(:trait) || ',%'
          )
        ORDER BY s.rank ASC, r.name ASC
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
