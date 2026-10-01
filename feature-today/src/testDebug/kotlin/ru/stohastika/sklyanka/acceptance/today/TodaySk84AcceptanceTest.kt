package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон баннера ненадёжности по приёмке SKL-84 (группа `@screen`, `@banner`).
 *
 * Модуль свой: баннер живёт в шапке «Сегодня» (`SKL-26/TDD.md §4.3`). Сценарии
 * «Исправить» и вход из баннера помечены `@composition`: им нужен экран настройки
 * сразу с «Сегодня», а это два экрана в одном прогоне — единственное такое место `app`.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: строки баннера и момента перерыва читаются по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodaySk84AcceptanceTest {

    @Test
    fun theBannerScenariosOfSk84RunOnTheStand() {
        assertNull(
            ScreenAcceptanceRun.run(
                task = TASK,
                tags = "@screen and @banner and not @composition and not @manual",
            ),
        )
    }

    private companion object {
        const val TASK = "SKL-84"
    }
}
