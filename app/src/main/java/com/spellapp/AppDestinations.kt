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

    data object SpellDetail : AppDestinations("spell_detail/{spellId}") {
        const val argSpellId = "spellId"

        fun routeFor(spellId: String): String {
            return "spell_detail/${Uri.encode(spellId)}"
        }
    }
}
