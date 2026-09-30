package ru.stohastika.sklyanka.acceptance.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки кадров запуска (SKL-42, группа `@screen`).
 *
 * Кадры запуска — механика корня приложения, и собрать её целиком может только `app`:
 * правило кадров, настоящая заставка и экраны `feature-today` и `feature-onboarding`
 * живут вместе только здесь (`SKL-26/TDD.md §4.3`).
 *
 * Сценарии `@manual` (устройство) в этот прогон не входят: системный кадр
 * и затянувшийся старт воспроизводятся только на платформе.
 */
@RunWith(AndroidJUnit4::class)
// Русский язык стенда: тексты заставки читаются как в макете (задача SKL-28).
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class StartupFramesAcceptanceTest {

    @Test
    fun theStartupFrameScenariosRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
        } finally {
            OnboardingAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-42"
        const val SCREEN_TAG = "@screen"
    }
}
