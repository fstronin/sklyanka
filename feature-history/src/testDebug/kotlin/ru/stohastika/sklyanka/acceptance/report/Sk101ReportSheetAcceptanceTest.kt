package ru.stohastika.sklyanka.acceptance.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки шторки отчёта (SKL-101, ОВ-125).
 *
 * Сценарии исполняются на Robolectric-стенде `feature-history`: шапка, полотно,
 * чипы периода, карточка «подпись — значение», тихое пояснение и состояния исхода.
 * Сценарий `@manual` (устройство) в прогон не входит — системные диалоги и прокрутку
 * при крупном шрифте смотрят на устройстве.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk101ReportSheetAcceptanceTest {

    @Test
    fun theReportSheetScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-101"
        const val SCREEN_TAG = "@screen"
    }
}
