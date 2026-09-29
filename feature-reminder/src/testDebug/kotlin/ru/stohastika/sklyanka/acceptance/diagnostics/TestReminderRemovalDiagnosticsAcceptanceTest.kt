package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки удаления пробного напоминания в диагностике (SKL-32, группа `@diagnostics`).
 *
 * Модуль свой: экран диагностики живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: журнал и действия иначе не собираются, и проверка видела бы
// не экран, а его верх. Язык — часть условий: сценарии приёмки записаны по-русски.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TestReminderRemovalDiagnosticsAcceptanceTest {

    @Test
    fun theRemovalScenariosOfTheDiagnosticsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-32"
        const val TAGS = "@screen and @diagnostics"
    }
}
