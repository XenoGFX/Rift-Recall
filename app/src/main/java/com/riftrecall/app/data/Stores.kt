package com.riftrecall.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Spaced-repetition state for one (card, field) pair. */
@Serializable
data class Entry(
    val box: Int = 0,
    val dueAt: Long = 0L,
    val seen: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
    val lastAt: Long = 0L,
)

@Serializable
data class ProgressFile(val entries: Map<String, Entry> = emptyMap())

class ProgressStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(dir, "progress_v1.json")

    fun load(): Map<String, Entry> =
        runCatching { json.decodeFromString<ProgressFile>(file.readText()).entries }.getOrDefault(emptyMap())

    fun save(entries: Map<String, Entry>) {
        runCatching {
            val tmp = File(dir, "progress_v1.json.tmp")
            tmp.writeText(json.encodeToString(ProgressFile(entries)))
            tmp.renameTo(file)
        }
    }
}

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            newPerSession = sp.getInt("new", d.newPerSession),
            sessionSize = sp.getInt("size", d.sessionSize),
            sets = (sp.getStringSet("sets", emptySet()) ?: emptySet()).toSet(),
            includeExtras = sp.getBoolean("extras", d.includeExtras),
            portrait = loadCrop("p", d.portrait),
            landscape = loadCrop("l", d.landscape),
            cropReviewed = sp.getBoolean("reviewed", false),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putInt("new", s.newPerSession)
            .putInt("size", s.sessionSize)
            .putStringSet("sets", s.sets.toSet())
            .putBoolean("extras", s.includeExtras)
            .putBoolean("reviewed", s.cropReviewed)
            .apply()
        saveCrop("p", s.portrait)
        saveCrop("l", s.landscape)
    }

    private fun loadCrop(k: String, d: Crop) = Crop(
        sp.getFloat("${k}_l", d.l), sp.getFloat("${k}_t", d.t),
        sp.getFloat("${k}_r", d.r), sp.getFloat("${k}_b", d.b),
    )

    private fun saveCrop(k: String, c: Crop) {
        sp.edit()
            .putFloat("${k}_l", c.l).putFloat("${k}_t", c.t)
            .putFloat("${k}_r", c.r).putFloat("${k}_b", c.b)
            .apply()
    }
}

object Pool {
    fun isExtra(c: Card): Boolean =
        c.type.contains("rune", true) || c.type.contains("token", true) ||
            (c.supertype?.contains("token", true) == true)

    /** The cards the user has chosen to study (set filter + runes/tokens switch). */
    fun filter(all: List<Card>, s: Settings): List<Card> =
        all.filter { (s.sets.isEmpty() || it.setId in s.sets) && (s.includeExtras || !isExtra(it)) }
}
