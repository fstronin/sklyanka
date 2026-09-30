package ru.stohastika.sklyanka.acceptance.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки языка отчёта врачу (SKL-45, группа `@screen`).
 *
 * Квалификатор задаёт **чужой** язык телефона — того, чего не делает ни один другой прогон
 * модуля: `ReportAcceptanceTest` идёт с русским телефоном, и дефект 44-A для него невидим.
 * Так же устроен `InterfaceLanguageAcceptanceTest` (SKL-28) в модуле `app` — для экранов,
 * которые дефект языка уже прошёл.
 *
 * Сценарий собирается из существующих шагов: новых объявлений словаря SKL-23 не появляется.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h3000dp-mdpi")
class ReportForeignLocaleAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheReportLanguageRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-45"
        const val SCREEN_TAG = "@screen"
    }
}
