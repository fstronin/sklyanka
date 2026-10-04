package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки «Проверки напоминаний» (SKL-102, группа `@screen`).
 *
 * Модуль свой: экран живёт в `feature-reminder`, и шаги обязаны видеть собранную
 * композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`). Сценарии `@manual` (устройство)
 * в прогон не входят: системные экраны, клавиатуру и крупный шрифт смотрят на устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: нижние секции и шторка иначе не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class Sk102DiagnosticsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSk102RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-102"
        const val SCREEN_TAG = "@screen and not @manual"
    }
}
