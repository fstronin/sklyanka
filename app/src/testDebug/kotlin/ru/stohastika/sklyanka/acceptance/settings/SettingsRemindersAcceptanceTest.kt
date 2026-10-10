package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки строки перехода в системные настройки и «Отложить на»
 * (SKL-78, группа `@screen`).
 *
 * Прогон живёт в `app`: сценарий «интервал задаёт подпись кнопки» говорит и о настройках,
 * и о карточке напоминания, а вместе их видит только `app`. Экран длиннее окна телефона,
 * поэтому стенд собирает его целиком.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class SettingsRemindersAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl78RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
        } finally {
            SettingsRemindersStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-78"
    }
}
