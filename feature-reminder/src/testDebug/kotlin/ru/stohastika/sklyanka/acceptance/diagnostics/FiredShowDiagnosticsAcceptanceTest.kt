package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-88 (группа `@diagnostics`): подавленное не выдаётся за показ.
 *
 * Модуль свой: сценарии говорят об экране «Проверка напоминаний», и шаги обязаны видеть
 * собранную композицию и ресурсы модуля. Сценарии «Сегодня» той же задачи исполняет
 * `feature-today`.
 */
@RunWith(AndroidJUnit4::class)
// Журнал длиннее окна телефона: без запаса по высоте строки журнала не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class FiredShowDiagnosticsAcceptanceTest {

    @Test
    fun theSuppressionScenariosOfDiagnosticsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $DIAGNOSTICS_TAG"))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-88"
        const val SCREEN_TAG = "@screen"
        const val DIAGNOSTICS_TAG = "@diagnostics"
    }
}
