package ru.stohastika.sklyanka.acceptance.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Кросс-экранный прогон приёмки второй точки входа самопроверки (SKL-86, группа `@reliability`).
 *
 * Модуль — `app`: сценарию нужны «Настроить надёжность» и «Проверка напоминаний» сразу,
 * а экранные модули друг о друге не знают (`ADR-0007`). Комментарий сценария называет
 * стенд `feature-onboarding` — там проверялась бы только кнопка, но не переход.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: плашка и заголовок шторки читаются по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class DeliveryCheckCompositionSk86AcceptanceTest {

    @Test
    fun theReliabilityScenarioOfSk86RunsOnTheAppStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            OnboardingAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-86"
        const val TAGS = "@screen and @SKL-86 and @reliability"
    }
}
