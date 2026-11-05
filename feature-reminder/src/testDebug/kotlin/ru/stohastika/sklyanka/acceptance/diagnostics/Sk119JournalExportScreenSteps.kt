package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/**
 * Экранные шаги приёмки SKL-119 — служебная выгрузка журнала на «Проверке напоминаний».
 *
 * Проверяется **продукт**, а не исходники: экран собирается настоящим `DiagnosticsViewModel`
 * на мире сценария ([DiagnosticsStand]), и сценарий говорит о том, что человек видит и может
 * нажать.
 *
 * Покупки и подписки в продукте нет вовсе: журнал работает без них (НФТ-51, Р-85 п. 2).
 *
 * Русский текст в аннотации — ключевое слово Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk119JournalExportScreenSteps(private val world: AcceptanceWorld) {

    @Допустим("^человек открыл «Проверку напоминаний» на стенде$")
    fun theRemindersCheckIsOpenOnTheStand() {
        DiagnosticsStand.enter(world)
    }

    @Тогда("^на стенде есть строка выгрузки журнала$")
    fun theJournalExportIsOnTheStand() {
        DiagnosticsStand.ask(world) {
            onNodeWithTag(TAG_JOURNAL_EXPORT, useUnmergedTree = true)
                .performScrollTo()
                .assertExists()
                .assertIsEnabled()
        }
    }

    /**
     * Выгрузка доступна всегда: нажатие открывает системный выбор места и файл пишется.
     *
     * Успех подтверждается тем, что файл лёг на файловый порт мира, — то есть продукт довёл
     * действие до конца, а не показал кнопку.
     */
    @Тогда("^журнал выгружается файлом$")
    fun theJournalIsExportedAsAFile() {
        DiagnosticsStand.act(world) {
            onNodeWithTag(TAG_JOURNAL_EXPORT, useUnmergedTree = true).performScrollTo().performClick()
        }
        DiagnosticsStand.chooseJournalPlace(world)
        assertTrue(
            "the journal must be written: ${world.transfer.files.documents.keys}",
            world.transfer.files.documents.keys.any { handle -> handle.contains("document") },
        )
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
