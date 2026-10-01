package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-88 (группа `@screen`): «Сегодня» показывает приём без пометок.
 *
 * Модуль свой: сценарию нужен собранный экран «Сегодня» с его ресурсами. Сценарии
 * «Проверки напоминаний» той же задачи исполняет `feature-reminder`.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: без запаса по высоте лента дня не собирается.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class FiredShowTodayAcceptanceTest {

    @Test
    fun theTodayScenarioOfTheSuppressedFiringRunsOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-88"
        const val TAGS = "@screen and not @diagnostics and not @composition and not @manual"
    }
}
