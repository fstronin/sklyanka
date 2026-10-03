package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки повторного открытия формы нового лекарства (SKL-57, `@screen`).
 *
 * Модуль свой: список и форма живут здесь, и их шаги обязаны видеть собранный экран
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Форму длиннее окна телефона стенд собирает
 * целиком — тот же приём, что в `MedsAcceptanceTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsReopenAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl57RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-57"
    }
}
