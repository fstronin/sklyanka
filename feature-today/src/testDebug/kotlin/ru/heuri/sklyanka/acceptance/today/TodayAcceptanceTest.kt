package ru.heuri.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки главного экрана (SKL-26, группа `@screen` файла SKL-08).
 *
 * Модуль свой: «Сегодня» живёт здесь, и шаг о составе его лент обязан видеть собранный
 * экран и его ресурсы (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
class TodayAcceptanceTest {

    @Test
    fun theScreenScenariosOfTodayRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-08"
    }
}
