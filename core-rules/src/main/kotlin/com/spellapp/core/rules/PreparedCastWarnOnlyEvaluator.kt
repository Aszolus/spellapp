package com.spellapp.core.rules

import com.spellapp.core.model.PreparedSlot

data class PreparedCastContext(
    val slot: PreparedSlot?,
)

interface PreparedCastWarnOnlyEvaluator {
    fun evaluate(context: PreparedCastContext): List<RuleWarning>
}

class DefaultPreparedCastWarnOnlyEvaluator : PreparedCastWarnOnlyEvaluator {
    override fun evaluate(context: PreparedCastContext): List<RuleWarning> {
        val slot = context.slot
        if (slot == null) {
            return listOf(
                RuleWarning(
                    code = WarningCode.SLOT_NOT_FOUND,
                    message = "Slot no longer exists. Refresh and try again.",
                ),
            )
        }

        val warnings = mutableListOf<RuleWarning>()
        if (slot.preparedSpellId == null) {
            warnings += RuleWarning(
                code = WarningCode.PREPARED_SLOT_EMPTY,
                message = "No spell is prepared in this slot.",
            )
        }
        if (slot.isExpended) {
            warnings += RuleWarning(
                code = WarningCode.PREPARED_SLOT_ALREADY_EXPENDED,
                message = "This slot is already expended.",
            )
        }
        return warnings
    }
}
