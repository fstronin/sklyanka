package ru.heuri.sklyanka.acceptance.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.acceptance.world.ScenarioTransfer
import ru.heuri.sklyanka.acceptance.world.ScenarioTransferIds
import ru.heuri.sklyanka.data.transfer.JsonTransferCodec
import ru.heuri.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.heuri.sklyanka.domain.transfer.BuildTransferPlan
import ru.heuri.sklyanka.domain.transfer.ExportDataUseCase
import ru.heuri.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.heuri.sklyanka.domain.transfer.TransferCodec
import ru.heuri.sklyanka.domain.transfer.TransferCounts
import ru.heuri.sklyanka.domain.transfer.TransferFormat
import ru.heuri.sklyanka.domain.transfer.TransferMode
import ru.heuri.sklyanka.domain.transfer.ValidateSnapshot
import ru.heuri.sklyanka.feature.settings.transfer.DataTransferScreen
import ru.heuri.sklyanka.feature.settings.transfer.DataTransferViewModel
import ru.heuri.sklyanka.feature.settings.transfer.MatchReviewScreen
import ru.heuri.sklyanka.feature.settings.transfer.ReplaceConfirmScreen
import ru.heuri.sklyanka.feature.settings.transfer.TransferIntent
import ru.heuri.sklyanka.feature.settings.transfer.TransferReportScreen
import ru.heuri.sklyanka.feature.settings.transfer.TransferStep
import ru.heuri.sklyanka.feature.settings.transfer.TransferSummaryScreen
import ru.heuri.sklyanka.feature.settings.transfer.TransferUiState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд переноса данных для шагов приёмки: настоящий `DataTransferViewModel` на мире сценария.
 *
 * Состояние экрана — то же, что у продукта: `DataTransferViewModel` собирается поверх
 * портов мира (`TransferStore`, `TransferFiles`, `TransferSettingsStore`) и настоящего
 * кодека `JsonTransferCodec`. Правил в стенде нет: порядок шагов, отказы, сводка и отчёт —
 * код ViewModel; шаги только нажимают и читают. Именно это делает проверки отказов
 * (ПЕР-27, ПЕР-31) доказательными: «человеку сказано» проверяется у того состояния,
 * которое продукт действительно показывает.
 *
 * Для сценариев, чья загрузка идёт общим шагом артефакта (`человек подтверждает загрузку`),
 * состояние отчёта собирается из мира: у них применение выполняет доменный шаг, а не
 * ViewModel стенда. Это отмечено у [reportState].
 */
@OptIn(ExperimentalTestApi::class)
internal object TransferScreenProbe {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: DataTransferViewModel? = null

    /** Сколько раз маршрут позвал переход к разрешениям (ПЕР-25). */
    var permissionsOpened: Int = 0
        private set

    /** Экран принял нажатие «Сохранить файл»: место выбирает система (ПЕР-32, НФТ-17). */
    var exportRequested: Boolean = false
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            permissionsOpened = 0
            exportRequested = false
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    /** Перезапуск приложения: ViewModel собирается заново над тем же миром (ПЕР-31). */
    fun restart(world: AcceptanceWorld) {
        reset(world)
        viewModel = null
    }

