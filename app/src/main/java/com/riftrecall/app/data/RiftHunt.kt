package com.riftrecall.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Stable card id that doesn't depend on which API the card came from: "OGN-9", "SFD-t01". */
fun stableId(set: String, number: String): String {
    val n = number.trim()
    val norm = if (n.isNotEmpty() && n.all(Char::isDigit)) n.toInt().toString() else n.lowercase()
    return "${set.uppercase()}-$norm"
}

/**
 * Parser for https://api.rifthunt.com/bulk/cards (free, no key). Written defensively because the
 * exact response wrapper isn't documented: a bare array or an object holding the array both work.
 */
object RiftHunt {
    const val BULK_URL = "https://api.rifthunt.com/bulk/cards"

    fun parse(body: String, json: Json = Json): List<Card> {
        val root = json.parseToJsonElement(body)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] ?: root["cards"] ?: root["items"]) as? JsonArray
            else -> null
        } ?: error("Unexpected response format from RiftHunt")
        return array.mapNotNull { (it as? JsonObject)?.let(::toCard) }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()

    private fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }

    private fun toCard(o: JsonObject): Card? {
        val name = (o.str("oracle_name") ?: o.str("name"))?.trim() ?: return null
        val images = o["images"] as? JsonObject
        val image = images?.str("normal") ?: images?.str("large") ?: images?.str("small") ?: return null
        val set = o.str("set")?.uppercase().orEmpty()
        val numberRaw = o.str("collector_number").orEmpty()
        val type = o.str("type")?.let(::cap).orEmpty()
        val rarity = o.str("rarity").orEmpty()
        val variant = o.str("variant")
        val id = if (set.isNotEmpty() && numberRaw.isNotEmpty()) stableId(set, numberRaw) else o.str("id") ?: return null

        return Card(
            id = id,
            name = name,
            code = o.str("riftbound_id").orEmpty(),
            number = numberRaw.filter(Char::isDigit).toIntOrNull() ?: 0,
            setId = set,
            setLabel = o.str("set_name").orEmpty(),
            type = type,
            supertype = null,
            rarity = cap(rarity),
            domains = o.strList("domains").map(::cap),
            energy = o.int("energy"),
            might = o.int("might"),
            power = o.int("power"),
            text = o.str("rules_text").orEmpty(),
            flavour = o.str("flavor_text"),
            imageUrl = image,
            artist = o.str("artist"),
            tags = o.strList("tags"),
            landscape = type.equals("Battlefield", ignoreCase = true),
            special = variant != null || rarity.equals("showcase", true) || rarity.equals("promo", true),
        )
    }
}

/** Keeps study progress when card ids change between data sources. */
object ProgressMigration {
    fun remap(progress: Map<String, Entry>, legacy: List<Card>, current: List<Card>): Map<String, Entry> {
        if (progress.isEmpty() || legacy.isEmpty()) return progress
        val currentIds = current.mapTo(HashSet()) { it.id }
        val oldToNew = legacy.associate { it.id to stableId(it.setId, it.number.toString()) }
        val out = HashMap<String, Entry>()
        for ((k, v) in progress) {
            val sep = k.lastIndexOf('|')
            if (sep < 0) continue
            val oldId = k.substring(0, sep)
            val newId = if (oldId in currentIds) oldId else oldToNew[oldId]?.takeIf { it in currentIds } ?: oldId
            val newKey = newId + k.substring(sep)
            val existing = out[newKey]
            if (existing == null || v.seen > existing.seen) out[newKey] = v
        }
        return out
    }
}
