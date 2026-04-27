package com.spellapp.feature.spells

import com.spellapp.core.data.local.ClassSpellcastingCatalogJsonParser
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.SpellDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class KnownSpellWarningPolicyTest {
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        ClassSpellcastingCatalogJsonParser.parse(readClassSpellcastingAsset())
    private val policy = DefaultKnownSpellWarningPolicy(classSpellcastingCatalogSource)

    @Test
    fun returns_off_tradition_warning_when_explicit_tradition_mismatches_track() {
        val warning = policy.warningFor(
            detail = spellDetail(
                name = "Heal",
                tradition = "divine",
                traits = listOf("healing"),
            ),
            preferredTradition = "primal",
            trackSourceId = "DRUID",
        )

        assertNotNull(warning)
        assertEquals("Learn Off-Tradition Spell?", warning?.title)
    }

    @Test
    fun returns_class_specific_warning_when_no_tradition_and_class_trait_mismatches_track() {
        val warning = policy.warningFor(
            detail = spellDetail(
                name = "Discern Secrets",
                tradition = "",
                traits = listOf("cantrip", "hex", "witch"),
            ),
            preferredTradition = "primal",
            trackSourceId = "DRUID",
        )

        assertNotNull(warning)
        assertEquals("Learn Class-Specific Spell?", warning?.title)
    }

    @Test
    fun returns_generic_warning_when_no_tradition_and_no_class_trait_exists() {
        val warning = policy.warningFor(
            detail = spellDetail(
                name = "Mysterious Spell",
                tradition = "",
                traits = listOf("cantrip", "hex"),
            ),
            preferredTradition = "primal",
            trackSourceId = "DRUID",
        )

        assertNotNull(warning)
        assertEquals("Learn Spell Without Tradition?", warning?.title)
    }

    @Test
    fun returns_no_warning_when_no_tradition_and_class_trait_matches_track() {
        val warning = policy.warningFor(
            detail = spellDetail(
                name = "Discern Secrets",
                tradition = "",
                traits = listOf("cantrip", "hex", "witch"),
            ),
            preferredTradition = null,
            trackSourceId = "WITCH",
        )

        assertNull(warning)
    }

    private fun spellDetail(
        name: String,
        tradition: String,
        traits: List<String>,
    ): SpellDetail {
        return SpellDetail(
            id = name.lowercase().replace(' ', '-'),
            name = name,
            rank = 0,
            tradition = tradition,
            rarity = "uncommon",
            traits = traits,
            castTime = "1",
            range = "",
            target = "",
            duration = "",
            description = "",
            license = "",
            sourceBook = "Player Core",
            sourcePage = null,
        )
    }

    private fun readClassSpellcastingAsset(): String {
        val candidates = listOf(
            File("app/src/main/assets/class-spellcasting.normalized.json"),
            File("../app/src/main/assets/class-spellcasting.normalized.json"),
            File("../../app/src/main/assets/class-spellcasting.normalized.json"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("class-spellcasting.normalized.json not found from ${File(".").absolutePath}")
    }
}
