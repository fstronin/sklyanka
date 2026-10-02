package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.text.style.TextOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор разметки: разрыв слова и усечение (SKL-91).
 *
 * Проверки чистые, без композиции: факты — значение, а не экран, и правило
 * «иероглиф на границе — не разрыв» обязано проверяться без Robolectric.
 */
class TextLayoutFactsTest {

    @Test
    fun aCyrillicWordSplitByTheLayoutIsBroken() {
        val facts = facts(lines = listOf("При", "нят"))

        assertTrue(facts.wordBroken)
        assertNotNull(facts.complaint())
    }

    @Test
    fun aLatinWordSplitByTheLayoutIsBroken() {
        val facts = facts(lines = listOf("Benachrichti", "gungen"))

        assertTrue(facts.wordBroken)
    }

    @Test
    fun aBreakAtASpaceIsNotAWordBreak() {
        val facts = facts(lines = listOf("Принят ", "в 08:00"))

        assertFalse(facts.wordBroken)
        assertNull(facts.complaint())
    }

    @Test
    fun aChineseBreakBetweenIdeographsIsNotAWordBreak() {
        val facts = facts(lines = listOf("今天需要", "服用药物"))

        assertFalse(facts.wordBroken)
        assertNull(facts.complaint())
    }

    @Test
    fun aMixedBreakNextToAnIdeographIsNotAWordBreak() {
        val facts = facts(lines = listOf("PDF 报告", "今天"))

        assertFalse(facts.wordBroken)
    }

    @Test
    fun anEllipsizedLineIsNotBlamedForAWordBreak() {
        val facts = facts(lines = listOf("Очень длинное назва", "ние"), ellipsized = true)

        assertFalse(facts.wordBroken)
    }

    @Test
    fun anExplicitSingleLineEllipsisIsAllowed() {
        val facts = facts(
            lines = listOf("Magnesium B6 with a very long name"),
            ellipsized = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        assertTrue(facts.allowedEllipsis)
        assertFalse(facts.cut)
        assertNull(facts.complaint())
        assertNotNull(facts.complaint(reportAllowedEllipsis = true))
    }

    @Test
    fun anEllipsisWithoutTheSingleLineChoiceIsADefect() {
        val facts = facts(
            lines = listOf("Настройки напоминаний"),
            ellipsized = true,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        assertFalse(facts.allowedEllipsis)
        assertTrue(facts.ellipsized)
        assertNotNull(facts.complaint())
        assertNotNull(facts.complaint(reportAllowedEllipsis = true))
    }

    @Test
    fun aTextCutOffByHeightIsADefect() {
        val facts = facts(text = "Первая строка", lines = listOf("Первая строка"), cut = true)

        assertTrue(facts.cut)
        assertEquals(
            "the text \"Первая строка\" is cut off: [Первая строка]",
            facts.complaint(),
        )
    }

    private fun facts(
        text: String = "sample",
        lines: List<String>,
        cut: Boolean = false,
        ellipsized: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
        overflow: TextOverflow = TextOverflow.Clip,
    ) = TextFacts(
        text = text,
        lines = lines,
        cut = cut,
        ellipsized = ellipsized,
        maxLines = maxLines,
        overflow = overflow,
    )
}
