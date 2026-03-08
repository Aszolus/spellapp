package com.spellapp.feature.spells

import com.spellapp.core.model.PreparedSlot

sealed interface SpellBrowserMode {
    data class BrowseCatalog(
        val characterId: Long? = null,
    ) : SpellBrowserMode

    data class ManageKnownSpells(
        val characterId: Long,
        val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ) : SpellBrowserMode

    data class AssignPreparedSlot(
        val characterId: Long,
        val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
        val slotRank: Int,
        val slotIndex: Int,
    ) : SpellBrowserMode
}
