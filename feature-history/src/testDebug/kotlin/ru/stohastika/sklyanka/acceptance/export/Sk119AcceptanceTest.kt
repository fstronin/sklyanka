package ru.stohastika.sklyanka.acceptance.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.feature.history.FakeRenderer
import ru.stohastika.sklyanka.feature.history.FakeTransfer
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.HistoryViewModel
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд выбора состава отчёта — настоящий `HistoryViewModel` на мире сценария (SKL-119).
 *
 * Состояние собирает **продукт**, а не пересказ: приверженность считает настоящий
 * `ObserveAdherenceUseCase`, расширенные разделы — настоящий `BuildExtendedSectionsUseCase`.
 * Стенд только собирает композицию и передаёт намерения; правил состава в нём нет —
 * иначе сценарий проверял бы стенд.
 *
 * Отрисовка документа — записывающая: настоящий PDF рисуется только на устройстве
 * (`PdfDocument`), а проверке нужны **разделы**, с которыми документ собран, и сам факт сборки.
 * Вёрстка страниц проверяется отдельным шагом по настоящему композеру и раскладке.
 */
@OptIn(ExperimentalTestApi::class)
internal object ReportScopeStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: HistoryViewModel? = null

    /** Отрисовка документа записывающая: разделы, с которыми собран документ, видны сценарию. */
    val renderer = FakeRenderer()

    fun model(world: AcceptanceWorld): HistoryViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): HistoryUiState = model(world).state.value

    fun onIntent(world: AcceptanceWorld, intent: HistoryIntent) {
        model(world).onIntent(intent)
    }

    /** Открыть шторку отчёта тем же намерением, каким её открывает кнопка «Отчёт врачу». */
    fun openReport(world: AcceptanceWorld) {
        onIntent(world, HistoryIntent.ReportRequested)
    }

    /** Собрать экран и задать вопрос его композиции. */
    fun ask(world: AcceptanceWorld, question: (ComposeUiTest) -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state(world)) { onIntent(world, it) } }
            question(this)
            waitForIdle()
        }
    }

    /** Действие человека на собранном экране: нажатие уходит намерением в настоящую модель. */
    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state(world)) { onIntent(world, it) } }
            action(this)
            waitForIdle()
        }
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Composable
    private fun Content(state: HistoryUiState, onIntent: (HistoryIntent) -> Unit) {
        SklyankaTheme { HistoryScreen(state = state, onIntent = onIntent) }
    }

    private fun build(world: AcceptanceWorld): HistoryViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        renderer.rendered.clear()
        renderer.sections.clear()
        return HistoryViewModel(
            observeAdherence = ObserveAdherenceUseCase(
                activeProfile = world.profileSession.activeStore,
                history = world.history,
                calculator = AdherenceCalculator(OnTimeRule.TwoHours),
                time = world.clock.time,
                log = world.log,
            ),
            buildReport = BuildDoctorReportUseCase(
                activeProfile = world.profileSession.activeStore,
                profiles = world.repositories.profileStore,
                history = world.history,
                calculator = AdherenceCalculator(OnTimeRule.TwoHours),
                time = world.clock.time,
                log = world.log,
            ),
            buildExtended = BuildExtendedSectionsUseCase(
                activeProfile = world.profileSession.activeStore,
                history = world.history,
                calculator = AdherenceCalculator(OnTimeRule.TwoHours),
                missedPattern = MissedPatternRule(),
                time = world.clock.time,
            ),
            transferReport = TransferDoctorReportUseCase(
                transfer = FakeTransfer(),
                activeProfile = world.profileSession.activeStore,
                log = world.log,
            ),
            renderer = renderer,
            missedPattern = MissedPatternRule(),
            time = world.clock.time,
        )
    }

    private fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            dropMainDispatcher()
            lastWorld = world
            viewModel = null
        }
    }

    /** Закрыть стенд после сценария: подменённый диспетчер и корутины не утекают в соседние тесты. */
    fun close() {
        dropMainDispatcher()
        viewModel = null
        lastWorld = null
    }
}

/**
 * Экранный прогон приёмки SKL-119 — выбор состава отчёта и выгрузка истории.
 *
 * Модуль свой, а не общий: и шторка отчёта, и шторка выгрузки живут в `feature-history`,
 * а её теги объявлены `internal` — увидеть их может только прогон самого модуля
 * (`SKL-26/TDD.md §4.3`). Сценарии строки настроек помечены `@settings` и идут прогоном
 * `feature-settings`: `feature-settings` не видит шторку истории, а история — строку настроек.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk119AcceptanceTest {

    @Test
    fun theScreenScenariosOfSk119RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        ReportScopeStand.close()
    }

    private companion object {
        const val TASK = "SKL-119"

        /**
         * Группа шторок: строка настроек помечена `@settings` и идёт прогоном `feature-settings`,
         * а выгрузка журнала — `@journal` и прогоном `feature-reminder`: тег отличает сценарий
         * от чужого прогона, и он не «прячется», а исполняется своим стендом (SKL-26).
         */
        const val TAGS = "@screen and not @settings and not @journal"
    }
}
