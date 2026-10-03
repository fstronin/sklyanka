package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки состава, чипов и шапки «Переноса данных» (SKL-60, `@screen`).
 *
 * Модуль свой: экран переноса живёт здесь, и его шаги обязаны видеть собранный экран,
 * ресурсы модуля и настоящий кодек формата из `core-data` (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class TransferOverviewAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl60RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-60"
    }
}
