package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки «Проверки напоминаний» (SKL-79, группы `@screen`/`@support`).
 *
 * Модуль свой: экран живёт в `feature-reminder`, и шаги обязаны видеть собранную
 * композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`). Ручной сценарий «Исправить
 * открывает нужный системный экран» исключён: системные экраны приложению не принадлежат.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: техблок «Для поддержки» иначе не собирается,
// и проверка видела бы только первый уровень.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class CheckAcceptanceTest {

    @Test
    fun theCheckScenariosOfTheReminderRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-79"
        const val TAGS = "(@screen or @support) and not @manual"
    }
}
