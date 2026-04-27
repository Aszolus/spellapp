package com.spellapp.core.rules.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

class BoundaryFitnessKonsistTest {
    @Test
    fun `app navigation entrypoints stay repository free`() {
        Konsist.scopeFromModule("app")
            .files
            .assertFalse(
                additionalMessage = "Keep repositories inside AppContainer and feature factory providers; navigation entrypoints should depend on providers or view-model factories only.",
            ) { file ->
                val path = file.path.normalizedPath()
                val isNavigationEntrypoint = path.endsWith("/MainActivity.kt") ||
                    path.endsWith("/SpellApp.kt") ||
                    path.endsWith("/SpellAppNavGraph.kt")
                isNavigationEntrypoint && hasCoreDataRepositoryImport(file.text)
            }
    }

    @Test
    fun `character build package does not depend on prepared spellcasting package`() {
        Konsist.scopeFromModule("feature-character")
            .files
            .assertFalse(
                additionalMessage = "Top-level character/build files may coordinate with shared spellcasting use cases, but prepared casting must stay under spellcasting.prepared.",
            ) { file ->
                val path = file.path.normalizedPath()
                val isCharacterBuildFile = path.contains("/src/main/") &&
                    path.contains("/com/spellapp/feature/character/") &&
                    !path.contains("/spellcasting/")
                isCharacterBuildFile &&
                    file.text.contains("import com.spellapp.feature.character.spellcasting.prepared.")
            }
    }

    @Test
    fun `shared spellcasting package does not depend on prepared spellcasting package`() {
        Konsist.scopeFromModule("feature-character")
            .files
            .assertFalse(
                additionalMessage = "Shared spellcasting should remain reusable across future casting styles; it cannot depend on prepared-only package details.",
            ) { file ->
                val path = file.path.normalizedPath()
                val isSharedSpellcastingFile = path.contains("/src/main/") &&
                    path.contains("/com/spellapp/feature/character/spellcasting/") &&
                    !path.contains("/spellcasting/prepared/")
                isSharedSpellcastingFile &&
                    file.text.contains("import com.spellapp.feature.character.spellcasting.prepared.")
            }
    }

    @Test
    fun `spell catalog module does not depend on character spellcasting packages`() {
        Konsist.scopeFromModule("feature-spells")
            .files
            .assertFalse(
                additionalMessage = "Spell catalog code should stay isolated from character spellcasting implementation packages.",
            ) { file ->
                val isProductionFile = file.path.normalizedPath().contains("/src/main/")
                isProductionFile &&
                    file.text.contains("import com.spellapp.feature.character.spellcasting")
            }
    }

    @Test
    fun `core rules production files do not use the old shared bucket package`() {
        Konsist.scopeFromModule("core-rules")
            .files
            .assertFalse(
                additionalMessage = "New rules code should live in domain packages such as core.rules.spellcasting instead of the old catch-all core.rules package.",
            ) { file ->
                val isProductionFile = file.path.normalizedPath().contains("/src/main/")
                isProductionFile && BASE_CORE_RULES_PACKAGE_REGEX.containsMatchIn(file.text)
            }
    }

    private fun String.normalizedPath(): String = replace('\\', '/')

    private fun hasCoreDataRepositoryImport(fileText: String): Boolean {
        return CORE_DATA_REPOSITORY_IMPORT_REGEX.containsMatchIn(fileText)
    }

    private companion object {
        private val CORE_DATA_REPOSITORY_IMPORT_REGEX =
            Regex("""^\s*import\s+com\.spellapp\.core\.data\..*Repository\b""", RegexOption.MULTILINE)
        private val BASE_CORE_RULES_PACKAGE_REGEX =
            Regex("""^\s*package\s+com\.spellapp\.core\.rules\s*$""", RegexOption.MULTILINE)
    }
}
