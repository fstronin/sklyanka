package ru.stohastika.sklyanka.acceptance.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки отчёта врачу в PDF (SKL-33, группа `@screen`).
 *
 * Модуль свой: документ собирается из строк этого модуля, и шаги обязаны видеть его
 * ресурсы (`SKL-26/TDD.md §4.3`). Сценарии без тега — «без сети» и «журнал» —
 * исполняются сценарным прогоном `:acceptance:acceptanceRun`; на устройстве остаются
 * `@device` (настоящий PDF и системный обмен) и `@manual`.
 *
 * Локаль закреплена русской: документ набирается для человека, и месяц в списке дат
 * обязан стоять в родительном падеже.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class ReportAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheReportRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-33"
        const val SCREEN_TAG = "@screen"
    }
}
