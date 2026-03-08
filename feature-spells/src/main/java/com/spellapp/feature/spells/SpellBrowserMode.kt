package com.spellapp.feature.spells

import com.spellapp.core.model.PreparedSlot

sealed interface SpellBrowserMode {
    data class BrowseCatalog(
        val characterId: Long? = null,
    ) : SpellBrowserMode

    data class ManageKnownSpells(
        val characterId: Long,
        val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
        val preferredTradition: String? = null,
        val trackSourceId: String? = null,
    ) : SpellBrowserMode

    data class AssignPreparedSlot(
        val characterId: Long,
        val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
        val slotRank: Int,
        val slotIndex: Int,
        val preferredTradition: String? = null,
    ) : SpellBrowserMode
}
