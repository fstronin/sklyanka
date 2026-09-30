package ru.stohastika.sklyanka.acceptance.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки предъявления шторки отчёта (SKL-44, группа `@screen`).
 *
 * Сценарии исполняются на стенде `HistoryScreenProbe` в обёртке `ProfileChrome` — той же,
 * в какой экран собирает корень приложения. Прямая композиция шторку не теряла, и проверка
 * без обёртки дефект SKL-44 не ловила (`SKL-44/TDD.md` §3).
 *
 * Сценарий `@manual` (устройство) в этот прогон не входит: системные диалоги
 * воспроизводятся только на устройстве.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class ReportSheetAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheReportSheetRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-44"
        const val SCREEN_TAG = "@screen"
    }
}
