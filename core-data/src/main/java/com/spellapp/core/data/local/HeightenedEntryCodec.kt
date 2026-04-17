package com.spellapp.core.data.local

import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry
import org.json.JSONArray
import org.json.JSONObject

internal object HeightenedEntryCodec {
    private const val TYPE_ABSOLUTE = "absolute"
    private const val TYPE_STEP = "step"

    fun encode(entries: List<HeightenedEntry>): String {
        if (entries.isEmpty()) return "[]"
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            when (val trigger = entry.trigger) {
                is HeightenTrigger.Absolute -> {
                    obj.put("type", TYPE_ABSOLUTE)
                    obj.put("rank", trigger.rank)
                }
                is HeightenTrigger.Step -> {
                    obj.put("type", TYPE_STEP)
                    obj.put("increment", trigger.increment)
                }
            }
            obj.put("text", entry.text)
            array.put(obj)
        }
        return array.toString()
    }

    fun decode(json: String): List<HeightenedEntry> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val entries = mutableListOf<HeightenedEntry>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val text = obj.optString("text").orEmpty()
            val trigger = when (obj.optString("type")) {
                TYPE_ABSOLUTE -> {
                    val rank = obj.optInt("rank", -1).takeIf { it > 0 } ?: continue
                    HeightenTrigger.Absolute(rank)
                }
                TYPE_STEP -> {
                    val increment = obj.optInt("increment", -1).takeIf { it > 0 } ?: continue
                    HeightenTrigger.Step(increment)
                }
                else -> continue
            }
            entries += HeightenedEntry(trigger = trigger, text = text)
        }
        return entries
    }
}