    fun model(world: AcceptanceWorld): DataTransferViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): TransferUiState = model(world).state.value

    fun onIntent(world: AcceptanceWorld, intent: TransferIntent) {
        model(world).onIntent(intent)
    }

    // --- Действия ------------------------------------------------------------------------------

    /** Выгрузка: место выбрано системой, дальше — настоящий `ExportDataUseCase`. */
    fun export(world: AcceptanceWorld, handle: String = ScenarioTransfer.EXPORT_HANDLE) {
        onIntent(world, TransferIntent.SaveTo(handle))
    }

    /**
     * Файл, подготовленный доменными шагами, выкладывается в выбранное место настоящим
     * кодеком: экран обязан получить провод, а не снимок в обход формата.
     */
    fun materialize(world: AcceptanceWorld, handle: String = ScenarioTransfer.IMPORT_HANDLE): String {
        val prepared = requireNotNull(world.transfer.prepared) { "no file was prepared for the import" }
        world.transfer.codec = codec(world)
        world.transfer.files.put(handle, world.transfer.codec.encode(prepared))
        return handle
    }

    fun chooseFile(world: AcceptanceWorld, handle: String? = null) {
        model(world)
        // Файл либо подготовлен доменными шагами (тогда его выкладывает настоящий кодек),
        // либо уже лежит в выбранном месте повреждённым (сценарии отказов).
        val target = handle
            ?: if (world.transfer.prepared != null) materialize(world) else ScenarioTransfer.IMPORT_HANDLE
        onIntent(world, TransferIntent.FileChosen(target))
    }

    /** «Человек выбирает файл» доводит до списка совпадений либо до подтверждения замены. */
    fun chooseFileAndContinue(world: AcceptanceWorld, handle: String? = null) {
        chooseFile(world, handle)
        if (state(world).step == TransferStep.SUMMARY) {
            onIntent(world, TransferIntent.ContinueFromSummary)
        }
    }

    fun apply(world: AcceptanceWorld) {
        onIntent(world, TransferIntent.Apply)
    }

    /** Режим, выбранный человеком на экране (ПЕР-14). */
    fun selectMode(world: AcceptanceWorld, mode: TransferMode) {
        onIntent(world, TransferIntent.SelectMode(mode))
    }

    /**
     * Состояние отчёта для сценариев, чью загрузку исполнил общий доменный шаг.
     *
     * Такой шаг пишет в мир и не идёт через ViewModel стенда; экран отчёта обязан показать
     * то же применение, и состояние собирается из его следов — отчёта, замечаний и имён
     * лекарств файла. Правил здесь нет: всё, что показывается, посчитал домен.
     */
    fun reportState(world: AcceptanceWorld): TransferUiState = TransferUiState(
        step = TransferStep.REPORT,
        deviceCounts = world.transfer.counts(),
        fileCounts = world.transfer.prepared?.counts,
        report = world.transfer.lastReport,
        medicationNames = world.transfer.prepared?.medications?.associate { it.id to it.name }.orEmpty(),
        safetyCopyName = world.transfer.files.safetyCopy()?.name,
        zone = world.clock.zone,
    )

    // --- Композиция ----------------------------------------------------------------------------

    fun ask(world: AcceptanceWorld, state: TransferUiState = state(world), question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent {
                Content(state = state, onIntent = {}, onSave = {}, onOpenPermissions = {})
            }
            question()
            waitForIdle()
        }
    }

    /**
     * Действие человека на собранном экране.
     *
     * Нажатия копятся во время композиции и применяются после неё: намерение экрана —
     * результат `onClick`, и обрабатывает его тот же ViewModel, что у продукта.
     */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent {
                Content(
                    state = state(world),
                    onIntent = { intent -> performed += { onIntent(world, intent) } },
                    onSave = { performed += { exportRequested = true } },
                    onOpenPermissions = { performed += { permissionsOpened += 1 } },
                )
            }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    /** Есть ли на собранном экране текст: сверка идёт с ресурсами модуля, а не с литералом. */
    fun hasText(state: TransferUiState, text: String): Boolean {
        var found = false
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSave = {}, onOpenPermissions = {}) }
            found = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    /** Все тексты собранного экрана: ими проверяются «ничего не предлагает» и «нет строки». */
    fun texts(state: TransferUiState): List<String> {
        var texts: List<String> = emptyList()
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSave = {}, onOpenPermissions = {}) }
            texts = onAllNodes(
                SemanticsMatcher("has text") { node ->
                    node.config.contains(SemanticsProperties.Text)
                },
            ).fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
            }
        }
        return texts
    }

    fun hasTag(state: TransferUiState, tag: String): Boolean {
        var found = false
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSave = {}, onOpenPermissions = {}) }
            found = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    fun click(world: AcceptanceWorld, tag: String) {
        act(world) { onNodeWithTag(tag).performClick() }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun codec(world: AcceptanceWorld): TransferCodec = JsonTransferCodec(world.clock.time)

    private fun build(world: AcceptanceWorld): DataTransferViewModel {
        world.transfer.codec = codec(world)
        val transfer = world.transfer
        val ids = ScenarioTransferIds()
        return DataTransferViewModel(
            exportData = ExportDataUseCase(
                store = transfer.store,
                settings = transfer.settings,
                codec = transfer.codec,
                files = transfer.files,
                time = world.clock.time,
                log = world.log,
                app = ScenarioTransfer.APP_STAMP,
            ),
            readFile = ReadTransferFileUseCase(
                files = transfer.files,
                codec = transfer.codec,
                validate = ValidateSnapshot(),
                log = world.log,
            ),
            applyTransfer = ApplyTransferUseCase(
                store = transfer.store,
                settings = transfer.settings,
                codec = transfer.codec,
                files = transfer.files,
                activeProfile = world.profileSession.activeStore,
                replanner = transfer.replanner,
                log = world.log,
                buildPlan = BuildTransferPlan(ids),
            ),
            store = transfer.store,
            settings = transfer.settings,
            files = transfer.files,
            ids = ids,
            time = world.clock.time,
        )
    }

    @Composable
    private fun Content(
        state: TransferUiState,
        onIntent: (TransferIntent) -> Unit,
        onSave: () -> Unit,
        onOpenPermissions: () -> Unit,
    ) {
        SklyankaTheme {
            when (state.step) {
                TransferStep.OVERVIEW -> DataTransferScreen(
                    state = state,
                    onIntent = onIntent,
                    onSave = onSave,
                    onPickFile = {},
                    onBack = {},
                )

                TransferStep.SUMMARY -> TransferSummaryScreen(
                    counts = state.fileCounts ?: EMPTY_COUNTS,
                    state = state,
                    zone = state.zone,
                    onContinue = { onIntent(TransferIntent.ContinueFromSummary) },
                    onCancel = {},
                )

                TransferStep.MATCHES -> MatchReviewScreen(
                    state = state,
                    onIntent = onIntent,
                    onCancel = {},
                )

                TransferStep.REPLACE_CONFIRM -> ReplaceConfirmScreen(
                    device = state.deviceCounts ?: EMPTY_COUNTS,
                    file = state.fileCounts ?: EMPTY_COUNTS,
                    safetyCopyName = TransferFormat.SAFETY_COPY_NAME,
                    onConfirm = { onIntent(TransferIntent.Apply) },
                    onCancel = {},
                )

                TransferStep.REPORT -> state.report?.let { report ->
                    TransferReportScreen(
                        report = report,
                        medicationNames = state.medicationNames,
                        safetyCopyName = state.safetyCopyName,
                        onContinue = onOpenPermissions,
                    )
                }
            }
        }
    }

    private val EMPTY_COUNTS = TransferCounts(profiles = 0, medications = 0, archived = 0, intakes = 0)

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}
