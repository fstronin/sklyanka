package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-85 (группа `@indicator @diagnostics`): пояснение индикатора
 * будильника стоит под следующим напоминанием на «Проверке напоминаний».
 *
 * Модуль свой: сценарии говорят об экране «Проверка напоминаний», и шаги обязаны видеть
 * собранную композицию и ресурсы модуля. Вторая половина сценария ОВ-110 — на шаге
 * «Точные будильники» — исполняется стендом `feature-onboarding`.
 */
@RunWith(AndroidJUnit4::class)
// Журнал длиннее окна телефона: без запаса по высоте строки не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class IndicatorDiagnosticsAcceptanceTest {

    @Test
    fun theIndicatorExplanationRunsOnTheDiagnosticsStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-85"
        const val TAGS = "@screen and @diagnostics"
    }
}
