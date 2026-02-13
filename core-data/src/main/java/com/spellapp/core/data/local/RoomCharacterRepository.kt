package com.spellapp.core.data.local

import androidx.room.withTransaction
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCharacterRepository(
    private val database: SpellDatabase,
    private val characterDao: CharacterDao,
    private val preparedSlotDao: PreparedSlotDao,
    private val focusStateDao: FocusStateDao,
    private val sessionEventDao: SessionEventDao,
) : CharacterRepository {
    override fun observeCharacters(): Flow<List<CharacterProfile>> {
        return characterDao.observeCharacters().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCharacter(characterId: Long): CharacterProfile? {
        return characterDao.getCharacterById(characterId)?.toDomain()
    }

    override suspend fun upsertCharacter(character: CharacterProfile): Long {
        return characterDao.upsert(character.toEntity())
    }

    override suspend fun deleteCharacter(characterId: Long) {
        characterDao.deleteById(characterId)
    }

    override fun observePreparedSlots(
        characterId: Long,
        trackKey: String?,
    ): Flow<List<PreparedSlot>> {
        val source = if (trackKey == null) {
            preparedSlotDao.observeByCharacter(characterId)
        } else {
            preparedSlotDao.observeByCharacterAndTrack(
                characterId = characterId,
                trackKey = trackKey,
            )
        }
        return source.map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addPreparedSlot(
        characterId: Long,
        rank: Int,
        trackKey: String,
    ): Long {
        return database.withTransaction {
            val currentIndexes = preparedSlotDao.getSlotIndexesByCharacterAndRank(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
            )
            val nextIndex = nextAvailableSlotIndex(currentIndexes)
            preparedSlotDao.upsert(
                PreparedSlotEntity(
                    characterId = characterId,
                    trackKey = trackKey,
                    rank = rank,
                    slotIndex = nextIndex,
                    preparedSpellId = null,
                    isExpended = false,
                ),
            )
        }
    }

    override suspend fun removePreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
    ): Boolean {
        return database.withTransaction {
            preparedSlotDao.deleteByCharacterRankAndIndex(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
            ) > 0
        }
    }

    override suspend fun clearPreparedSlotSpell(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
    ): Boolean {
        return database.withTransaction {
            preparedSlotDao.clearPreparedSpell(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
            ) > 0
        }
    }

    override suspend fun assignSpellToPreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        spellId: String,
        trackKey: String,
    ) {
        database.withTransaction {
            val existing = preparedSlotDao.getByCharacterRankAndIndex(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
            )
            val target = if (existing != null) {
                existing.copy(
                    preparedSpellId = spellId,
                    isExpended = false,
                )
            } else {
                PreparedSlotEntity(
                    characterId = characterId,
                    trackKey = trackKey,
                    rank = rank,
                    slotIndex = slotIndex,
                    preparedSpellId = spellId,
                    isExpended = false,
                )
            }
            preparedSlotDao.upsert(target)
        }
    }

    override suspend fun castPreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
    ): Boolean {
        return database.withTransaction {
            val targetSlot = preparedSlotDao.getByCharacterRankAndIndex(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
            ) ?: return@withTransaction false

            val spellId = targetSlot.preparedSpellId ?: return@withTransaction false
            if (targetSlot.isExpended) {
                return@withTransaction false
            }

            val updateCount = preparedSlotDao.setExpendedState(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
                isExpended = true,
            )
            if (updateCount <= 0) {
                return@withTransaction false
            }

            sessionEventDao.insert(
                SessionEventEntity(
                    characterId = characterId,
                    type = SessionEventType.CAST_SPELL.name,
                    spellId = spellId,
                    spellRank = rank,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    metadataJson = SessionEventMetadata.castSpell(
                        slotIndex = slotIndex,
                        trackKey = trackKey,
                    ),
                ),
            )
            true
        }
    }

    override suspend fun undoLastCast(characterId: Long): Boolean {
        return database.withTransaction {
            val latestEvent = sessionEventDao.getLatestByCharacter(characterId)
                ?.takeIf { it.type == SessionEventType.CAST_SPELL.name }
                ?: return@withTransaction false

            val rank = latestEvent.spellRank ?: return@withTransaction false
            val slotIndex = SessionEventMetadata.slotIndexOrNull(latestEvent.metadataJson)
                ?: return@withTransaction false
            val trackKey = SessionEventMetadata.trackKeyOrDefault(latestEvent.metadataJson)

            val updateCount = preparedSlotDao.setExpendedState(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slotIndex,
                isExpended = false,
            )
            if (updateCount <= 0) {
                return@withTransaction false
            }

            sessionEventDao.insert(
                SessionEventEntity(
                    characterId = characterId,
                    type = SessionEventType.UNDO_LAST_ACTION.name,
                    spellId = latestEvent.spellId,
                    spellRank = latestEvent.spellRank,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    metadataJson = latestEvent.metadataJson,
                ),
            )
            true
        }
    }

    override fun observeFocusState(characterId: Long): Flow<FocusState?> {
        return focusStateDao.observeByCharacter(characterId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun upsertFocusState(state: FocusState) {
        focusStateDao.upsert(state.toEntity())
    }

    override fun observeSessionEvents(characterId: Long): Flow<List<SessionEvent>> {
        return sessionEventDao.observeByCharacter(characterId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun appendSessionEvent(event: SessionEvent): Long {
        return sessionEventDao.insert(event.toEntity())
    }

    private fun CharacterEntity.toDomain(): CharacterProfile {
        return CharacterProfile(
            id = id,
            name = name,
            level = level,
            characterClass = enumValueOrDefault(characterClass, CharacterClass.OTHER),
            keyAbility = enumValueOrDefault(keyAbility, AbilityScore.INTELLIGENCE),
            spellDc = spellDc,
            spellAttackModifier = spellAttackModifier,
            legacyTerminologyEnabled = legacyTerminologyEnabled,
        )
    }

    private fun CharacterProfile.toEntity(): CharacterEntity {
        return CharacterEntity(
            id = id,
            name = name,
            level = level,
            characterClass = characterClass.name,
            keyAbility = keyAbility.name,
            spellDc = spellDc,
            spellAttackModifier = spellAttackModifier,
            legacyTerminologyEnabled = legacyTerminologyEnabled,
        )
    }

    private fun PreparedSlotEntity.toDomain(): PreparedSlot {
        return PreparedSlot(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            rank = rank,
            slotIndex = slotIndex,
            preparedSpellId = preparedSpellId,
            isExpended = isExpended,
        )
    }

    private fun FocusStateEntity.toDomain(): FocusState {
        return FocusState(
            characterId = characterId,
            currentPoints = currentPoints,
            maxPoints = maxPoints,
        )
    }

    private fun FocusState.toEntity(): FocusStateEntity {
        return FocusStateEntity(
            characterId = characterId,
            currentPoints = currentPoints,
            maxPoints = maxPoints,
        )
    }

    private fun SessionEventEntity.toDomain(): SessionEvent {
        return SessionEvent(
            id = id,
            characterId = characterId,
            type = enumValueOrDefault(type, SessionEventType.CAST_SPELL),
            spellId = spellId,
            spellRank = spellRank,
            createdAtEpochMillis = createdAtEpochMillis,
            metadataJson = metadataJson,
        )
    }

    private fun SessionEvent.toEntity(): SessionEventEntity {
        return SessionEventEntity(
            id = id,
            characterId = characterId,
            type = type.name,
            spellId = spellId,
            spellRank = spellRank,
            createdAtEpochMillis = createdAtEpochMillis,
            metadataJson = metadataJson,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        rawValue: String,
        defaultValue: T,
    ): T {
        return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(defaultValue)
    }

    private fun nextAvailableSlotIndex(sortedIndexes: List<Int>): Int {
        var expected = 0
        for (value in sortedIndexes) {
            if (value != expected) {
                return expected
            }
            expected += 1
        }
        return expected
    }
}

private object SessionEventMetadata {
    private const val SLOT_INDEX_KEY = "\"slotIndex\""
    private const val TRACK_KEY = "\"trackKey\""
    private val slotIndexRegex = Regex("$SLOT_INDEX_KEY\\s*:\\s*(\\d+)")
    private val trackKeyRegex = Regex("$TRACK_KEY\\s*:\\s*\"([^\"]+)\"")

    fun castSpell(
        slotIndex: Int,
        trackKey: String,
    ): String {
        return "{$SLOT_INDEX_KEY:$slotIndex,$TRACK_KEY:\"$trackKey\"}"
    }

    fun slotIndexOrNull(metadataJson: String): Int? {
        val match = slotIndexRegex.find(metadataJson) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    fun trackKeyOrDefault(metadataJson: String): String {
        return trackKeyRegex.find(metadataJson)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: PreparedSlot.PRIMARY_TRACK_KEY
    }
}
