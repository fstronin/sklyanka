package ru.stohastika.sklyanka.ui.analytics

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen

/**
 * Показ экрана сообщается один раз за вход (SKL-165, TDD §5.4).
 *
 * Проверяется на JVM: настоящая композиция и настоящий `LaunchedEffect`. Повторная
 * композиция того же значения не должна плодить событий — иначе каждый кадр прокрутки
 * выглядел бы как новый показ.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTrackingTest {

    @get:Rule
    val compose = createComposeRule()

    private val shown = mutableListOf<AnalyticsScreen>()

    private fun show(screen: () -> AnalyticsScreen) {
        compose.setContent {
            CompositionLocalProvider(
                LocalScreenTracker provides { shown += it },
            ) {
                TrackScreenView(screen())
            }
        }
    }

    @Test
    fun `a recomposition of the same screen does not report it twice`() {
        var recompositions by mutableStateOf(0)
        show {
            // Чтение состояния внутри композиции само по себе меняет кадр, но значение
            // экрана остаётся тем же, и событие обязано остаться одним.
            recompositions
            AnalyticsScreen.TODAY
        }

        compose.runOnIdle { recompositions = 1 }
        compose.runOnIdle { recompositions = 2 }
        compose.waitForIdle()

        assertEquals(listOf(AnalyticsScreen.TODAY), shown)
    }

    @Test
    fun `a change of screen reports each screen once`() {
        var screen by mutableStateOf(AnalyticsScreen.TODAY)
        show { screen }

        compose.runOnIdle { screen = AnalyticsScreen.HISTORY }
        compose.runOnIdle { screen = AnalyticsScreen.SETTINGS }
        compose.waitForIdle()

        assertEquals(listOf(AnalyticsScreen.TODAY, AnalyticsScreen.HISTORY, AnalyticsScreen.SETTINGS), shown)
    }

    @Test
    fun `without a provider the screen works and reports nothing`() {
        compose.setContent { TrackScreenView(AnalyticsScreen.TODAY) }

        compose.waitForIdle()

        assertEquals(emptyList<AnalyticsScreen>(), shown)
    }
}
