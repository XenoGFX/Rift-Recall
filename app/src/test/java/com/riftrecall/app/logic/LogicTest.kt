package com.riftrecall.app.logic

import com.riftrecall.app.data.Card
import com.riftrecall.app.data.Entry
import com.riftrecall.app.data.ProgressMigration
import com.riftrecall.app.data.RiftHunt
import com.riftrecall.app.data.stableId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun card(
        id: String = "1",
        name: String = "Master Yi - Honed",
        text: String = "[Ganking] (I can move from battlefield to battlefield.)\nI enter ready.",
        energy: Int? = 7,
        might: Int? = 6,
        power: Int? = 1,
    ) = Card(
        id = id, name = name, type = "Unit", domains = listOf("Body"),
        energy = energy, might = might, power = power, text = text, imageUrl = "https://example.invalid/$id.png",
    )

    @Test fun exactAndTypoNames() {
        assertEquals(Verdict.CORRECT, Matching.gradeName("master yi honed", "Master Yi - Honed").verdict)
        assertEquals(Verdict.CORRECT, Matching.gradeName("Master Yi Honde", "Master Yi - Honed").verdict)
        assertEquals(Verdict.WRONG, Matching.gradeName("Vi", "Master Yi - Honed").verdict)
        assertEquals(Verdict.WRONG, Matching.gradeName("", "Master Yi - Honed").verdict)
    }

    @Test fun starterSuffixIsOptional() {
        assertEquals(
            Verdict.CORRECT,
            Matching.gradeName("Master Yi Wuju Bladesman", "Master Yi - Wuju Bladesman (Starter)").verdict,
        )
    }

    @Test fun abilityIgnoresReminderTextAndWordOrderNoise() {
        val g = Matching.gradeAbility("Ganking, I enter ready", card().text)
        assertEquals(Verdict.CORRECT, g.verdict)
        assertEquals(Verdict.WRONG, Matching.gradeAbility("draw two cards", card().text).verdict)
    }

    @Test fun abilityIconTokensBecomeWords() {
        val t = "While you have 8+ runes, I have +4 :rb_might:."
        assertEquals(Verdict.CORRECT, Matching.gradeAbility("while you have 8 runes I have +4 might", t).verdict)
        assertTrue(AbilityText.display(t).contains("‹Might›"))
    }

    @Test fun numbersAndBlanks() {
        assertEquals(Verdict.CORRECT, Matching.gradeNumber("7", 7).verdict)
        assertEquals(Verdict.WRONG, Matching.gradeNumber("6", 7).verdict)
        assertEquals(Verdict.CORRECT, Matching.gradeNumber("", null).verdict)
        assertEquals(Verdict.WRONG, Matching.gradeNumber("", 3).verdict)
    }

    @Test fun srsMovesUpOnCorrectAndDownOnMiss() {
        val first = Srs.apply(null, Outcome.CORRECT, 0L)
        assertEquals(2, first.box)
        val up = Srs.apply(first, Outcome.CORRECT, 0L)
        assertEquals(3, up.box)
        val down = Srs.apply(Entry(box = 5), Outcome.MISS, 0L)
        assertEquals(3, down.box)
        assertEquals(0, Srs.apply(Entry(box = 2), Outcome.MISS, 0L).box)
        assertEquals(2, Srs.apply(Entry(box = 2), Outcome.HINTED, 0L).box)
    }

    @Test fun builderPutsDueFirstThenIntroducesNewCards() {
        val a = card("a")
        val b = card("b")
        val c = card("c")
        val progress = mapOf(Srs.key("a", Field.NAME) to Entry(box = 1, dueAt = 100L))
        val items = SessionBuilder.build(listOf(a, b, c), Mode.NAME, progress, now = 1_000L, newLimit = 1, maxItems = 10)
        assertEquals(listOf("a", "b"), items.map { it.card.id })
        assertFalse(items[0].intro)
        assertTrue(items[1].intro)
    }

    @Test fun fullModeSkipsInapplicableFields() {
        val spell = card(energy = 2, might = null, power = null, text = "")
        val steps = Engine.stepsFor(Mode.FULL, spell)
        assertEquals(listOf(Field.NAME, Field.TYPE_DOMAIN, Field.COST), steps)
    }

    @Test fun sessionFlowIntroThenQuizThenRelearnOnMiss() {
        val x = card("x")
        var s = Engine.begin(Mode.NAME, listOf(QItem(x, intro = true)))
        assertEquals(Phase.INTRO, s.phase)

        s = Engine.ackIntro(s)
        assertEquals(Phase.ASK, s.phase)

        s = Engine.submitStep(s, Answer(text = "totally wrong"))
        assertEquals(Phase.FEEDBACK, s.phase)
        val (afterMiss, commits) = Engine.advance(s)
        assertEquals(listOf(Outcome.MISS), commits.map { it.outcome })
        assertEquals(1, afterMiss.queue.size)
        assertTrue(afterMiss.queue[0].relearn)

        var r = Engine.submitStep(afterMiss, Answer(text = "Master Yi Honed"))
        val (done, commits2) = Engine.advance(r)
        assertTrue(commits2.isEmpty()) // relearn pass doesn't touch the schedule
        assertEquals(Phase.DONE, done.phase)
        assertEquals(0, done.perfect)
        assertEquals(1, done.missed.size)
    }

    @Test fun overrideCountsAsPass() {
        val x = card("x")
        var s = Engine.begin(Mode.NAME, listOf(QItem(x, intro = false)))
        s = Engine.submitStep(s, Answer(text = "nope"))
        s = Engine.toggleOverride(s, 0)
        val (done, commits) = Engine.advance(s)
        assertEquals(listOf(Outcome.CORRECT), commits.map { it.outcome })
        assertEquals(Phase.DONE, done.phase)
    }

    @Test fun parsesRiftHuntBulkWithEitherWrapper() {
        val one = """{"id":"u1","oracle_name":"Trove Golem","name":"Trove Golem","type":"Unit","domains":["order"],
            "energy":8,"power":2,"might":9,"rules_text":"When you play me, play four Gold gear tokens exhausted.",
            "set":"sfd","set_name":"Spiritforged","collector_number":"174","rarity":"rare","variant":null,
            "images":{"small":"https://x/s.webp","normal":"https://x/n.webp","large":"https://x/l.webp"}}"""
        val fromArray = RiftHunt.parse("[$one]")
        val fromWrapper = RiftHunt.parse("""{"data":[$one]}""")
        assertEquals(1, fromArray.size)
        assertEquals(fromArray, fromWrapper)
        val c = fromArray[0]
        assertEquals("SFD-174", c.id)
        assertEquals(listOf("Order"), c.domains)
        assertEquals(8, c.energy)
        assertEquals("https://x/n.webp", c.imageUrl)
        assertFalse(c.special)
    }

    @Test fun progressCarriesOverToNewIds() {
        val old = card(id = "mongo123").copy(setId = "SFD", number = 174)
        val new = card(id = "SFD-174").copy(setId = "SFD", number = 174)
        val progress = mapOf("mongo123|NAME" to Entry(box = 3, seen = 4))
        val out = ProgressMigration.remap(progress, listOf(old), listOf(new))
        assertEquals(3, out["SFD-174|NAME"]?.box)
        assertEquals("OGN-9", stableId("ogn", "009"))
    }
}
