package com.riftrecall.app.data

import com.riftrecall.app.logic.Matching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int) : Exception("Server returned HTTP $code")

/**
 * Downloads the card list from the open Riftcodex API (no key needed) and caches the cleaned
 * list on disk so the app works offline afterwards. Card art itself is streamed from the
 * image URLs and cached by Coil - nothing from the game is bundled in the app.
 */
class CardRepository(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheFile get() = File(dir, "cards_v1.json")

    fun loadCache(): List<Card>? =
        runCatching { json.decodeFromString<List<Card>>(cacheFile.readText()) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    suspend fun download(onProgress: (page: Int, pages: Int) -> Unit): List<Card> =
        withContext(Dispatchers.IO) {
            var size = 100
            var page = 1
            var pages = 1
            val all = ArrayList<ApiCard>()
            while (page <= pages) {
                val body = try {
                    fetch(page, size)
                } catch (e: HttpException) {
                    // Some servers cap page size - retry the first page smaller.
                    if (page == 1 && size > 50 && e.code in 400..499) {
                        size = 50
                        continue
                    } else throw e
                }
                val parsed = json.decodeFromString<ApiPage>(body)
                all += parsed.items
                pages = parsed.pages.coerceAtLeast(1)
                onProgress(page, pages)
                if (parsed.items.isEmpty()) break
                page++
            }
            val cards = CardCleaner.clean(all.mapNotNull { it.toCard() })
            if (cards.isEmpty()) error("The card API returned no cards")
            runCatching { cacheFile.writeText(json.encodeToString(cards)) }
            cards
        }

    private fun fetch(page: Int, size: Int): String {
        val conn = URL("https://api.riftcodex.com/cards?page=$page&size=$size").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "RiftRecall/1.0 (personal study app)")
            val code = conn.responseCode
            if (code != 200) throw HttpException(code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

object CardCleaner {
    /** One entry per distinct card: prefer the regular printing over alternate art / signature versions. */
    fun clean(cards: List<Card>): List<Card> =
        cards.groupBy { Matching.normalize(it.name) + "|" + it.type }
            .values
            .map { group -> group.minWith(compareBy<Card>({ it.special }, { it.setId }, { it.number })) }
            .sortedWith(compareBy({ it.setId }, { it.number }))
}
