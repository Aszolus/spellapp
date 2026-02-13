package com.spellapp.core.model

data class PreparedSlot(
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String = PRIMARY_TRACK_KEY,
    val rank: Int,
    val slotIndex: Int,
    val preparedSpellId: String? = null,
    val isExpended: Boolean = false,
) {
    companion object {
        const val PRIMARY_TRACK_KEY = "primary"
    }
}
