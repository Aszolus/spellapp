package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Query
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT value FROM catalog_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadataValue(key: String): String?

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
