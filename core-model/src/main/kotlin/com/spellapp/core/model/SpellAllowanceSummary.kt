package com.spellapp.core.model

data class SpellAllowanceSummary(
    val kind: SpellAllowanceKind,
    val label: String,
    val rank: Int? = null,
    val expected: Int? = null,
    val actual: Int,
    val policy: SpellAllowancePolicy,
    val warning: String? = null,
    val source: String? = null,
    val note: String? = null,
)

fun buildSpellAllowanceSummaries(
    rules: List<SpellAllowanceRule>,
    characterLevel: Int,
    knownSpells: List<KnownSpell>,
    knownSpellBaseRanksById: Map<String, Int> = emptyMap(),
    preparedSlots: List<PreparedSlot> = emptyList(),
): List<SpellAllowanceSummary> {
    val knownRanks = knownSpells.mapNotNull { knownSpell ->
        val rank = knownSpell.knownRank ?: knownSpellBaseRanksById[knownSpell.spellId]
        rank?.let { knownSpell to it }
    }
    val knownCountByRank = knownRanks.groupingBy { (_, rank) -> rank }.eachCount()
    val preparedCountByRank = preparedSlots
        .filter { slot -> slot.preparedSpellId != null }
        .groupingBy { slot -> slot.rank }
        .eachCount()

    return rules.flatMap { rule ->
        when (rule.kind) {
            SpellAllowanceKind.PREPARED_SLOTS -> {
                val expectedByRank = rule.countsAtLevel(characterLevel)
                    .ifEmpty {
                        preparedSlots.groupingBy { slot -> slot.rank }.eachCount()
                    }
                expectedByRank.toSortedMap().map { (rank, expected) ->
                    allowanceSummaryForRank(
                        rule = rule,
                        rank = rank,
                        expected = expected,
                        actual = preparedCountByRank[rank] ?: 0,
                    )
                }
            }

            SpellAllowanceKind.REPERTOIRE -> {
                val expectedByRank = rule.countsAtLevel(characterLevel)
                expectedByRank.toSortedMap().map { (rank, expected) ->
                    allowanceSummaryForRank(
                        rule = rule,
                        rank = rank,
                        expected = expected,
                        actual = knownCountByRank[rank] ?: 0,
                    )
                }
            }

            SpellAllowanceKind.SIGNATURE_SPELLS -> {
                val totalKnown = knownSpells.size
                val actualSignatures = knownSpells.count { knownSpell -> knownSpell.isSignature }
                val expected = rule.totalAtLevel(characterLevel)
                listOf(
                    SpellAllowanceSummary(
                        kind = rule.kind,
                        label = rule.label,
                        expected = expected,
                        actual = if (rule.policy == SpellAllowancePolicy.ALL_KNOWN) {
                            totalKnown
                        } else {
                            actualSignatures
                        },
                        policy = rule.policy,
                        warning = when {
                            rule.policy == SpellAllowancePolicy.ALL_KNOWN -> null
                            expected == null -> null
                            actualSignatures < expected -> "${expected - actualSignatures} signature spell not marked"
                            actualSignatures > expected -> "${actualSignatures - expected} over expected signature spells"
                            else -> null
                        },
                        source = rule.source,
                        note = rule.note,
                    ),
                )
            }

            SpellAllowanceKind.SPELLBOOK_MINIMUM,
            SpellAllowanceKind.FAMILIAR_MINIMUM,
            -> {
                val expected = rule.totalAtLevel(characterLevel)
                listOf(
                    SpellAllowanceSummary(
                        kind = rule.kind,
                        label = rule.label,
                        expected = expected,
                        actual = knownSpells.size,
                        policy = rule.policy,
                        warning = if (expected != null && knownSpells.size < expected) {
                            "${expected - knownSpells.size} below expected minimum"
                        } else {
                            null
                        },
                        source = rule.source,
                        note = rule.note,
                    ),
                )
            }
        }
    }
}

private fun allowanceSummaryForRank(
    rule: SpellAllowanceRule,
    rank: Int,
    expected: Int,
    actual: Int,
): SpellAllowanceSummary {
    return SpellAllowanceSummary(
        kind = rule.kind,
        label = rule.label,
        rank = rank,
        expected = expected,
        actual = actual,
        policy = rule.policy,
        warning = when {
            rule.kind == SpellAllowanceKind.PREPARED_SLOTS -> if (actual > expected) {
                "${actual - expected} over expected ${rule.label.lowercase()}"
            } else {
                null
            }

            rule.policy == SpellAllowancePolicy.CAP -> when {
                actual > expected -> "${actual - expected} over expected ${rule.label.lowercase()}"
                actual < expected -> "${expected - actual} below expected ${rule.label.lowercase()}"
                else -> null
            }

            rule.policy == SpellAllowancePolicy.MINIMUM -> if (actual < expected) {
                "${expected - actual} below expected minimum"
            } else {
                null
            }

            rule.policy == SpellAllowancePolicy.WARNING_ONLY -> when {
                actual > expected -> "${actual - expected} over expected ${rule.label.lowercase()}"
                actual < expected -> "${expected - actual} below expected ${rule.label.lowercase()}"
                else -> null
            }

            rule.policy == SpellAllowancePolicy.ALL_KNOWN -> null

            else -> null
        },
        source = rule.source,
        note = rule.note,
    )
}
