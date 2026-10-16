package ru.stohastika.sklyanka.acceptance.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.BuildHistoryCsvTableUseCase
import ru.stohastika.sklyanka.domain.history.ExportHistoryCsvUseCase
import ru.stohastika.sklyanka.domain.history.HistoryCsvDocument
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.feature.history.ExportHistoryIntent
import ru.stohastika.sklyanka.feature.history.ExportHistorySheet
import ru.stohastika.sklyanka.feature.history.ExportHistoryUiState
import ru.stohastika.sklyanka.feature.history.ExportHistoryViewModel
import ru.stohastika.sklyanka.feature.history.FakeTransfer
import ru.stohastika.sklyanka.feature.history.HistoryCsvComposer
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд шторки выгрузки истории — настоящий `ExportHistoryViewModel` на мире сценария (SKL-119).
 *
 * Правил в стенде нет: таблицу выгрузки собирает `BuildHistoryCsvTableUseCase`, слова файла —
 * настоящий `HistoryCsvComposer`, доступ читает `ObserveProStatusUseCase` поверх кэша подписки
 * мира, а запись идёт файловым портом мира. Стенд собирает композицию шторки и передаёт
 * намерения — и только это.
 *
 * Шторку стенд собирает **саму**, а не её маршрут: системные диалоги (выбор места и обмен)
 * умеет получить только `composable` в активности, и в прогоне им места нет — как и у стендов
 * отчёта (`ReportShareStand`, `SavePlaceStand`).
 */
@OptIn(ExperimentalTestApi::class)
internal object ExportStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: ExportHistoryViewModel? = null
    private var collector: CoroutineScope? = null

    /** Обращения к витрине: строка настроек без доступа открывает её вместо шторки (Р-22). */
    var showcaseRequests: Int = 0

        private set

    fun model(world: AcceptanceWorld): ExportHistoryViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): ExportHistoryUiState = model(world).state.value

    /** Нажатие строки «Экспорт истории»: с доступом — шторка, без доступа — витрина. */
    fun open(world: AcceptanceWorld) {
        model(world)
        onIntent(world, ExportHistoryIntent.Requested)
    }

    fun choosePeriod(world: AcceptanceWorld, preset: ReportPreset) {
        onIntent(world, ExportHistoryIntent.PeriodChosen(preset))
    }

    fun document(world: AcceptanceWorld): HistoryCsvDocument =
        requireNotNull(state(world).sheet?.document) { "the export sheet must be open" }

    fun onIntent(world: AcceptanceWorld, intent: ExportHistoryIntent) {
        model(world).onIntent(intent)
    }

    fun ask(world: AcceptanceWorld, question: (ComposeUiTest) -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state(world)) { onIntent(world, it) } }
            question(this)
            waitForIdle()
        }
    }

    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state(world)) { onIntent(world, it) } }
            action(this)
            waitForIdle()
        }
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun close() {
        Dispatchers.resetMain()
        viewModel = null
        lastWorld = null
        collector = null
    }

    @Composable
    private fun Content(state: ExportHistoryUiState, onIntent: (ExportHistoryIntent) -> Unit) {
        val sheet = state.sheet
        SklyankaTheme {
            if (sheet != null) ExportHistorySheet(sheet, state.outcome, onIntent)
        }
    }

    private fun build(world: AcceptanceWorld): ExportHistoryViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val collecting = CoroutineScope(dispatcher)
        collector = collecting
        showcaseRequests = 0
        return ExportHistoryViewModel(
            buildTable = BuildHistoryCsvTableUseCase(
                activeProfile = world.profileSession.activeStore,
                profiles = world.repositories.profileStore,
                history = world.history,
                onTimeRule = OnTimeRule.TwoHours,
                time = world.clock.time,
            ),
            composer = HistoryCsvComposer(ApplicationProvider.getApplicationContext()),
            export = ExportHistoryCsvUseCase(
                files = world.transfer.files,
                transfer = FakeTransfer(),
                activeProfile = world.profileSession.activeStore,
                log = world.log,
            ),
            proStatus = world.pro.statusUseCase,
            time = world.clock.time,
        ).also { model -> collecting.launch { model.showcase.collect { showcaseRequests++ } } }
    }

    private fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            close()
            lastWorld = world
        }
    }
}
