package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки повторного пробного напоминания по сценарию SKL-14 (группа `@diagnostics`).
 *
 * Модуль свой: экран диагностики живёт здесь, и шаг обязан видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Прогоны SKL-13 и SKL-20 исполняют свои группы
 * `@diagnostics` тем же стендом — сценарии задач различаются файлами, а не механизмом.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: кнопка пробного напоминания иначе не собирается.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TestReminderRepeatAcceptanceTest {

    @Test
    fun theTestReminderScenarioOfTheOnboardingRunsOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-14"
        const val TAGS = "@screen and @diagnostics"
    }
}
