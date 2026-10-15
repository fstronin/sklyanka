package ru.stohastika.sklyanka.acceptance.pro

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.TAG_PRO_ROW
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-122 — строка блока «Склянка Про» в настройках.
 *
 * Шаги живут в `feature-settings`, потому что говорят об одной строке этого модуля: её тег
 * `TAG_PRO_ROW` объявлен `internal`, и увидеть его может только прогон самого модуля
 * (`SKL-26/TDD.md §4.3`). Состояние подписки строка не считает — она показывает то,
 * что дал `ProStatus` (Р-67 п. 6), и шаг берёт его из того же чтения, каким живёт экран.
 *
 * Состояние стенда набирает сценарий словами Предыстории: «подписка „Склянка Про“ активна
 * до 13 ноября 2026» и «подписка „Склянка Про“ не оформлена» — общие шаги приёмки
 * (`Sk122ProSteps`), и своих выражений для того же состояния здесь не заводится.
 *
 * Выражение кончается на «на стенде»: выражение сценарного прогона `строка настроек несёт
 * значение «…»` приезжает сюда общим артефактом (`StepScope`), и второе объявление той же
 * строки было бы дублем словаря.
 *
 * Русский текст в аннотации — ключевое слово Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk122ProRowScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Строка настроек несёт названное сценарием значение: «Активна до 13 ноября 2026»
     * либо «Не оформлена».
     *
     * Сценарий называет значение словами — шаг сверяет эти слова с ресурсом каталога
     * и с тем, что нарисовано: строка не имеет права ни разойтись с ресурсом, ни показать
     * чужое состояние.
     */
    @Тогда("^на стенде строка настроек несёт значение «([^»]+)»$")
    fun theSettingsRowCarriesTheValueOnTheStand(value: String) {
        assertEquals("the scenario must name the value the row shows", expectedRowValue(), value)
        runComposeUiTest {
            setContent {
                Stand {
                    SettingsScreen(
                        state = SettingsUiState(pro = world.pro.status(), version = VERSION),
                        onIntent = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_PRO_ROW).assertExists().assertTextContains(value)
        }
    }

    /** Значение строки словами каталога: срок подписки подставляет платформенный форматтер (НФТ-43а). */
    private fun expectedRowValue(): String {
        val until = world.pro.status().until ?: return string(UiR.string.pro_state_inactive)
        return context.withInterfaceLocale(RUSSIAN)
            .getString(UiR.string.pro_state_active_until, formatDate(until))
    }

    private fun formatDate(until: LocalDate): String =
        DateTimeFormatter.ofPattern(string(UiR.string.pro_date_pattern), RUSSIAN).format(until)

    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

    /**
     * Стенд: язык интерфейса сценария.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть
     * любым (НФТ-43): и слова строки, и дата в них берутся из того же каталога, что у экрана.
     */
    @Composable
    private fun Stand(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(RUSSIAN)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        /** Версия на плашке «О приложении»: строке блока она безразлична, но экран её показывает. */
        const val VERSION = "1.0"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
