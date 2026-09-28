package ru.heuri.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки диагностики напоминаний по сценариям SKL-13 (группа `@diagnostics`).
 *
 * Модуль свой: экран диагностики живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Прогон SKL-20 исполняет свою группу `@diagnostics`
 * тем же стендом — сценарии двух задач различаются файлами, а не механизмом.
 */
@RunWith(AndroidJUnit4::class)
// Журнал длиннее окна телефона: без запаса по высоте строки журнала не собираются,
// и проверка видела бы не экран, а его верх.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderDiagnosticsAcceptanceTest {

    @Test
    fun theDiagnosticsScenariosOfTheReminderRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $DIAGNOSTICS_TAG"))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-13"
        const val SCREEN_TAG = "@screen"
        const val DIAGNOSTICS_TAG = "@diagnostics"
    }
}
