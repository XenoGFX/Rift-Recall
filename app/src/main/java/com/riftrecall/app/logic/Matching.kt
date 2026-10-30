package com.riftrecall.app.logic

import com.riftrecall.app.data.Card
import java.text.Normalizer

/** Forgiving answer checking: typos in names, paraphrased abilities, blanks for "none". */
object Matching {
    private val STOP = setOf(
        "a", "an", "the", "of", "to", "in", "on", "at", "and", "or", "is", "are", "be", "it", "its",
        "that", "this", "with", "as", "for",
    )

    fun normalize(s: String): String {
        val noMarks = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noMarks.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun nameVariants(name: String): List<String> {
        val withoutParen = name.replace(Regex("\\([^)]*\\)"), " ")
        return listOf(name, withoutParen).map(::normalize).filter { it.isNotEmpty() }.distinct()
    }

    fun gradeName(input: String, name: String): Grade {
        val given = normalize(input)
        if (given.isEmpty()) return Grade(Verdict.WRONG, 0f)
        val variants = nameVariants(name)
        if (given in variants) return Grade(Verdict.CORRECT, 1f)

        var best = Int.MAX_VALUE
        var bestLen = 1
        for (v in variants) {
            val d = levenshtein(given, v)
            if (d < best) {
                best = d
                bestLen = maxOf(v.length, given.length)
            }
        }
        val ref = variants.minOf { it.length }
        val tolerance = when {
            ref <= 4 -> 0
            ref <= 9 -> 1
            else -> 2
        }
        val similarity = 1f - best.toFloat() / bestLen
        return when {
            best <= tolerance -> Grade(Verdict.CORRECT, similarity, note = "Small typo - still counts")
            similarity >= 0.7f -> Grade(Verdict.CLOSE, similarity)
            else -> Grade(Verdict.WRONG, similarity)
        }
    }

    fun gradeNumber(input: String, expected: Int?): Grade {
        val t = input.trim()
        if (t.isEmpty()) return if (expected == null) Grade(Verdict.CORRECT, 1f) else Grade(Verdict.WRONG, 0f)
        val n = t.toIntOrNull() ?: return Grade(Verdict.WRONG, 0f)
        return if (n == expected) Grade(Verdict.CORRECT, 1f) else Grade(Verdict.WRONG, 0f)
    }

    /** Ability text without reminder text in (parentheses), with :rb_icon: tokens turned into words. */
    fun cleanAbility(raw: String): String =
        raw.replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex(":rb_([a-z0-9_]+):")) { it.groupValues[1].replace('_', ' ') + " " }

    fun contentWords(text: String): List<String> =
        normalize(cleanAbility(text)).split(' ').filter { it.isNotEmpty() && it !in STOP }

    private fun wordMatch(a: String, b: String): Boolean =
        a == b || (a.length >= 5 && b.length >= 5 && levenshtein(a, b) <= 1)

    fun gradeAbility(input: String, expected: String): Grade {
        val exp = contentWords(expected)
        if (exp.isEmpty()) return Grade(Verdict.CORRECT, 1f)
        val inp = contentWords(input)
        if (inp.isEmpty()) return Grade(Verdict.WRONG, 0f, missing = exp.distinct())

        val used = BooleanArray(inp.size)
        val missing = ArrayList<String>()
        var hit = 0
        for (w in exp) {
            val k = inp.indices.firstOrNull { !used[it] && wordMatch(inp[it], w) }
            if (k != null) {
                used[k] = true
                hit++
            } else {
                missing += w
            }
        }
        val coverage = hit.toFloat() / exp.size
        val precision = (hit.toFloat() / inp.size).coerceIn(0f, 1f)
        val score = coverage * (0.75f + 0.25f * precision)
        val verdict = when {
            score >= 0.8f -> Verdict.CORRECT
            score >= 0.5f -> Verdict.CLOSE
            else -> Verdict.WRONG
        }
        return Grade(verdict, score, missing = missing.distinct())
    }
}

object AbilityText {
    /** Readable ability text: icon tokens become ‹Might›, ‹Energy 2› etc. */
    fun display(raw: String): String =
        raw.replace(Regex(":rb_([a-z0-9_]+):")) {
            val word = it.groupValues[1].replace('_', ' ')
            "‹" + word.replaceFirstChar { c -> c.uppercase() } + "›"
        }
}

