package com.spellapp.core.model

data class SessionEvent(
    val id: Long = 0L,
    val characterId: Long,
    val type: SessionEventType,
    val spellId: String? = null,
    val spellRank: Int? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val metadataJson: String = "{}",
)
