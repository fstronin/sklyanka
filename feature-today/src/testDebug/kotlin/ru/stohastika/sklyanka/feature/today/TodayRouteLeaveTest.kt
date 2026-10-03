package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.acceptance.today.TodayStand
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Уход с «Сегодня» заканчивает окно отмены (SKL-30).
 *
 * Проверяется именно `DisposableEffect` маршрута: уход любым путём (вкладка, карточка
 * лекарства, экран разрешений) снимает `TodayRoute` с композиции, и окно закрывается
 * одним механизмом, а не пятью вызовами по местам навигации.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TodayRouteLeaveTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope настоящей ViewModel: на JVM главный диспетчер подменяется,
        // как в стенде карточки напоминания (SKL-13).
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun leavingTheTodayRouteClosesTheUndoWindow() {
        val world = AcceptanceWorld()
        world.profiles.use("self")
        val viewModel = TodayStand.viewModel(world)
        world.undoActions.publishMark(listOf("intake-1"), MarkResult.TAKEN, listOf("med"))
        assertNotNull(world.undoActions.live())

        var routeIsShown by mutableStateOf(true)
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    if (routeIsShown) {
                        TodayRoute(viewModel = viewModel, navigation = TodayNavigation())
                    }
                }
            }
            runOnIdle { routeIsShown = false }
            waitForIdle()
        }

        assertNull(world.undoActions.live())
    }
}
