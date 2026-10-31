package com.riftrecall.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Kept for reference/tests: a set + collector-number key. Not unique across runes, tokens and promos. */
fun stableId(set: String, number: String): String {
    val n = number.trim()
    val norm = if (n.isNotEmpty() && n.all(Char::isDigit)) n.toInt().toString() else n.lowercase()
    return "${set.uppercase()}-$norm"
}

/**
 * Parser for https://api.rifthunt.com/bulk/cards (free, no key).
 *
 * The bulk export is `{ "fetched", "source", "sets": [...], "cards": [...] }` and uses short field
 * names (num, text, flavour, imgUrl, alt/sig/over...). The documented search API uses a different
 * shape (collector_number, rules_text, images{...}, variant...). Both are accepted here.
 */
object RiftHunt {
    const val BULK_URL = "https://api.rifthunt.com/bulk/cards"

    fun parse(body: String, json: Json = Json): List<Card> {
        val root = json.parseToJsonElement(body)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["cards"] ?: root["data"] ?: root["items"]) as? JsonArray
            else -> null
        } ?: error("Unexpected response format from RiftHunt")
        return array.mapNotNull { (it as? JsonObject)?.let(::toCard) }
    }

    /**
     * URL of the next page if the response is a paged list envelope (`has_more` + `next_page`);
     * null for the bulk export, which comes back in one piece.
     */
    fun nextPage(body: String, json: Json = Json): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        if (!root.flag("has_more")) return null
        val next = root.str("next_page") ?: return null
        return if (next.startsWith("/")) "https://api.rifthunt.com$next" else next
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()

    private fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }

    private fun toCard(o: JsonObject): Card? {
        val name = (o.str("oracle_name") ?: o.str("name"))?.trim() ?: return null
        val images = o["images"] as? JsonObject
        val image = o.str("imgUrl")
            ?: images?.str("normal") ?: images?.str("large") ?: images?.str("small")
            ?: return null
        val set = o.str("set")?.uppercase().orEmpty()
        val numberRaw = o.str("collector_number") ?: o.int("num")?.toString().orEmpty()
        val type = o.str("type")?.let(::cap).orEmpty()
        val rarity = o.str("rarity").orEmpty()
        // The card's own id ("rad-001-167", "rad-r01") is unique; set + number is not (runes,
        // tokens and promos reuse numbers). It's also the same id Riftcodex calls riftbound_id.
        val id = (o.str("riftboundId") ?: o.str("riftbound_id") ?: o.str("id"))?.lowercase() ?: return null
        val orientation = o.str("orientation")

        return Card(
            id = id,
            name = name,
            code = o.str("riftbound_id") ?: o.str("riftboundId") ?: o.str("id").orEmpty(),
            number = numberRaw.filter(Char::isDigit).toIntOrNull() ?: 0,
            setId = set,
            setLabel = o.str("set_name") ?: o.str("setName").orEmpty(),
            type = type,
            supertype = o.str("supertype"),
            rarity = cap(rarity),
            domains = o.strList("domains").map(::cap),
            energy = o.int("energy"),
            might = o.int("might"),
            power = o.int("power"),
            text = o.str("rules_text") ?: o.str("text").orEmpty(),
            flavour = o.str("flavor_text") ?: o.str("flavour"),
            imageUrl = image,
            artist = o.str("artist"),
            tags = o.strList("tags"),
            landscape = orientation.equals("landscape", ignoreCase = true) || type.equals("Battlefield", ignoreCase = true),
            special = o.str("variant") != null || o.flag("alt") || o.flag("sig") || o.flag("over") ||
                rarity.equals("showcase", true) || rarity.equals("promo", true),
        )
    }
}

/** Keeps study progress when card ids change between data sources. */
object ProgressMigration {
    fun remap(progress: Map<String, Entry>, legacy: List<Card>, current: List<Card>): Map<String, Entry> {
        if (progress.isEmpty() || legacy.isEmpty()) return progress
        val currentIds = current.mapTo(HashSet()) { it.id }
        val oldToNew = legacy.filter { it.code.isNotBlank() }.associate { it.id to it.code.lowercase() }
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
