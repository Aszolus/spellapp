package com.spellapp.core.data.local.foundry

import android.content.Context
import org.json.JSONObject

internal class AssetFoundryLocalizationResolver(
    context: Context,
) : FoundryLocalizationResolver {
    private val appContext = context.applicationContext

    override fun resolve(key: String): String? {
        return loadEntries()[key.trim().lowercase()]
    }

    private fun loadEntries(): Map<String, String> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: readEntries().also { cachedEntries = it }
        }
    }

    private fun readEntries(): Map<String, String> {
        val assetText = runCatching {
            appContext.assets.open(LOCALIZATION_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyMap()

        val root = JSONObject(assetText)
        val entries = root.optJSONObject("entries") ?: return emptyMap()
        return buildMap {
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = entries.optString(key).trim()
                if (value.isNotBlank()) {
                    put(key.trim().lowercase(), value)
                }
            }
        }
    }

    private companion object {
        @Volatile
        private var cachedEntries: Map<String, String>? = null

        private const val LOCALIZATION_ASSET = "foundry.localization.en.flat.json"
    }
}
