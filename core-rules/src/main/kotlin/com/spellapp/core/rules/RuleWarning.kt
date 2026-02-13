package com.spellapp.core.rules

data class RuleWarning(
    val code: WarningCode,
    val message: String,
)

enum class WarningCode {
    SLOT_NOT_FOUND,
    PREPARED_SLOT_EMPTY,
    PREPARED_SLOT_ALREADY_EXPENDED,
}