object Eligibility {
    fun ok(card: Card, field: Field): Boolean = when (field) {
        Field.NAME -> true
        Field.ABILITY -> Matching.contentWords(card.text).isNotEmpty()
        Field.COST -> card.energy != null
        Field.STATS -> card.might != null || card.power != null
        Field.TYPE_DOMAIN -> card.type.isNotBlank()
    }
}

object Hints {
    const val MAX = 2

    fun forField(card: Card, field: Field, level: Int): String = when (field) {
        Field.NAME -> namePattern(card.name, level)
        Field.ABILITY -> abilityHint(card.text, level)
        Field.COST -> {
            val e = card.energy ?: 0
            if (level <= 1) (if (e % 2 == 0) "An even number" else "An odd number")
            else "Between ${maxOf(0, e - 1)} and ${e + 1}"
        }
        Field.STATS -> {
            if (level <= 1) "It's a ${card.type}${if (card.domains.isNotEmpty()) " (" + card.domains.joinToString(", ") + ")" else ""}"
            else "Might is between ${maxOf(0, (card.might ?: 0) - 1)} and ${(card.might ?: 0) + 1}"
        }
        Field.TYPE_DOMAIN -> {
            if (level <= 1) "The type starts with \"${card.type.firstOrNull() ?: '?'}\""
            else "${card.domains.size} domain(s)"
        }
    }

    fun namePattern(name: String, level: Int): String =
        name.split(' ').joinToString("   ") { w ->
            w.mapIndexed { i, c ->
                when {
                    !c.isLetterOrDigit() -> c.toString()
                    level >= 2 && i == 0 -> c.toString()
                    else -> "_"
                }
            }.joinToString(" ")
        }

    private fun abilityHint(text: String, level: Int): String {
        val words = Matching.cleanAbility(text).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return "No ability text"
        return if (level <= 1) {
            "About ${words.size} words, starting with \"${words.first()}\""
        } else {
            words.take(maxOf(3, words.size / 3)).joinToString(" ") + " ..."
        }
    }
}

object Grader {
    fun grade(card: Card, field: Field, a: Answer, hints: Int): StepResult = when (field) {
        Field.NAME -> StepResult(field, Matching.gradeName(a.text, card.name), a.text.trim(), card.name, hints)
        Field.ABILITY -> StepResult(
            field, Matching.gradeAbility(a.text, card.text), a.text.trim(), AbilityText.display(card.text), hints,
        )
        Field.COST -> StepResult(
            field, Matching.gradeNumber(a.text, card.energy),
            a.text.trim().ifEmpty { "-" }, card.energy?.toString() ?: "-", hints,
        )
        Field.STATS -> {
            val gm = Matching.gradeNumber(a.a, card.might)
            val gp = Matching.gradeNumber(a.b, card.power)
            val ok = gm.verdict == Verdict.CORRECT && gp.verdict == Verdict.CORRECT
            val missing = buildList {
                if (gm.verdict != Verdict.CORRECT) add("might")
                if (gp.verdict != Verdict.CORRECT) add("power")
            }
            StepResult(
                field,
                Grade(if (ok) Verdict.CORRECT else Verdict.WRONG, if (ok) 1f else 0f, missing = missing),
                "Might ${a.a.trim().ifEmpty { "-" }} / Power ${a.b.trim().ifEmpty { "-" }}",
                "Might ${card.might ?: "-"} / Power ${card.power ?: "-"}",
                hints,
            )
        }
        Field.TYPE_DOMAIN -> {
            val typeOk = a.type?.equals(card.type, ignoreCase = true) == true
            val domOk = a.domains.map { it.lowercase() }.toSet() == card.domains.map { it.lowercase() }.toSet()
            val missing = buildList {
                if (!typeOk) add("type")
                if (!domOk) add("domain")
            }
            val ok = typeOk && domOk
            StepResult(
                field,
                Grade(if (ok) Verdict.CORRECT else Verdict.WRONG, if (ok) 1f else 0f, missing = missing),
                (a.type ?: "-") + " / " + (a.domains.joinToString(", ").ifEmpty { "no domain" }),
                card.type + " / " + (card.domains.joinToString(", ").ifEmpty { "no domain" }),
                hints,
            )
        }
    }
}
