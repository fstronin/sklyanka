package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_EXPORT
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-119 — служебная выгрузка журнала на «Проверке напоминаний».
 *
 * Проверяется **продукт**, а не исходники: экран собирается настоящим `DiagnosticsViewModel`
 * на мире сценария ([DiagnosticsStand]), и сценарий говорит о том, что человек видит и может
 * нажать. Метки «Про» на строке нет — и это проверяется составом поверхности, а не чтением
 * файла разметки: слово платного контура рисовалось бы на экране, а не в тексте исходника.
 *
 * Подписка здесь не оформляется вовсе: журнал обязан работать без неё (НФТ-51, Р-85 п. 2).
 *
 * Русский текст в аннотации — ключевое слово Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk119JournalExportScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Допустим("^человек открыл «Проверку напоминаний» на стенде$")
    fun theRemindersCheckIsOpenOnTheStand() {
        DiagnosticsStand.enter(world)
    }

    @Тогда("^на стенде есть строка выгрузки журнала и метки «Про» на ней нет$")
    fun theJournalExportIsOnTheStandAndUnmarked() {
        DiagnosticsStand.ask(world) {
            onNodeWithTag(TAG_JOURNAL_EXPORT, useUnmergedTree = true)
                .performScrollTo()
                .assertExists()
                .assertIsEnabled()
            val mark = context.withInterfaceLocale(RUSSIAN).getString(UiR.string.pro_mark)
            val marks = onAllNodesWithText(mark, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("the journal export must carry no mark of the paid contour: $marks", marks.isEmpty())
        }
    }

    /**
     * Выгрузка доступна без подписки: нажатие открывает системный выбор места и файл пишется.
     *
     * Успех подтверждается тем, что файл лёг на файловый порт мира, — то есть продукт довёл
     * действие до конца, а не показал кнопку.
     */
    @Тогда("^без подписки журнал выгружается файлом$")
    fun theJournalIsExportedWithoutASubscription() {
        DiagnosticsStand.act(world) {
            onNodeWithTag(TAG_JOURNAL_EXPORT, useUnmergedTree = true).performScrollTo().performClick()
        }
        DiagnosticsStand.chooseJournalPlace(world)
        assertTrue(
            "the journal must be written without any subscription: ${world.transfer.files.documents.keys}",
            world.transfer.files.documents.keys.any { handle -> handle.contains("document") },
        )
    }

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}

/**
 * Экранный прогон приёмки SKL-119 — выгрузка журнала на «Проверке напоминаний».
 *
 * Модуль свой, а не общий: строка выгрузки живёт в `feature-reminder`, и её тег объявлен
 * `internal` — увидеть его может только прогон самого модуля (`SKL-26/TDD.md §4.3`).
 * От прочих прогонов SKL-119 отличается тегом `@journal`: шторки и строка настроек идут
 * прогонами `feature-history` и `feature-settings`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk119JournalAcceptanceTest {

    @Test
    fun theJournalScenariosOfSk119RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        DiagnosticsStand.dropMainDispatcher()
    }

    private companion object {
        const val TASK = "SKL-119"

        const val TAGS = "@screen and @journal"
    }
}
