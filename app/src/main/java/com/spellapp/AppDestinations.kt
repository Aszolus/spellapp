package com.spellapp

import android.net.Uri

sealed class AppDestinations(val route: String) {
    data object CharacterList : AppDestinations("character_list")

    data object SpellList : AppDestinations("spell_list")

    data object PreparedSlots : AppDestinations("prepared_slots/{characterId}") {
        const val argCharacterId = "characterId"

        fun routeFor(characterId: Long): String {
            return "prepared_slots/$characterId"
        }
    }

    data object SpellDetail : AppDestinations("spell_detail/{spellId}?heightenedAt={heightenedAt}") {
        const val argSpellId = "spellId"
        const val argHeightenedAt = "heightenedAt"

        fun routeFor(spellId: String, heightenedAt: Int? = null): String {
            val base = "spell_detail/${Uri.encode(spellId)}"
            return if (heightenedAt != null) "$base?heightenedAt=$heightenedAt" else base
        }
    }
}
