package com.riftrecall.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A card as the app uses it (cached on disk as JSON). */
@Serializable
data class Card(
    val id: String,
    val name: String,
    val code: String = "",
    val number: Int = 0,
    val setId: String = "",
    val setLabel: String = "",
    val type: String = "",
    val supertype: String? = null,
    val rarity: String = "",
    val domains: List<String> = emptyList(),
    val energy: Int? = null,
    val might: Int? = null,
    val power: Int? = null,
    val text: String = "",
    val flavour: String? = null,
    val imageUrl: String,
    val artist: String? = null,
    val tags: List<String> = emptyList(),
    val landscape: Boolean = false,
    /** alternate art / overnumbered / signature printing */
    val special: Boolean = false,
)

/** Fractions (0..1) of the full card image that contain the artwork. */
data class Crop(val l: Float, val t: Float, val r: Float, val b: Float)

data class Settings(
    val newPerSession: Int = 8,
    val sessionSize: Int = 20,
    /** Empty = all sets. */
    val sets: Set<String> = emptySet(),
    val includeExtras: Boolean = false,
    val portrait: Crop = DEFAULT_PORTRAIT,
    val landscape: Crop = DEFAULT_LANDSCAPE,
    val cropReviewed: Boolean = false,
) {
    fun cropFor(card: Card): Crop = if (card.landscape) landscape else portrait

    companion object {
        // Estimates - the in-app calibration screen lets the user fix these visually.
        val DEFAULT_PORTRAIT = Crop(0.06f, 0.11f, 0.94f, 0.60f)
        val DEFAULT_LANDSCAPE = Crop(0.04f, 0.06f, 0.96f, 0.94f)
    }
}

// ---------------------------------------------------------------------------
// Riftcodex API DTOs (https://api.riftcodex.com/cards) - everything optional.
// ---------------------------------------------------------------------------

@Serializable
data class ApiPage(
    val items: List<ApiCard> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 0,
    val pages: Int = 1,
)

@Serializable
data class ApiCard(
    val id: String = "",
    val name: String = "",
    @SerialName("riftbound_id") val riftboundId: String = "",
    @SerialName("collector_number") val collectorNumber: Int = 0,
    val attributes: ApiAttrs? = null,
    val classification: ApiClass? = null,
    val text: ApiText? = null,
    val set: ApiSet? = null,
    val media: ApiMedia? = null,
    val tags: List<String>? = null,
    val orientation: String? = null,
    val metadata: ApiMeta? = null,
)

@Serializable data class ApiAttrs(val energy: Int? = null, val might: Int? = null, val power: Int? = null)

@Serializable
data class ApiClass(
    val type: String? = null,
    val supertype: String? = null,
    val rarity: String? = null,
    val domain: List<String>? = null,
)

@Serializable data class ApiText(val rich: String? = null, val plain: String? = null, val flavour: String? = null)

@Serializable data class ApiSet(@SerialName("set_id") val setId: String? = null, val label: String? = null)

@Serializable data class ApiMedia(@SerialName("image_url") val imageUrl: String? = null, val artist: String? = null)

@Serializable
data class ApiMeta(
    @SerialName("alternate_art") val alternateArt: Boolean? = null,
    val overnumbered: Boolean? = null,
    val signature: Boolean? = null,
)

internal fun htmlToText(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>\\s*<p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()

fun ApiCard.toCard(): Card? {
    val image = media?.imageUrl
    if (name.isBlank() || image.isNullOrBlank()) return null
    val rich = text?.rich?.takeIf { it.isNotBlank() }?.let(::htmlToText)
    return Card(
        id = id.ifBlank { riftboundId },
        name = name.trim(),
        code = riftboundId,
        number = collectorNumber,
        setId = set?.setId.orEmpty().uppercase(),
        setLabel = set?.label.orEmpty(),
        type = classification?.type.orEmpty(),
        supertype = classification?.supertype?.takeIf { it.isNotBlank() },
        rarity = classification?.rarity.orEmpty(),
        domains = classification?.domain.orEmpty(),
        energy = attributes?.energy,
        might = attributes?.might,
        power = attributes?.power,
        text = rich ?: text?.plain.orEmpty(),
        flavour = text?.flavour?.takeIf { it.isNotBlank() },
        imageUrl = image,
        artist = media?.artist,
        tags = tags.orEmpty(),
        landscape = orientation.equals("landscape", ignoreCase = true),
        special = metadata?.alternateArt == true || metadata?.overnumbered == true || metadata?.signature == true,
    )
}
