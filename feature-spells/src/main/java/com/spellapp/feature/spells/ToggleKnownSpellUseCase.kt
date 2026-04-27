package com.spellapp.feature.spells

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository

class ToggleKnownSpellUseCase(
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val warningPolicy: KnownSpellWarningPolicy,
) {
    suspend fun addAll(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellIds: Collection<String>,
    ): PendingKnownSpellWarning? {
        val unknownSpellIds = spellIds.distinct().filterNot { spellId ->
            knownSpellRepository.isKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
        }
        if (unknownSpellIds.isEmpty()) {
            return null
        }

        val warnedSpellNames = buildList {
            unknownSpellIds.forEach { spellId ->
                val detail = spellRepository.getSpellDetail(spellId) ?: return@forEach
                val warning = warningPolicy.warningFor(
                    detail = detail,
                    preferredTradition = mode.preferredTradition,
                    trackSourceId = mode.trackSourceId,
                )
                if (warning != null) {
                    add(detail.name)
                }
            }
        }
        if (warnedSpellNames.isNotEmpty()) {
            return bulkLearnWarning(
                spellIds = unknownSpellIds,
                warnedSpellNames = warnedSpellNames,
            )
        }

        confirmAdd(mode, unknownSpellIds)
        return null
    }

    suspend fun removeAll(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellIds: Collection<String>,
    ) {
        spellIds.distinct().forEach { spellId ->
            knownSpellRepository.removeKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
        }
    }

    suspend fun toggle(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellId: String,
    ): PendingKnownSpellWarning? {
        val isKnown = knownSpellRepository.isKnownSpell(
            characterId = mode.characterId,
            trackKey = mode.trackKey,
            spellId = spellId,
        )
        if (isKnown) {
            knownSpellRepository.removeKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
            return null
        }
        val warning = spellRepository.getSpellDetail(spellId)?.let { detail ->
            warningPolicy.warningFor(
                detail = detail,
                preferredTradition = mode.preferredTradition,
                trackSourceId = mode.trackSourceId,
            )
        }
        if (warning != null) {
            return warning
        }
        knownSpellRepository.addKnownSpell(
            characterId = mode.characterId,
            trackKey = mode.trackKey,
            spellId = spellId,
        )
        return null
    }

    suspend fun confirmAdd(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellIds: Collection<String>,
    ) {
        spellIds.distinct().forEach { spellId ->
            val isKnown = knownSpellRepository.isKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
            if (!isKnown) {
                knownSpellRepository.addKnownSpell(
                    characterId = mode.characterId,
                    trackKey = mode.trackKey,
                    spellId = spellId,
                )
            }
        }
    }

    private fun bulkLearnWarning(
        spellIds: List<String>,
        warnedSpellNames: List<String>,
    ): PendingKnownSpellWarning {
        val previewNames = warnedSpellNames.take(4)
        val namesSummary = when {
            previewNames.isEmpty() -> ""
            warnedSpellNames.size > previewNames.size -> {
                "${previewNames.joinToString(", ")}, and ${warnedSpellNames.size - previewNames.size} more"
            }

            else -> previewNames.joinToString(", ")
        }
        val warningCount = warnedSpellNames.size
        val totalCount = spellIds.size
        val subject = if (warningCount == 1) "spell needs" else "spells need"
        val message = if (namesSummary.isBlank()) {
            "$warningCount of the $totalCount visible spells $subject confirmation for this track. Learn all anyway?"
        } else {
            "$warningCount of the $totalCount visible spells $subject confirmation for this track: $namesSummary. Learn all anyway?"
        }
        return PendingKnownSpellWarning(
            spellIds = spellIds,
            title = "Learn Spells With Warnings?",
            message = message,
            confirmLabel = "Learn All Anyway",
        )
    }
}
