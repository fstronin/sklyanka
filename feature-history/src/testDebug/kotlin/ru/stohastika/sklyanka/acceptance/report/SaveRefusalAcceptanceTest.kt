package ru.stohastika.sklyanka.acceptance.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки честного отказа без обработчика сохранения (SKL-46).
 *
 * Модуль свой: шторка отчёта и маршрут истории живут здесь, и шаги обязаны видеть
 * собранную композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Сценарии `@manual` (устройство) в этот прогон не входят: образ без `DocumentsUI`
 * и обычный телефон — проверки AVD.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона, иначе шторка отчёта не собирается (SKL-24, SKL-44).
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class SaveRefusalAcceptanceTest {

    @Test
    fun theSaveRefusalScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-46"
        const val SCREEN_TAG = "@screen"
    }
}
