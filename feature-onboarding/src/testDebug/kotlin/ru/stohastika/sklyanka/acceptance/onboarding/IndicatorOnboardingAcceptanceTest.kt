package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-85 (группа `@indicator @onboarding`): индикатор будильника
 * объясняется там, где выдаётся право.
 *
 * Модуль свой: экран разрешений живёт здесь, и шаг обязан видеть собранную композицию
 * и ресурсы модуля. Вторая половина сценария ОВ-110 — под следующим напоминанием —
 * исполняется стендом `feature-reminder`.
 */
@RunWith(AndroidJUnit4::class)
// Экран с карточками и нижними кнопками: без запаса по высоте пояснение не собирается.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class IndicatorOnboardingAcceptanceTest {

    @Test
    fun theIndicatorExplanationRunsOnThePermissionsStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-85"
        const val TAGS = "@screen and @onboarding"
    }
}
