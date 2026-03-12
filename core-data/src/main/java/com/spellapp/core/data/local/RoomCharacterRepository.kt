package com.spellapp.core.data.local

import androidx.room.withTransaction
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterBuildIdentity
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCharacterRepository private constructor(
    private val database: SpellDatabase,
    private val characterDao: CharacterDao,
    private val characterBuildIdentityDao: CharacterBuildIdentityDao,
    private val characterBuildOptionDao: CharacterBuildOptionDao,
    private val preparedSlotDao: PreparedSlotDao,
    private val castingTrackDao: CastingTrackDao,
    private val focusStateDao: FocusStateDao,
    private val sessionEventDao: SessionEventDao,
    private val slotProgressionEngine: SlotProgressionEngine,
) : CharacterRepository {
    constructor(
        database: SpellDatabase,
        characterDao: CharacterDao,
        characterBuildIdentityDao: CharacterBuildIdentityDao,
        characterBuildOptionDao: CharacterBuildOptionDao,
        preparedSlotDao: PreparedSlotDao,
        castingTrackDao: CastingTrackDao,
        focusStateDao: FocusStateDao,
        sessionEventDao: SessionEventDao,
    ) : this(
        database = database,
        characterDao = characterDao,
        characterBuildIdentityDao = characterBuildIdentityDao,
        characterBuildOptionDao = characterBuildOptionDao,
        preparedSlotDao = preparedSlotDao,
        castingTrackDao = castingTrackDao,
        focusStateDao = focusStateDao,
        sessionEventDao = sessionEventDao,
        slotProgressionEngine = DefaultSlotProgressionEngine(),
    )

    override fun observeCharacters(): Flow<List<CharacterProfile>> {
        return characterDao.observeCharacters().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCharacter(characterId: Long): CharacterProfile? {
        return characterDao.getCharacterById(characterId)?.toDomain()
    }

    override suspend fun upsertCharacter(character: CharacterProfile): Long {
        return database.withTransaction {
            val characterId = characterDao.insertOrUpdate(character.toEntity())
            val persistedCharacter = character.copy(id = characterId)
            ensurePrimaryTrack(persistedCharacter)
            syncPreparedSlotsForCharacterInternal(persistedCharacter)
            characterId
        }
    }

    override suspend fun deleteCharacter(characterId: Long) {
        characterDao.deleteById(characterId)
    }

    override fun observeBuildIdentity(characterId: Long): Flow<CharacterBuildIdentity?> {
        return characterBuildIdentityDao.observeByCharacter(characterId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getBuildIdentity(characterId: Long): CharacterBuildIdentity? {
        return characterBuildIdentityDao.getByCharacter(characterId)?.toDomain()
    }

    override suspend fun upsertBuildIdentity(identity: CharacterBuildIdentity) {
        characterBuildIdentityDao.upsert(identity.toEntity())
    }

    override fun observeBuildOptions(characterId: Long): Flow<List<CharacterBuildOption>> {
        return characterBuildOptionDao.observeByCharacter(characterId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBuildOptions(characterId: Long): List<CharacterBuildOption> {
        return characterBuildOptionDao.getByCharacter(characterId).map { it.toDomain() }
    }

    override suspend fun upsertBuildOption(option: CharacterBuildOption): Long {
        return characterBuildOptionDao.upsert(option.toEntity())
    }

    override suspend fun deleteBuildOption(
        characterId: Long,
        optionType: CharacterBuildOptionType,
        optionId: String,
    ): Boolean {
        return characterBuildOptionDao.deleteByCharacterAndOption(
            characterId = characterId,
            optionType = optionType.name,
            optionId = optionId,
        ) > 0
    }

    override suspend fun replaceBuildOptions(
        characterId: Long,
        options: List<CharacterBuildOption>,
    ) {
        database.withTransaction {
            characterBuildOptionDao.deleteByCharacter(characterId)
            if (options.isNotEmpty()) {
                characterBuildOptionDao.upsertAll(
                    options.map { option ->
                        option.copy(
                            id = 0L,
                            characterId = characterId,
                        ).toEntity()
                    },
                )
            }
        }
    }

    override fun observeCastingTracks(characterId: Long): Flow<List<CastingTrack>> {
        return castingTrackDao.observeByCharacter(characterId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCastingTracks(characterId: Long): List<CastingTrack> {
        return castingTrackDao.getByCharacter(characterId).map { it.toDomain() }
    }

    override suspend fun upsertCastingTrack(track: CastingTrack): Long {
        return castingTrackDao.upsert(track.toEntity())
    }

    override suspend fun deleteCastingTrack(
        characterId: Long,
        trackKey: String,
    ): Boolean {
        return castingTrackDao.deleteByCharacterAndTrack(
            characterId = characterId,
            trackKey = trackKey,
        ) > 0
    }

    override suspend fun syncPreparedSlotsForCharacter(characterId: Long) {
        database.withTransaction {
            val character = characterDao.getCharacterById(characterId)?.toDomain()
                ?: return@withTransaction
            ensurePrimaryTrack(character)
            syncPreparedSlotsForCharacterInternal(character)
        }
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
            val isCantrip = rank == 0
            if (!isCantrip && targetSlot.isExpended) {
                return@withTransaction false
            }

            if (!isCantrip) {
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

    override suspend fun uncastSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
    ): Boolean {
        val updateCount = preparedSlotDao.setExpendedState(
            characterId = characterId,
            trackKey = trackKey,
            rank = rank,
            slotIndex = slotIndex,
            isExpended = false,
        )
        return updateCount > 0
    }

    override suspend fun restoreAllSlotsForTrack(
        characterId: Long,
        trackKey: String,
    ): Int {
        return preparedSlotDao.restoreAllSlotsForTrack(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    override suspend fun clearPreparedSlotsForTrack(
        characterId: Long,
        trackKey: String,
    ): Int {
        return database.withTransaction {
            preparedSlotDao.clearPreparedSpellsForTrack(
                characterId = characterId,
                trackKey = trackKey,
            )
        }
    }

    override suspend fun undoLastCast(
        characterId: Long,
        trackKey: String?,
    ): Boolean {
        return database.withTransaction {
            val latestEvent = sessionEventDao.getByCharacter(characterId)
                .firstOrNull { event ->
                    event.type == SessionEventType.CAST_SPELL.name &&
                        (
                            trackKey == null ||
                                SessionEventMetadata.trackKeyOrDefault(event.metadataJson) == trackKey
                            )
                }
                ?: return@withTransaction false

            val rank = latestEvent.spellRank ?: return@withTransaction false
            val slotIndex = SessionEventMetadata.slotIndexOrNull(latestEvent.metadataJson)
                ?: return@withTransaction false
            val eventTrackKey = SessionEventMetadata.trackKeyOrDefault(latestEvent.metadataJson)

            if (rank != 0) {
                val updateCount = preparedSlotDao.setExpendedState(
                    characterId = characterId,
                    trackKey = eventTrackKey,
                    rank = rank,
                    slotIndex = slotIndex,
                    isExpended = false,
                )
                if (updateCount <= 0) {
                    return@withTransaction false
                }
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

    override fun observeSessionEvents(
        characterId: Long,
        trackKey: String?,
    ): Flow<List<SessionEvent>> {
        return sessionEventDao.observeByCharacter(characterId).map { entities ->
            entities
                .filter { entity ->
                    trackKey == null || SessionEventMetadata.trackKeyOrDefault(entity.metadataJson) == trackKey
                }
                .map { it.toDomain() }
        }
    }

    override suspend fun appendSessionEvent(event: SessionEvent): Long {
        return sessionEventDao.insert(event.toEntity())
    }

    private suspend fun ensurePrimaryTrack(character: CharacterProfile) {
        val existingPrimary = castingTrackDao.getByCharacterAndTrack(
            characterId = character.id,
            trackKey = CastingTrack.PRIMARY_TRACK_KEY,
        )
        val primaryTrack = CastingTrackEntity(
            id = existingPrimary?.id ?: 0L,
            characterId = character.id,
            trackKey = CastingTrack.PRIMARY_TRACK_KEY,
            sourceType = CastingTrackSourceType.PRIMARY_CLASS.name,
            sourceId = character.characterClass.name,
            progressionType = primaryProgressionForClass(character.characterClass).name,
        )
        castingTrackDao.upsert(primaryTrack)
    }

    private suspend fun syncPreparedSlotsForCharacterInternal(character: CharacterProfile) {
        val tracks = castingTrackDao.getByCharacter(character.id).map { it.toDomain() }
        if (tracks.isEmpty()) {
            return
        }
        val selectedBuildOptionIds = characterBuildOptionDao.getByCharacter(character.id)
            .map { entity -> entity.optionId }
            .toSet()

        val currentByTrackAndRank = preparedSlotDao.getByCharacter(character.id)
            .groupBy { slot -> slot.trackKey to slot.rank }

        val desiredTrackKeys = tracks.map { it.trackKey }.toSet()
        val existingTrackKeys = currentByTrackAndRank.keys.map { it.first }.toSet()
        val orphanTrackKeys = existingTrackKeys - desiredTrackKeys
        orphanTrackKeys.forEach { orphanTrackKey ->
            val entriesForTrack = currentByTrackAndRank
                .filterKeys { (trackKey, _) -> trackKey == orphanTrackKey }
                .values
                .flatten()
            entriesForTrack.forEach { slot ->
                preparedSlotDao.deleteByCharacterRankAndIndex(
                    characterId = character.id,
                    trackKey = slot.trackKey,
                    rank = slot.rank,
                    slotIndex = slot.slotIndex,
                )
            }
        }

        tracks.forEach { track ->
            val desiredByRank = slotProgressionEngine.slotCountsByRank(
                level = character.level,
                track = track,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
            val ranksInPlay = (desiredByRank.keys + currentByTrackAndRank.keys
                .filter { (trackKey, _) -> trackKey == track.trackKey }
                .map { (_, rank) -> rank }).toSet()

            ranksInPlay.forEach { rank ->
                val desiredCount = desiredByRank[rank] ?: 0
                val current = currentByTrackAndRank[track.trackKey to rank]
                    .orEmpty()
                    .sortedBy { it.slotIndex }
                syncRankSlots(
                    characterId = character.id,
                    trackKey = track.trackKey,
                    rank = rank,
                    current = current,
                    desiredCount = desiredCount,
                )
            }
        }
    }

    private suspend fun syncRankSlots(
        characterId: Long,
        trackKey: String,
        rank: Int,
        current: List<PreparedSlotEntity>,
        desiredCount: Int,
    ) {
        if (desiredCount <= 0) {
            current.forEach { slot ->
                preparedSlotDao.deleteByCharacterRankAndIndex(
                    characterId = characterId,
                    trackKey = trackKey,
                    rank = rank,
                    slotIndex = slot.slotIndex,
                )
            }
            return
        }

        val desiredIndexes = (0 until desiredCount).toSet()
        val currentByIndex = current.associateBy { it.slotIndex }

        current.filter { slot -> slot.slotIndex !in desiredIndexes }.forEach { slot ->
            preparedSlotDao.deleteByCharacterRankAndIndex(
                characterId = characterId,
                trackKey = trackKey,
                rank = rank,
                slotIndex = slot.slotIndex,
            )
        }

        desiredIndexes.filter { slotIndex -> slotIndex !in currentByIndex }.forEach { slotIndex ->
            preparedSlotDao.upsert(
                PreparedSlotEntity(
                    characterId = characterId,
                    trackKey = trackKey,
                    rank = rank,
                    slotIndex = slotIndex,
                    preparedSpellId = null,
                    isExpended = false,
                ),
            )
        }
    }

    private fun primaryProgressionForClass(characterClass: CharacterClass): CastingProgressionType {
        return when (characterClass) {
            CharacterClass.WIZARD,
            CharacterClass.CLERIC,
            CharacterClass.DRUID,
            CharacterClass.OTHER -> CastingProgressionType.FULL_PREPARED
        }
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

    private fun CharacterBuildIdentityEntity.toDomain(): CharacterBuildIdentity {
        return CharacterBuildIdentity(
            characterId = characterId,
            ancestryId = ancestryId,
            heritageId = heritageId,
            backgroundId = backgroundId,
        )
    }

    private fun CharacterBuildIdentity.toEntity(): CharacterBuildIdentityEntity {
        return CharacterBuildIdentityEntity(
            characterId = characterId,
            ancestryId = ancestryId,
            heritageId = heritageId,
            backgroundId = backgroundId,
        )
    }

    private fun CharacterBuildOptionEntity.toDomain(): CharacterBuildOption {
        return CharacterBuildOption(
            id = id,
            characterId = characterId,
            optionType = enumValueOrDefault(optionType, CharacterBuildOptionType.OTHER),
            optionId = optionId,
            levelAcquired = levelAcquired,
            metadataJson = metadataJson,
        )
    }

    private fun CharacterBuildOption.toEntity(): CharacterBuildOptionEntity {
        return CharacterBuildOptionEntity(
            id = id,
            characterId = characterId,
            optionType = optionType.name,
            optionId = optionId,
            levelAcquired = levelAcquired,
            metadataJson = metadataJson,
        )
    }

    private fun CastingTrackEntity.toDomain(): CastingTrack {
        return CastingTrack(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            sourceType = enumValueOrDefault(sourceType, CastingTrackSourceType.PRIMARY_CLASS),
            sourceId = sourceId,
            progressionType = enumValueOrDefault(progressionType, CastingProgressionType.FULL_PREPARED),
        )
    }

    private fun CastingTrack.toEntity(): CastingTrackEntity {
        return CastingTrackEntity(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            sourceType = sourceType.name,
            sourceId = sourceId,
            progressionType = progressionType.name,
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

internal suspend fun CharacterDao.insertOrUpdate(character: CharacterEntity): Long {
    return when {
        character.id == 0L -> insert(character)
        update(character) > 0 -> character.id
        else -> insert(character)
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


