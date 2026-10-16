package ru.stohastika.sklyanka.acceptance.export

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsIntent
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.TAG_EXPORT_ROW
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-119 — строка «Экспорт истории» в настройках (Р-85 п. 2, §5.16).
 *
 * Шаги живут в `feature-settings`, потому что говорят об одной строке этого модуля: её тег
 * `TAG_EXPORT_ROW` объявлен `internal`, и увидеть его может только прогон самого модуля
 * (`SKL-26/TDD.md §4.3`). Строка не решает о подписке и шторки не рисует: нажатие отдаёт
 * намерение, а шторку выгрузки хостит `app` — второй поверхности выгрузки в продукте нет.
 *
 * Русский текст в аннотации — ключевое слово Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk119ExportRowScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Намерения последнего нажатия: строка обязана просить то, за чем её нажали. */
    private var intents: List<SettingsIntent> = emptyList()

    @Тогда("^на стенде настроек есть строка «([^»]+)» с пояснением «([^»]+)»$")
    fun theStandCarriesThatRowWithThatCaption(title: String, caption: String) {
        compose { test ->
            test.onNodeWithTag(TAG_EXPORT_ROW, useUnmergedTree = true).assertExists()
            assertEquals(title, string(R.string.settings_export_history))
            assertEquals(caption, string(R.string.settings_export_note))
            test.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().let { found ->
                assertTrue("the row must carry «$title»", found.isNotEmpty())
            }
            test.onAllNodesWithText(caption, useUnmergedTree = true).fetchSemanticsNodes().let { found ->
                assertTrue("the row must carry the caption «$caption»", found.isNotEmpty())
            }
        }
    }

    @Тогда("^на строке стоит метка «Про»$")
    fun theRowCarriesTheProMark() {
        val mark = string(UiR.string.pro_mark)
        compose { test ->
            val found = test.onAllNodesWithText(mark, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("the paid row carries the mark once (§5.9)", 1, found.size)
        }
    }

    @Тогда("^нажатие строки просит открыть выгрузку$")
    fun theRowAsksForTheExportSheet() {
        compose { test -> test.onNodeWithTag(TAG_EXPORT_ROW, useUnmergedTree = true).performClick() }
        assertEquals(listOf(SettingsIntent.OpenHistoryExport), intents)
    }

    /**
     * Собрать экран настроек и задать вопрос его композиции.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть любым
     * (НФТ-43): слова строки берутся из того же каталога, что у экрана.
     */
    private fun compose(question: (androidx.compose.ui.test.ComposeUiTest) -> Unit) {
        intents = emptyList()
        runComposeUiTest {
            setContent {
                Stand {
                    SettingsScreen(
                        state = SettingsUiState(pro = world.pro.status(), version = VERSION),
                        onIntent = { intent -> intents = intents + intent },
                    )
                }
            }
            question(this)
            waitForIdle()
        }
    }

    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

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

        const val VERSION = "1.0"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}

/**
 * Экранный прогон приёмки SKL-119 — строка «Экспорт истории» в настройках.
 *
 * Модуль свой, а не общий: строка живёт в `feature-settings`, и `feature-history` её не видит
 * (проверка направлений `verifyModuleDependencies`) — как и наоборот. Группа шторок и выгрузки
 * идёт прогоном `feature-history` с тегом без `@settings`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk119ExportRowAcceptanceTest {

    @Test
    fun theExportRowScenariosOfSk119RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-119"

        const val TAGS = "@screen and @settings"
    }
}
