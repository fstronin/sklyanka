package ru.stohastika.sklyanka.feature.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.time.LocalDate
import java.util.Locale

/**
 * Период отчёта словами (ОВ-125, решение 02.10.2026).
 *
 * Внутри месяца начало печатается днём, на границе месяцев — полными датами на обоих
 * концах, для одного дня — одной датой. Начало внутри месяца — ресурс языка: в zh-CN
 * голое «1» рядом с «2026年9月30日» не читается, и там печатается «9月1日».
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU")
class ReportPdfTextTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val text = ReportPdfText(context)

    @Test
    fun `a period inside one month prints the start as a day`() {
        assertEquals(
            "1 — 30 сентября 2026",
            text.periodRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), RU),
        )
    }

    @Test
    fun `a period across months prints full dates on both ends`() {
        assertEquals(
            "1 августа 2026 — 31 октября 2026",
            text.periodRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), RU),
        )
    }

    @Test
    fun `a single day prints one date`() {
        assertEquals(
            "4 октября 2026",
            text.periodRange(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 4), RU),
        )
    }

    @Test
    fun `the labeled period keeps the label for the document header`() {
        assertEquals(
            "Период: 4 октября 2026",
            text.period(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 4), RU),
        )
    }

    @Test
    fun `the start of a period inside one month is a language resource`() {
        val chinese = context.withInterfaceLocale(Locale.SIMPLIFIED_CHINESE)
        val chineseText = ReportPdfText(chinese)

        val range = chineseText.periodRange(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
            Locale.SIMPLIFIED_CHINESE,
        )

        assertTrue("the start must be «9月1日», not a bare «1»: $range", range.startsWith("9月1日"))
        assertFalse("a bare day next to a full date must not be printed: $range", range.startsWith("1 —"))
    }

    private companion object {
        val RU: Locale = Locale.forLanguageTag("ru-RU")
    }
}
