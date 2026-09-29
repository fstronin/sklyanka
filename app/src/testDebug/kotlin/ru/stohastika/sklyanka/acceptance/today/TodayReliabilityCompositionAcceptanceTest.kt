package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки композиции предупреждения о ненадёжности (SKL-29,
 * `@composition`).
 *
 * Модуль — `app`: сценариям нужны «Сегодня» и экран разрешений одновременно
 * (`SKL-26/TDD.md §4.3`). Прогон `feature-today` исполняет сценарии одного экрана
 * (`@screen and not @composition`) и до разрешений не достаёт.
 */
@RunWith(AndroidJUnit4::class)
// Экраны высокие: карточки разрешений и ленты «Сегодня» иначе не собираются целиком.
// Язык закреплён: строки предупреждения и карточек читаются по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TodayReliabilityCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfReliabilityRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            OnboardingAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-29"
        const val TAGS = "@screen and @composition"
    }
}
