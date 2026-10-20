package es.gdgaranjuez.handsup.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun q(id: String, votes: Int, minute: Int, status: QuestionStatus = QuestionStatus.OPEN) = Question(
    id = id, text = id, delivery = Delivery.READ, authorUid = "u", pseudonym = "A B",
    status = status, votes = votes, createdAtMs = minute * 60_000L,
)

class OrderingTest {
    private val list = listOf(q("a", 1, 0), q("b", 5, 1), q("c", 5, 2), q("d", 0, 3))

    @Test fun sortsByVotesThenOldest() =
        assertEquals(listOf("b", "c", "a", "d"), sortQuestions(list, SortMode.VOTES).map { it.id })

    @Test fun sortsByTime() =
        assertEquals(listOf("a", "b", "c", "d"), sortQuestions(list, SortMode.TIME).map { it.id })

    @Test fun buildsLanes() {
        val view = buildQueue(
            listOf(
                q("a", 1, 0), q("s", 9, 1, QuestionStatus.ONSTAGE), q("f", 2, 2, QuestionStatus.FOLLOWUP),
                q("h", 0, 3, QuestionStatus.HIDDEN), q("p", 0, 4, QuestionStatus.PENDING), q("x", 0, 5, QuestionStatus.ANSWERED),
            ),
            SortMode.VOTES,
        )
        assertEquals(listOf("s"), view.onStage.map { it.id })
        assertEquals(listOf("a"), view.open.map { it.id })
        assertEquals(listOf("f"), view.followup.map { it.id })
        assertEquals(listOf("h"), view.hidden.map { it.id })
        assertEquals(listOf("p"), view.pending.map { it.id })
        assertEquals(listOf("x"), view.answered.map { it.id })
    }
}

class TalkWindowTest {
    private val base = Talk("ABCDE", "t", "s", "r", 0, 1, TalkState.LIVE)

    @Test fun liveAcceptsQuestions() = assertTrue(base.acceptsQuestions(10, 5))
    @Test fun scheduledDoesNot() = assertFalse(base.copy(state = TalkState.SCHEDULED).acceptsQuestions(10, 5))
    @Test fun closedDoesNot() = assertFalse(base.copy(questionsClosed = true).acceptsQuestions(10, 5))

    @Test fun endedHonoursGracePeriod() {
        val ended = base.copy(state = TalkState.ENDED, endedAtMs = 1_000_000)
        assertTrue(ended.acceptsQuestions(10, 1_000_000 + 9 * 60_000))
        assertFalse(ended.acceptsQuestions(10, 1_000_000 + 11 * 60_000))
        assertFalse(ended.copy(endedAtMs = null).acceptsQuestions(10, 0))
    }
}

class TalkCodeTest {
    @Test fun alphabetHasNoAmbiguousGlyphs() = "01OIL".forEach { assertFalse(it in TALK_CODE_ALPHABET) }

    @Test fun generatesValidCodes() = repeat(200) { assertTrue(isValidTalkCode(generateTalkCode())) }

    @Test fun normalisesTypedInput() {
        assertEquals("ABCDE", normalizeTalkCode(" ab-cde "))
        assertEquals("ABCDE", normalizeTalkCode("abcdefgh"))
    }

    @Test fun rejectsBadCodes() {
        assertFalse(isValidTalkCode("ABCD"))
        assertFalse(isValidTalkCode("ABCD0"))
        assertFalse(isValidTalkCode("abcde"))
    }

    @Test fun neverRepeatsTakenCode() {
        val taken = mutableSetOf("AAAAA")
        var calls = 0
        val rnd = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = if (calls++ < 5) 0 else 7
        }
        val code = uniqueTalkCode(taken, rnd)
        assertNotEquals("AAAAA", code)
        assertTrue(code in taken)
    }
}

class PseudonymTest {
    @Test fun hasBigPool() = assertTrue(PSEUDONYM_COMBINATIONS > 1500)

    @Test fun generatesFirstLast() = repeat(100) {
        val n = generatePseudonym()
        assertTrue(Regex("^\\S+ \\S+$").matches(n), n)
        assertTrue(isValidPseudonym(n))
    }

    @Test fun isDeterministicWithSeededRandom() = assertEquals(generatePseudonym(Random(1)), generatePseudonym(Random(1)))
    @Test fun keyIsLowercase() = assertEquals("marie curie", pseudonymKey("Marie CURIE"))

