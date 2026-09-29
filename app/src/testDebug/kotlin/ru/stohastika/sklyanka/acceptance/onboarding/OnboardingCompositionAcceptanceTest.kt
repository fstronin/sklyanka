package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки онбординга в композиции `app` (SKL-14, группа `@composition`).
 *
 * Модуль — `app`: сценариям нужны первый запуск (решение заставки), уход на «Сегодня»
 * и несколько экранов сразу (`SKL-26/TDD.md §4.3`). Экранные сценарии одного экрана
 * исполняет стенд `feature-onboarding`, строку настроек — `feature-settings`.
 */
@RunWith(AndroidJUnit4::class)
// Экраны высокие: карточки разрешений и нижняя панель «Сегодня» иначе не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class OnboardingCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTheOnboardingRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            OnboardingAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-14"
        const val TAGS = "@screen and @composition"
    }
}
