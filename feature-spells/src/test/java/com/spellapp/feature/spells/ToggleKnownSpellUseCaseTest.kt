package com.spellapp.feature.spells

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleKnownSpellUseCaseTest {
    @Test
    fun addAll_adds_only_visible_unknown_spells() = runBlocking {
        val knownSpellRepository = FakeKnownSpellRepository(initialKnownSpellIds = setOf("heal"))
        val useCase = ToggleKnownSpellUseCase(
            knownSpellRepository = knownSpellRepository,
            spellRepository = FakeSpellRepository(),
            warningPolicy = NoOpKnownSpellWarningPolicy(),
        )

        val warning = useCase.addAll(
            mode = SpellBrowserMode.ManageKnownSpells(
                characterId = CHARACTER_ID,
                trackKey = TRACK_KEY,
                preferredTradition = "divine",
            ),
            spellIds = listOf("heal", "bless", "heal", "bane"),
        )

        assertEquals(null, warning)
        assertEquals(
            setOf("heal", "bless", "bane"),
            knownSpellRepository.observeKnownSpellIds(CHARACTER_ID, TRACK_KEY).value(),
        )
        assertEquals(listOf("bless", "bane"), knownSpellRepository.addedSpellIds)
    }

    @Test
    fun addAll_returns_warning_when_any_visible_spell_needs_confirmation() = runBlocking {
        val knownSpellRepository = FakeKnownSpellRepository(initialKnownSpellIds = emptySet())
        val warningSpell = spellDetail(id = "mystery", name = "Mystery Spell")
        val safeSpell = spellDetail(id = "heal", name = "Heal")
        val useCase = ToggleKnownSpellUseCase(
            knownSpellRepository = knownSpellRepository,
            spellRepository = FakeSpellRepository(
                detailsById = mapOf(
                    warningSpell.id to warningSpell,
                    safeSpell.id to safeSpell,
                ),
            ),
            warningPolicy = SelectiveKnownSpellWarningPolicy(warnedSpellIds = setOf(warningSpell.id)),
        )

        val warning = useCase.addAll(
            mode = SpellBrowserMode.ManageKnownSpells(
                characterId = CHARACTER_ID,
                trackKey = TRACK_KEY,
                preferredTradition = "divine",
            ),
            spellIds = listOf(safeSpell.id, warningSpell.id),
        )

        assertEquals("Learn Spells With Warnings?", warning?.title)
        assertEquals(listOf(safeSpell.id, warningSpell.id), warning?.spellIds)
        assertEquals(emptyList<String>(), knownSpellRepository.addedSpellIds)
    }

    @Test
    fun removeAll_unlearns_only_requested_visible_spells() = runBlocking {
        val knownSpellRepository = FakeKnownSpellRepository(initialKnownSpellIds = setOf("heal", "bless", "bane"))
        val useCase = ToggleKnownSpellUseCase(
            knownSpellRepository = knownSpellRepository,
            spellRepository = FakeSpellRepository(),
            warningPolicy = NoOpKnownSpellWarningPolicy(),
        )

        useCase.removeAll(
            mode = SpellBrowserMode.ManageKnownSpells(
                characterId = CHARACTER_ID,
                trackKey = TRACK_KEY,
            ),
            spellIds = listOf("heal", "bane"),
        )

        assertEquals(
            setOf("bless"),
            knownSpellRepository.observeKnownSpellIds(CHARACTER_ID, TRACK_KEY).value(),
        )
        assertEquals(listOf("heal", "bane"), knownSpellRepository.removedSpellIds)
    }

    private class FakeKnownSpellRepository(
        initialKnownSpellIds: Set<String>,
    ) : KnownSpellRepository {
        private val knownSpells = MutableStateFlow(
            initialKnownSpellIds.mapIndexed { index, spellId ->
                KnownSpell(
                    id = index + 1L,
                    characterId = CHARACTER_ID,
                    trackKey = TRACK_KEY,
                    spellId = spellId,
                )
            },
        )

        val addedSpellIds = mutableListOf<String>()
        val removedSpellIds = mutableListOf<String>()

        override fun observeKnownSpells(characterId: Long, trackKey: String): Flow<List<KnownSpell>> = knownSpells

        override fun observeKnownSpellIds(characterId: Long, trackKey: String): Flow<Set<String>> {
            return knownSpells.map { spells -> spells.map { it.spellId }.toSet() }
        }

        override suspend fun addKnownSpell(characterId: Long, trackKey: String, spellId: String): Long {
            addedSpellIds += spellId
            knownSpells.value = knownSpells.value + KnownSpell(
                id = knownSpells.value.size + 1L,
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
            )
            return knownSpells.value.last().id
        }

        override suspend fun removeKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
            removedSpellIds += spellId
            val before = knownSpells.value.size
            knownSpells.value = knownSpells.value.filterNot { it.spellId == spellId }
            return knownSpells.value.size != before
        }

        override suspend fun isKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
            return knownSpells.value.any { it.spellId == spellId }
        }
    }

    private class NoOpKnownSpellWarningPolicy : KnownSpellWarningPolicy {
        override fun warningFor(
            detail: SpellDetail,
            preferredTradition: String?,
            trackSourceId: String?,
        ): PendingKnownSpellWarning? = null
    }

    private class FakeSpellRepository(
        private val detailsById: Map<String, SpellDetail> = emptyMap(),
    ) : SpellRepository {
        override fun observeAvailableSources(): Flow<List<String>> = flowOf(emptyList())

        override fun observeAvailableTraits(): Flow<List<String>> = flowOf(emptyList())

        override fun observeSpells(
            query: String,
            rank: Int?,
            tradition: String?,
            rarity: String?,
            trait: String?,
        ): Flow<List<SpellListItem>> = flowOf(emptyList())

        override suspend fun getSpellDetail(spellId: String): SpellDetail? = detailsById[spellId]

        override suspend fun seedFromDatasetIfEmpty(datasetJson: String) = Unit
    }

    private class SelectiveKnownSpellWarningPolicy(
        private val warnedSpellIds: Set<String>,
    ) : KnownSpellWarningPolicy {
        override fun warningFor(
            detail: SpellDetail,
            preferredTradition: String?,
            trackSourceId: String?,
        ): PendingKnownSpellWarning? {
            if (detail.id !in warnedSpellIds) return null
            return PendingKnownSpellWarning(
                spellId = detail.id,
                title = "Learn Off-Tradition Spell?",
                message = "${detail.name} needs confirmation.",
            )
        }
    }

    private fun Flow<Set<String>>.value(): Set<String> {
        return runBlocking { first() }
    }

    private companion object {
        private const val CHARACTER_ID = 42L
        private const val TRACK_KEY = "primary"
    }

    private fun spellDetail(
        id: String,
        name: String,
    ): SpellDetail {
        return SpellDetail(
            id = id,
            name = name,
            rank = 1,
            tradition = "divine",
            rarity = "common",
            traits = emptyList(),
            castTime = "2 actions",
            range = "",
            target = "",
            duration = "",
            description = "",
            license = "",
            sourceBook = "",
            sourcePage = null,
        )
    }
}