    @Test fun cleansCustomNames() {
        assertEquals("a b c", cleanPseudonym("  a/b\n  c  "))
        assertEquals(30, cleanPseudonym("x".repeat(50)).length)
    }

    @Test fun validates() {
        assertFalse(isValidPseudonym("A"))
        assertFalse(isValidPseudonym("a/b"))
    }
}

class BrandingTest {
    @Test fun picksReadableTextColours() {
        assertEquals("#111111", onColor("#ffffff"))
        assertEquals("#ffffff", onColor("#000000"))
        assertTrue(contrast(onColor("#d91e5b"), "#d91e5b") > 4)
    }

    @Test fun makesBrandColoursReadable() {
        assertTrue(contrast(readableOn("#ffee00", "#ffffff"), "#ffffff") >= 4.5)
        assertTrue(contrast(readableOn("#101040", "#14141a"), "#14141a") >= 4.5)
    }

    @Test fun validatesHex() {
        assertTrue(isHexColor("#a1B2c3"))
        assertFalse(isHexColor("red"))
        assertFalse(isHexColor("#fff"))
    }
}

class CsvTest {
    @Test fun quotesAndEscapes() {
        assertEquals("\"a,b\"", csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvCell("say \"hi\""))
    }

    @Test fun neutralisesFormulaInjection() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", csvCell("=HYPERLINK(\"x\")"))
        assertEquals("'+1", csvCell("+1"))
    }

    @Test fun writesRows() = assertEquals("a,b\n1,x\n", toCsv(listOf("a", "b"), listOf(listOf(1, "x"))))
}

class ImporterTest {
    private val base = """
        {"rooms":[{"id":"main","name":"Sala"}],
         "talks":[
           {"title":"One","speakers":"Ada","room":"main","start":"2026-10-24T10:00:00+02:00","end":"2026-10-24T10:45:00+02:00"},
           {"code":"ABCDE","title":"Two","room":"main","start":"2026-10-24T11:00:00+02:00","end":"2026-10-24T11:45:00+02:00"}]}
    """.trimIndent()

    @Test fun importsAndGeneratesMissingCodes() {
        val plan = parseImport(base)
        assertEquals(2, plan.talks.size)
        assertTrue(isValidTalkCode(plan.talks[0].code))
        assertEquals("ABCDE", plan.talks[1].code)
        assertNotEquals("ABCDE", plan.talks[0].code)
        assertEquals(45 * 60_000L, plan.talks[0].endMs - plan.talks[0].startMs)
        assertEquals("Ada", plan.talks[0].speakers)
    }

    @Test fun readsTheOffset() {
        val plan = parseImport(base)
        // 10:00 at +02:00 is 08:00 UTC
        assertEquals(8 * 3_600_000L, plan.talks[0].startMs % 86_400_000L)
    }

    private fun failure(json: String) = assertFailsWith<ImportException> { parseImport(json) }.code

    @Test fun rejectsBadInput() {
        assertEquals("badJson", failure("{ nope"))
        assertEquals("notObject", failure("[]"))
        assertEquals("rooms", failure("""{"rooms":[],"talks":[]}"""))
        assertEquals("room", failure(base.replace("\"room\":\"main\"", "\"room\":\"nope\"")))
        assertEquals("end", failure(base.replace("2026-10-24T10:45:00+02:00", "x")))
        assertEquals("order", failure(base.replace("2026-10-24T10:45:00+02:00", "2026-10-24T09:00:00+02:00")))
        assertEquals("code", failure(base.replace("ABCDE", "ABCD0")))
    }

    @Test fun rejectsRepeatedCodes() {
        val two = base.replace("{\"title\":\"One\"", "{\"code\":\"ABCDE\",\"title\":\"One\"")
        assertEquals("codeRepeated", failure(two))
    }

    @Test fun avoidsCodesAlreadyUsedByTheEvent() {
        val plan = parseImport(base, listOf("ZZZZZ"))
        assertFalse("ZZZZZ" in plan.talks.map { it.code })
    }
}

class SpeakerKeyTest {
    @Test fun isLongAndRandom() {
        val a = generateSpeakerKey()
        assertEquals(32, a.length)
        assertNotEquals(a, generateSpeakerKey())
    }
}
