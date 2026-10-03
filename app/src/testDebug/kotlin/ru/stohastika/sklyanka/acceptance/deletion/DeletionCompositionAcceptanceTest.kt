package ru.stohastika.sklyanka.acceptance.deletion

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Композиционный прогон приёмки SKL-99: карточка → «Сегодня» и ярлык (ОВ-124).
 *
 * Сценарию нужны оба экрана сразу, и живут они в разных модулях: единственное место,
 * где они встречаются, — `app` (SKL-26, `TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class DeletionCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = "SKL-99", tags = "@screen and @deletion-composition"))
    }
}
