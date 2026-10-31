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
 * Downloads the card list from RiftHunt (free, no key, kept current with new sets) and caches the
 * cleaned list on disk so the app works offline afterwards. If RiftHunt is unreachable it falls
 * back to the Riftcodex API, which can lag behind on new sets. Card art is streamed from the
 * image URLs and cached by Coil - nothing from the game is bundled in the app.
 */
class CardRepository(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheFile get() = File(dir, "cards_v2.json")
    private val legacyFile get() = File(dir, "cards_v1.json")

    private val sourceFile get() = File(dir, "source.txt")

    /** True when the last download had to use the (possibly outdated) fallback source. */
    var usedFallback = false
        private set

    /** Why RiftHunt failed, so the app can show the real reason instead of guessing. */
    var fallbackReason: String? = null
        private set

    /** Human-readable note about where the stored cards came from (and why a fallback happened). */
    fun loadSourceInfo(): String = runCatching { sourceFile.readText() }.getOrDefault("")

    fun lastUsedFallback(): Boolean = loadSourceInfo().startsWith("FALLBACK")

    private fun read(f: File): List<Card>? =
        runCatching { json.decodeFromString<List<Card>>(f.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun loadCache(): List<Card>? = read(cacheFile)

    /** Cards from the old Riftcodex-only version of the app, used once to carry progress over. */
    fun loadLegacy(): List<Card>? = read(legacyFile)

    suspend fun download(onProgress: (page: Int, pages: Int) -> Unit): List<Card> =
        withContext(Dispatchers.IO) {
            usedFallback = false
            var reason = ""
            val raw = try {
                onProgress(0, 1)
                downloadRiftHunt()
            } catch (e: Exception) {
                usedFallback = true
                fallbackReason = e.message ?: e.javaClass.simpleName
                reason = "${e::class.simpleName}: ${e.message}".take(300)
                downloadRiftcodex(onProgress)
            }
            val cards = CardCleaner.clean(raw)
            if (cards.isEmpty()) error("The card API returned no cards")
            runCatching { cacheFile.writeText(json.encodeToString(cards)) }
            runCatching {
                sourceFile.writeText(
                    if (usedFallback) "FALLBACK (Riftcodex) because RiftHunt failed - $reason"
                    else "RiftHunt (${cards.size} cards)",
                )
            }
            cards
        }

    private fun downloadRiftHunt(): List<Card> {
        val out = ArrayList<Card>()
        var url: String? = RiftHunt.BULK_URL
        var guard = 0
        while (url != null && guard++ < 30) {
            val body = get(url)
            out += RiftHunt.parse(body, json)
            url = RiftHunt.nextPage(body, json)
        }
        check(out.isNotEmpty()) { "RiftHunt returned no cards" }
        return out
    }

    private fun downloadRiftcodex(onProgress: (page: Int, pages: Int) -> Unit): List<Card> {
        var size = 100
        var page = 1
        var pages = 1
        val all = ArrayList<ApiCard>()
        while (page <= pages) {
            val body = try {
                get("https://api.riftcodex.com/cards?page=$page&size=$size")
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
        return all.mapNotNull { it.toCard() }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
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
    /** One entry per distinct card per set: prefer the regular printing over alternate art / signature versions. */
    fun clean(cards: List<Card>): List<Card> =
        cards.groupBy { Matching.normalize(it.name) + "|" + it.type + "|" + it.setId }
            .values
            .map { group -> group.minWith(compareBy<Card>({ it.special }, { it.setId }, { it.number })) }
            .sortedWith(compareBy({ it.setId }, { it.number }))
}
