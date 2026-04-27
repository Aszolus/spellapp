package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterProfile
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.ArchetypeSpellcastingPackage

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
        val selectedPreparedArchetypes = if (reconcileArchetypeTracks) {
            reconcileArchetypeTracks(
                characterId = character.id,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
        } else {
            emptyList()
        }
        if (isNewCharacter) {
            knownSpellsSeeder.seedForCharacter(
                character = character,
                selectedBuildOptionIds = selectedBuildOptionIds,
                acceptedSourceBooks = acceptedSourceBooks,
            )
        }
        selectedPreparedArchetypes.forEach { packageDef ->
            knownSpellsSeeder.seedPreparedArchetypeTrack(
                characterId = character.id,
                trackKey = trackKeyForArchetype(packageDef.archetypeId),
                archetypeClassId = packageDef.archetypeId,
                acceptedSourceBooks = acceptedSourceBooks,
            )
        }
        preparedSlotSyncRepository.syncPreparedSlotsForCharacter(character.id)
    }

    private suspend fun reconcileArchetypeTracks(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
    ): List<ArchetypeSpellcastingPackage> {
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
                    sourceId = packageDef.archetypeId,
                    progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
                    displayName = packageDef.label,
                ),
            )
        }
        return selectedArchetypes
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
