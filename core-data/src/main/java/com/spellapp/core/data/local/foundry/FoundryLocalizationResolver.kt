package com.spellapp.core.data.local.foundry

interface FoundryLocalizationResolver {
    fun resolve(key: String): String?
}

class MapFoundryLocalizationResolver(
    private val entries: Map<String, String>,
) : FoundryLocalizationResolver {
    override fun resolve(key: String): String? {
        return entries[key.trim().lowercase()]
    }
}
