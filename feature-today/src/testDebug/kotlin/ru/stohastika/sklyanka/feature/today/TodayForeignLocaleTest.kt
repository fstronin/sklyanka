package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Дата в шапке «Сегодня» на телефоне с чужим языком системы (SKL-28, НФТ-43а).
 *
 * Слова внутри даты — день недели и месяц — брались с языка устройства, и на английском
 * телефоне шапка русского приложения читалась «SUNDAY, 27 SEPTEMBER» (SKL-27).
 *
 * Проверяется отдельно от склонений: склонения и даты ломаются по разным причинам —
 * первые выбором правил множественного числа, вторые локалью форматтера, — и одна
 * проверка на обе не сказала бы, что именно сломалось.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h2400dp-mdpi")
class TodayForeignLocaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theHeaderNamesTheWeekdayAndTheMonthInTheInterfaceLanguage() {
        composeRule.setContent {
            val pinned = LocalContext.current.withInterfaceLocale()
            CompositionLocalProvider(
                LocalContext provides pinned,
                LocalConfiguration provides pinned.resources.configuration,
            ) {
                SklyankaTheme { TodayScreen(state = TodayFixtures.plainDay(), onIntent = {}) }
            }
        }

        // Шапка показывает дату прописными (§2.2 дизайн-системы), поэтому ожидание — тоже.
        composeRule.onNodeWithText(EXPECTED_DATE, useUnmergedTree = true).assertExists()
    }

    private companion object {
        /** 25 сентября 2026 — пятница. */
        const val EXPECTED_DATE = "ПЯТНИЦА, 25 СЕНТЯБРЯ"
    }
}
