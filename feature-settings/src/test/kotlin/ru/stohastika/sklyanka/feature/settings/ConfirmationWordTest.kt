package ru.stohastika.sklyanka.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило слова-подтверждения (ОВ-67): регистр и краевые пробелы не считаются,
 * внутренние пробелы значимы. Образец — ресурсное слово, поэтому в тесте он тоже
 * латиницей: кириллица в строковом литерале Kotlin запрещена (НФТ-42).
 */
class ConfirmationWordTest {

    private val word = "Zamenit"

    @Test
    fun `an exact word matches`() {
        assertTrue(matchesConfirmationWord("Zamenit", word))
    }

    @Test
    fun `an empty input never matches`() {
        assertFalse(matchesConfirmationWord("", word))
        assertFalse(matchesConfirmationWord("   ", word))
    }

    @Test
    fun `the case is ignored`() {
        assertTrue(matchesConfirmationWord("zamenit", word))
        assertTrue(matchesConfirmationWord("ZAMENIT", word))
        assertTrue(matchesConfirmationWord("zAmEnIt", word))
    }

    @Test
    fun `the edge whitespace is ignored`() {
        assertTrue(matchesConfirmationWord("  Zamenit", word))
        assertTrue(matchesConfirmationWord("Zamenit  ", word))
        assertTrue(matchesConfirmationWord("  Zamenit  ", word))
        assertTrue(matchesConfirmationWord("\tZamenit\n", word))
    }

    @Test
    fun `inner whitespace matters`() {
        assertFalse(matchesConfirmationWord("Za menit", word))
    }

    @Test
    fun `a wrong word does not match`() {
        assertFalse(matchesConfirmationWord("Udalit", word))
        assertFalse(matchesConfirmationWord("Zamenit vse", word))
        assertFalse(matchesConfirmationWord("Zameni", word))
        assertFalse(matchesConfirmationWord("Zamenitt", word))
    }

    @Test
    fun `the sample itself is trimmed before the comparison`() {
        assertTrue(matchesConfirmationWord("Zamenit", "  Zamenit  "))
    }
}
