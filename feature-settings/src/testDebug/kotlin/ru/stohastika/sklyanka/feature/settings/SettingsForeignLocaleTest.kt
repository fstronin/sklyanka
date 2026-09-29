package ru.stohastika.sklyanka.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Склонения числительных на телефоне с чужим языком системы (SKL-28, НФТ-43а, НФТ-44).
 *
 * Прогон идёт с локалью устройства `en-US` — той самой, на которой дефект нашёлся
 * (SKL-27). Форму множественного числа выбирает система по локали конфигурации, поэтому
 * на английской конфигурации русские строки получали английские правила: форм в английском
 * две, и 15 попадало в `other` — «15 минуты», а 0 — «0 лекарства».
 *
 * Ожидания записаны **словами, а не через `getQuantityString`**: ожидание, выраженное
 * теми же ресурсами, что и проверяемый экран, совпадает с любым результатом, включая
 * неверный. Проверяется то, как это читает человек.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h2400dp-mdpi")
class SettingsForeignLocaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Экран настроек на английском телефоне: обе строки, на которых дефект был виден.
     *
     * Числа выбраны так, чтобы русское и английское правила расходились: у пятнадцати
     * и пяти формы разные, у четырёх — совпадают, и на четырёх проверка ничего бы
     * не доказала.
     */
    @Test
    fun theSettingsScreenIsDeclinedByTheRulesOfTheInterfaceLanguage() {
        composeRule.setContent {
            WithInterfaceLocale {
                SklyankaTheme {
                    SettingsScreen(
                        state = stateWith(snoozeMinutes = SNOOZE, foreignMedications = MEDICATIONS),
                        onIntent = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("15 минут", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("5 лекарств", substring = true, useUnmergedTree = true).assertExists()
    }

    /**
     * Набор числительных требования (НФТ-44): 1, 2, 5, 11, 21, 101.
     *
     * Шесть чисел закрывают все три русские формы и обе ловушки — одиннадцать и сто один.
     * Собраны одной композицией: стенд разрешает рисовать содержимое один раз за проверку.
     */
    @Test
    fun everyNumeralOfTheRequirementIsDeclinedByTheInterfaceLanguage() {
        val minutes = linkedMapOf<Int, String>()
        val medications = linkedMapOf<Int, String>()

        composeRule.setContent {
            WithInterfaceLocale {
                NUMERALS.forEach { numeral ->
                    minutes[numeral] = pluralStringResource(R.plurals.settings_snooze_minutes, numeral, numeral)
                    medications[numeral] =
                        pluralStringResource(R.plurals.settings_profile_medications, numeral, numeral)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            listOf("1 минута", "2 минуты", "5 минут", "11 минут", "21 минута", "101 минута"),
            NUMERALS.map { minutes.getValue(it) },
        )
        assertEquals(
            listOf("1 лекарство", "2 лекарства", "5 лекарств", "11 лекарств", "21 лекарство", "101 лекарство"),
            NUMERALS.map { medications.getValue(it) },
        )
    }

    /**
     * Содержимое рисуется тем же контекстом, каким его рисует продукт.
     *
     * В продукте язык закрепляет `attachBaseContext` активности; здесь активность чужая —
     * её поднимает стенд, — поэтому контекст с закреплённым языком подставляется явно.
     * Убрать закрепление значит уронить обе проверки: без него содержимое рисуется
     * правилами языка устройства.
     */
    @androidx.compose.runtime.Composable
    private fun WithInterfaceLocale(content: @androidx.compose.runtime.Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
            content = content,
        )
    }

    private fun stateWith(snoozeMinutes: Int, foreignMedications: Int): SettingsUiState {
        val base = SettingsScreenFixtures.state(
            reminders = ReminderSettingsValues(persistentEnabled = true, snoozeMinutes = snoozeMinutes),
        )
        return base.copy(
            profiles = base.profiles.map { row ->
                if (row.isSelf) row else row.copy(medicationCount = foreignMedications)
            },
        )
    }

    private companion object {
        const val SNOOZE = 15
        const val MEDICATIONS = 5
        val NUMERALS = listOf(1, 2, 5, 11, 21, 101)
    }
}
