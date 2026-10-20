package es.gdgaranjuez.handsup

import es.gdgaranjuez.handsup.i18n.GENERATED_EN
import es.gdgaranjuez.handsup.i18n.GENERATED_ES
import es.gdgaranjuez.handsup.i18n.I18n
import es.gdgaranjuez.handsup.i18n.Lang
import es.gdgaranjuez.handsup.i18n.t
import es.gdgaranjuez.handsup.ui.formatClock
import es.gdgaranjuez.handsup.ui.formatDate
import es.gdgaranjuez.handsup.ui.parseLocal
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringsTest {
    @AfterTest fun reset() { I18n.lang = Lang.ES }

    private val placeholder = Regex("\\{(\\w+)\\}")
    private fun placeholders(s: String) = placeholder.findAll(s).map { it.groupValues[1] }.toSortedSet()

    @Test fun bothLanguagesHaveTheSameKeys() = assertEquals(GENERATED_ES.keys, GENERATED_EN.keys)

    @Test fun placeholdersMatchBetweenLanguages() {
        for (key in GENERATED_ES.keys) assertEquals(placeholders(GENERATED_ES[key]!!), placeholders(GENERATED_EN[key]!!), key)
    }

    @Test fun translatesAndSubstitutes() {
        I18n.lang = Lang.EN
        assertEquals("Wait 12 s for another question", t("talk.wait", "n" to 12))
        I18n.lang = Lang.ES
        assertEquals("Espera 12 s para otra pregunta", t("talk.wait", "n" to 12))
    }

    @Test fun unknownKeyFallsBackToItself() = assertEquals("no.such.key", t("no.such.key"))

    @Test fun detectsSystemLanguage() {
        assertEquals(Lang.EN, Lang.fromSystem("en-US"))
        assertEquals(Lang.ES, Lang.fromSystem("es-ES"))
        assertEquals(Lang.ES, Lang.fromSystem("fr-FR"))
    }
}

class TimeUtilTest {
    private val madrid = TimeZone.of("Europe/Madrid")

    @Test fun parsesLocalTimeIntoEpochMillis() {
        // 2026-10-24 10:00 in Madrid (UTC+2) is 08:00 UTC
        val ms = parseLocal("2026-10-24", "10:00", madrid)!!
        assertEquals("10:00", formatClock(ms, madrid))
        assertEquals("2026-10-24", formatDate(ms, madrid))
        assertEquals(8 * 3_600_000L, ms % 86_400_000L)
    }

    @Test fun acceptsSingleDigitHours() =
        assertEquals(parseLocal("2026-10-24", "09:05", madrid), parseLocal("2026-10-24", "9:05", madrid))

    @Test fun rejectsMalformedInput() {
        assertNull(parseLocal("24/10/2026", "10:00", madrid))
        assertNull(parseLocal("2026-10-24", "25:00", madrid))
        assertNull(parseLocal("2026-10-24", "noon", madrid))
        assertNull(parseLocal("2026-13-40", "10:00", madrid))
    }
}

class NavigatorTest {
    @Test fun pushPopReplace() {
        val nav = Navigator()
        assertFalse(nav.canGoBack)
        nav.push(Route.Event("e"))
        nav.push(Route.Talk("e", "ABCDE"))
        assertTrue(nav.canGoBack)
        nav.replace(Route.Event("e", browse = true))
        assertEquals(Route.Event("e", browse = true), nav.current)
        nav.pop()
        assertEquals(Route.Event("e"), nav.current)
        nav.pop(); nav.pop()
        assertEquals(Route.Home, nav.current)
    }

    @Test fun popToReturnsToTheNearestMatch() {
        val nav = Navigator()
        nav.push(Route.AdminHome)
        nav.push(Route.AdminEvent("e"))
        nav.push(Route.AdminSetup("e"))
        nav.popTo(Route.AdminHome)
        assertEquals(Route.AdminHome, nav.current)
        assertEquals(2, nav.stack.size)
    }
}
