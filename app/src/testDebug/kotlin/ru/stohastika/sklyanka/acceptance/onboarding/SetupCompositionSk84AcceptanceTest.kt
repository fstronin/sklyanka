package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон кросс-экранных сценариев SKL-84 в композиции `app` (группа `@composition`).
 *
 * Модуль — `app`: сценариям нужны «Сегодня», сообщение о перерыве и экран «Настроить
 * надёжность» сразу — переход «Исправить» одним действием и фокус на первом незакрытом
 * шаге проверяются только там, где оба экрана существуют вместе (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: строки сообщения о перерыве и плашки читаются по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SetupCompositionSk84AcceptanceTest {

    @Test
    fun theCompositionScenariosOfSk84RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "@screen and @composition and not @manual"))
        } finally {
            OnboardingAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-84"
    }
}
