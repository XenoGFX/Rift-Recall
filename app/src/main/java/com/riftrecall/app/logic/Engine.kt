package com.riftrecall.app.logic

import com.riftrecall.app.data.Card
import com.riftrecall.app.data.Entry

/** Pure, immutable session logic: every function takes a state and returns the next one. */
object Engine {
    fun stepsFor(mode: Mode, card: Card): List<Field> = mode.fields.filter { Eligibility.ok(card, it) }

    fun begin(mode: Mode, items: List<QItem>): SessionState =
        enter(SessionState(mode = mode, queue = items, total = items.size))

    private fun enter(s: SessionState): SessionState {
        var q = s.queue
        while (q.isNotEmpty() && stepsFor(s.mode, q.first().card).isEmpty()) q = q.drop(1)
        val head = q.firstOrNull() ?: return s.copy(queue = q, phase = Phase.DONE)
        return s.copy(
            queue = q,
            steps = stepsFor(s.mode, head.card),
            stepIndex = 0,
            results = emptyList(),
            hints = 0,
            phase = if (head.intro) Phase.INTRO else Phase.ASK,
        )
    }

    /** "Got it" on a new card: it gets quizzed a few cards from now. */
    fun ackIntro(s: SessionState): SessionState {
        if (s.phase != Phase.INTRO) return s
        val head = s.queue.first()
        val rest = s.queue.drop(1)
        val q = rest.toMutableList()
        q.add(minOf(3, rest.size), head.copy(intro = false))
        return enter(s.copy(queue = q))
    }

    fun useHint(s: SessionState): SessionState =
        if (s.phase == Phase.ASK && s.hints < Hints.MAX) s.copy(hints = s.hints + 1) else s

    fun submitStep(s: SessionState, answer: Answer): SessionState {
        if (s.phase != Phase.ASK) return s
        val head = s.queue.first()
        val field = s.steps[s.stepIndex]
        val results = s.results + Grader.grade(head.card, field, answer, s.hints)
        return if (s.stepIndex + 1 < s.steps.size) {
            s.copy(results = results, stepIndex = s.stepIndex + 1, hints = 0)
        } else {
            s.copy(results = results, phase = Phase.FEEDBACK, hints = 0)
        }
    }

    /** "I actually knew this" - flips a miss to a pass (and back). */
    fun toggleOverride(s: SessionState, index: Int): SessionState {
        if (s.phase != Phase.FEEDBACK || index !in s.results.indices) return s
        val r = s.results[index]
        if (r.grade.verdict == Verdict.CORRECT) return s
        val updated = s.results.toMutableList()
        updated[index] = r.copy(overridden = !r.overridden)
        return s.copy(results = updated)
    }

    fun advance(s: SessionState): Pair<SessionState, List<Commit>> {
        if (s.phase != Phase.FEEDBACK) return s to emptyList()
        val head = s.queue.first()
        val rest = s.queue.drop(1)
        val allPass = s.results.all { it.passed }

        val commits = if (head.relearn) emptyList() else s.results.map {
            Commit(
                head.card.id, it.field,
                when {
                    !it.passed -> Outcome.MISS
                    it.hints > 0 -> Outcome.HINTED
                    else -> Outcome.CORRECT
                },
            )
        }

        var q = rest
        if (!allPass && head.tries < 3) {
            val again = rest.toMutableList()
            again.add(minOf(3, rest.size), head.copy(relearn = true, tries = head.tries + 1))
            q = again
        }
        val firstTry = !head.relearn && allPass && s.results.all { it.hints == 0 }
        val next = s.copy(
            queue = q,
            answered = if (head.relearn) s.answered else s.answered + 1,
            perfect = s.perfect + if (firstTry) 1 else 0,
            missed = if (!allPass && head.card !in s.missed) s.missed + head.card else s.missed,
        )
        return enter(next) to commits
    }
}

object SessionBuilder {
    data class Counts(val due: Int, val fresh: Int)

    private class Status(val kind: Int, val priority: Long) // 0 n/a, 1 new, 2 due, 3 not yet due

    private fun status(card: Card, mode: Mode, progress: Map<String, Entry>, now: Long): Status {
        val fields = Engine.stepsFor(mode, card)
        if (fields.isEmpty()) return Status(0, 0)
        val entries = fields.map { progress[Srs.key(card.id, it)] }
        if (entries.all { it == null }) return Status(1, 0)
        val earliest = entries.minOf { it?.dueAt ?: 0L } // a never-seen field counts as due
        return if (earliest <= now) Status(2, earliest) else Status(3, earliest)
    }

    fun counts(cards: List<Card>, mode: Mode, progress: Map<String, Entry>, now: Long): Counts {
        var due = 0
        var fresh = 0
        for (c in cards) when (status(c, mode, progress, now).kind) {
            2 -> due++
            1 -> fresh++
        }
        return Counts(due, fresh)
    }

    /**
     * Due cards (most overdue first) plus a few brand-new cards, which start with a study screen.
     * If nothing is due or new, falls back to practising the cards that are due soonest.
     */
    fun build(
        cards: List<Card>,
        mode: Mode,
        progress: Map<String, Entry>,
        now: Long,
        newLimit: Int,
        maxItems: Int,
    ): List<QItem> {
        val statuses = cards.map { it to status(it, mode, progress, now) }
        val due = statuses.filter { it.second.kind == 2 }.sortedBy { it.second.priority }.map { it.first }
        val fresh = statuses.filter { it.second.kind == 1 }.map { it.first }.take(newLimit)
        val dueTake = due.take((maxItems - fresh.size).coerceAtLeast(0))
        val items = dueTake.map { QItem(it, intro = false) } + fresh.map { QItem(it, intro = true) }
        if (items.isNotEmpty()) return items

        return statuses.filter { it.second.kind == 3 }
            .sortedBy { it.second.priority }
            .take(maxItems)
            .map { QItem(it.first, intro = false) }
    }
}
