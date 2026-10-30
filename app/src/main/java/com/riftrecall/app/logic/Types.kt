package com.riftrecall.app.logic

import com.riftrecall.app.data.Card

/** One thing you can be asked about on a card. Each has its own spaced-repetition schedule. */
enum class Field(val label: String) {
    NAME("Name"),
    ABILITY("Ability"),
    COST("Energy cost"),
    STATS("Might & Power"),
    TYPE_DOMAIN("Type & Domain"),
}

enum class Mode(val title: String, val subtitle: String, val fields: List<Field>) {
    NAME("Name the card", "See the art, type the card's name.", listOf(Field.NAME)),
    ABILITY("Recall the ability", "Type what the card does, in your own words.", listOf(Field.ABILITY)),
    COST("Energy cost", "How much does it cost to play?", listOf(Field.COST)),
    STATS("Might & Power", "Recall the combat numbers.", listOf(Field.STATS)),
    TYPE_DOMAIN("Type & Domain", "Unit, Spell, Gear... and which domain(s).", listOf(Field.TYPE_DOMAIN)),
    FULL(
        "Full card",
        "Every detail, one after another.",
        listOf(Field.NAME, Field.TYPE_DOMAIN, Field.COST, Field.STATS, Field.ABILITY),
    ),
}

enum class Verdict { CORRECT, CLOSE, WRONG }

data class Grade(
    val verdict: Verdict,
    val score: Float,
    val note: String? = null,
    val missing: List<String> = emptyList(),
)

enum class Outcome { CORRECT, HINTED, MISS }

/** What the user typed / picked. Which parts are used depends on the [Field]. */
data class Answer(
    val text: String = "",
    val a: String = "",
    val b: String = "",
    val type: String? = null,
    val domains: Set<String> = emptySet(),
)

data class StepResult(
    val field: Field,
    val grade: Grade,
    val given: String,
    val expected: String,
    val hints: Int,
    val overridden: Boolean = false,
) {
    val passed: Boolean get() = grade.verdict == Verdict.CORRECT || overridden
}

data class Commit(val cardId: String, val field: Field, val outcome: Outcome)

enum class Phase { INTRO, ASK, FEEDBACK, DONE }

data class QItem(
    val card: Card,
    /** New card: show it fully first, then quiz it a few cards later. */
    val intro: Boolean,
    /** Second chance after a miss in this session - does not touch the schedule again. */
    val relearn: Boolean = false,
    val tries: Int = 0,
)

data class SessionState(
    val mode: Mode,
    val queue: List<QItem>,
    val total: Int,
    val phase: Phase = Phase.ASK,
    val steps: List<Field> = emptyList(),
    val stepIndex: Int = 0,
    val results: List<StepResult> = emptyList(),
    val hints: Int = 0,
    val answered: Int = 0,
    val perfect: Int = 0,
    val missed: List<Card> = emptyList(),
)
