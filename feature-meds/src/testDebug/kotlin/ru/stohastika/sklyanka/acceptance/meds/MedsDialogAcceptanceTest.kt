package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки диалогов времени и даты (SKL-69, группа `@screen`).
 *
 * Система английская: сценарий «все подписи диалога — на русском» проверяет, что язык
 * задаёт приложение, а не телефон (SKL-28). Остальные сценарии группы от языка не зависят.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h3000dp-mdpi")
class MedsDialogAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl69RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-69"
    }
}
