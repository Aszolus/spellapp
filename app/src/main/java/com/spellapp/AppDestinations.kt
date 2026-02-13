package com.spellapp

import android.net.Uri

sealed class AppDestinations(val route: String) {
    data object SpellList : AppDestinations("spell_list")

    data object SpellDetail : AppDestinations("spell_detail/{spellId}") {
        const val argSpellId = "spellId"

        fun routeFor(spellId: String): String {
            return "spell_detail/${Uri.encode(spellId)}"
        }
    }
}
