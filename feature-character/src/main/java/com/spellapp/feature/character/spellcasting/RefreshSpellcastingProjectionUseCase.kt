package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterProfile
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource

class RefreshSpellcastingProjectionUseCase(
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val knownSpellsSeeder: DefaultKnownSpellsSeeder,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource,
) {
    suspend fun refreshCharacterSpellcasting(
        character: CharacterProfile,
        selectedBuildOptionIds: Set<String>,
        acceptedSourceBooks: Set<String>,
        isNewCharacter: Boolean,
        reconcileArchetypeTracks: Boolean,
    ) {
        if (reconcileArchetypeTracks) {
            reconcileArchetypeTracks(
                characterId = character.id,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
        }
        if (isNewCharacter) {
            knownSpellsSeeder.seedForCharacter(
                character = character,
                acceptedSourceBooks = acceptedSourceBooks,
            )
        }
        preparedSlotSyncRepository.syncPreparedSlotsForCharacter(character.id)
    }

    private suspend fun reconcileArchetypeTracks(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
    ) {
        val existingArchetypeTracks = castingTrackRepository.getCastingTracks(characterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        val selectedArchetypes = archetypeSpellcastingCatalogSource.phaseOnePackages()
            .filter { packageDef ->
                packageDef.dedicationOptionId in selectedBuildOptionIds &&
                    packageDef.supportsPreparedSpellcastingTrack()
            }
        val desiredTracksByKey = selectedArchetypes.associateBy { packageDef ->
            trackKeyForArchetype(packageDef.archetypeId)
        }

        existingArchetypeTracks
            .filterNot { track -> track.trackKey in desiredTracksByKey.keys }
            .forEach { track ->
                castingTrackRepository.deleteCastingTrack(
                    characterId = characterId,
                    trackKey = track.trackKey,
                )
            }

        desiredTracksByKey.forEach { (trackKey, packageDef) ->
            castingTrackRepository.upsertCastingTrack(
                CastingTrack(
                    characterId = characterId,
                    trackKey = trackKey,
                    sourceType = CastingTrackSourceType.ARCHETYPE,
                    sourceId = packageDef.label,
                    progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
                ),
            )
        }
    }

    private fun trackKeyForArchetype(archetypeId: String): String {
        return "archetype-$archetypeId"
    }

    private fun com.spellapp.feature.character.ArchetypeSpellcastingPackage.supportsPreparedSpellcastingTrack(): Boolean {
        return basicSpellcastingOptionId != null ||
            expertSpellcastingOptionId != null ||
            masterSpellcastingOptionId != null
    }
}
