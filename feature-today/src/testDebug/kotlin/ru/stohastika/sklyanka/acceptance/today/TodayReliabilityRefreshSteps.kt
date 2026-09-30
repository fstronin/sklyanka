package ru.stohastika.sklyanka.acceptance.today

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.feature.today.TodayViewModel
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экранные шаги приёмки живого обновления предупреждения о ненадёжности (SKL-48,
 * группа `@screen`).
 *
 * Сценарий отличается от сценариев SKL-29 принципиально: там состояние собирает
 * `TodayStand` заново на каждый шаг, и дефект живого экрана не воспроизводится.
 * Здесь работает настоящий `TodayViewModel` на мире сценария, и подписка на его
 * состояние живёт между шагами — как `collectAsState` на открытом экране. Разрешение
 * меняется мимо приложения, как `pm grant` из smoke-прогона: без `refresh()`
 * (возврата) или минутного такта признак остался бы отозванным.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayReliabilityRefreshSteps(private val world: AcceptanceWorld) {

    private var model: TodayViewModel? = null
    private var subscription: Job? = null

    @Допустим("^«Сегодня» открыт приложением$")
    fun todayIsOpenInTheApp() {
        subscription?.cancel()
        val live = TodayStand.viewModel(world)
        model = live
        // Подписка, как у экрана: `collectAsState` не снимается между событиями,
        // и разрешение меняется при живом процессе, а не до первого показа.
        subscription = CoroutineScope(Dispatchers.Main).launch { live.state.collect { } }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "the live screen must have built its content: ${live.state.value}",
            live.state.value is TodayUiState.Content,
        )
    }

    @Когда("^человек возвращается в приложение$")
    fun heReturnsToTheApp() {
        val live = requireNotNull(model) { "«Сегодня» must be open in the app" }
        // Тот же вызов, что делает `MainActivity.onResume` (SKL-48): состояние
        // разрешений перечитывается при возврате, а не только при смене повестки.
        live.refresh()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Тогда("^на экране «Сегодня» предупреждения о ненадёжности нет$")
    fun noReliabilityWarningOnToday() {
        val state = liveContent()
        assertNull("the warning must clear without restarting the process: $state", state.reliability)
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state, onIntent = {}) } }
            onAllNodesWithTag(TAG_RELIABILITY_BANNER, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^приёмы сегодняшнего дня остаются на месте$")
    fun theIntakesOfTodayStay() {
        val state = liveContent()
        assertTrue("the day must still show its intakes: $state", state.layout.groups.isNotEmpty())
        subscription?.cancel()
    }

    /** Состояние живого экрана: подписка была запущена при открытии, а не сейчас. */
    private fun liveContent(): TodayUiState.Content {
        val live = requireNotNull(model) { "«Сегодня» must be open in the app" }
        shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(live.state.value as? TodayUiState.Content) {
            "the live screen must show content: ${live.state.value}"
        }
    }
}
