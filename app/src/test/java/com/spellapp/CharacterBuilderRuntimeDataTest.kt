package com.spellapp

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CharacterBuilderRuntimeDataTest {
    @Test
    fun appContainerLoadsClassDefinitionsForCreateCharacter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = AppContainer(context)

        val classDefinitions = container.characterClassDefinitionSource.phaseOneDefinitions()
        val spellcastingDefinitions = container.classSpellcastingCatalogSource.allDefinitions()

        assertTrue(
            "Expected runtime class definitions to include wizard; got ${classDefinitions.map { it.classId }}",
            classDefinitions.any { it.classId == "wizard" },
        )
        assertTrue(
            "Expected spellcasting class definitions to be loaded.",
            spellcastingDefinitions.isNotEmpty(),
        )
    }
}
