package com.riftrecall.app.logic

import com.riftrecall.app.data.Card
import com.riftrecall.app.data.Entry

/**
 * Leitner-style spaced repetition. Every (card, field) pair sits in a box 0..6; a correct answer
 * moves it up and schedules it further out, a miss drops it back so it returns soon.
 */
object Srs {
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN

    /** Delay before the next review, indexed by box. */
    val intervals = longArrayOf(0L, 10 * MIN, DAY, 3 * DAY, 7 * DAY, 16 * DAY, 35 * DAY)
    val MAX_BOX = intervals.size - 1

    fun key(cardId: String, field: Field) = "$cardId|${field.name}"

    fun apply(e: Entry?, outcome: Outcome, now: Long): Entry {
        val old = e ?: Entry()
        return when (outcome) {
            Outcome.CORRECT -> {
                // Getting it right cold on first sight earns a head start.
                val box = if (e == null) 2 else minOf(old.box + 1, MAX_BOX)
                old.copy(
                    box = box, dueAt = now + intervals[box], seen = old.seen + 1,
                    correct = old.correct + 1, streak = old.streak + 1, lastAt = now,
                )
            }
            Outcome.HINTED -> old.copy(
                dueAt = now + intervals[maxOf(old.box, 1)], seen = old.seen + 1, streak = 0, lastAt = now,
            )
            Outcome.MISS -> old.copy(
                box = if (old.box >= 4) old.box - 2 else 0,
                dueAt = now, seen = old.seen + 1, streak = 0, lastAt = now,
            )
        }
    }

    fun mastery(e: Entry?): Float = if (e == null) 0f else e.box.toFloat() / MAX_BOX

    /** Average mastery over every field that applies to this card. */
    fun cardMastery(card: Card, progress: Map<String, Entry>): Float {
        val fields = Field.values().filter { Eligibility.ok(card, it) }
        if (fields.isEmpty()) return 0f
        return fields.map { mastery(progress[key(card.id, it)]) }.average().toFloat()
    }
}
