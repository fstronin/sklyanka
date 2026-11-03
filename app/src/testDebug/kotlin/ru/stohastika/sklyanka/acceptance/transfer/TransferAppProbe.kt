package ru.stohastika.sklyanka.acceptance.transfer

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransfer
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransferIds
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.domain.onboarding.ManualStepState
import ru.stohastika.sklyanka.domain.onboarding.NotificationAccessState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionGate
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.stohastika.sklyanka.domain.transfer.BuildTransferPlan
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import ru.stohastika.sklyanka.domain.transfer.ValidateSnapshot
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferRoute
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferViewModel
import ru.stohastika.sklyanka.feature.settings.transfer.TransferIntent
import ru.stohastika.sklyanka.feature.settings.transfer.TransferReportScreen
import ru.stohastika.sklyanka.feature.settings.transfer.TransferUiState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant

/**
 * Стенд композиции переноса (SKL-19, группа `@composition`).
 *
 * Здесь собирается настоящая связка продукта: `DataTransferRoute` с настоящим
 * `DataTransferViewModel` на мире сценария и настоящий экран разрешений
 * (`feature-onboarding`) — единственное место, где оба экрана видны сразу
 * (`SKL-26/TDD.md §4.3`). Переход к разрешениям проверяется нажатием на реальной
 * кнопке отчёта: маршрут — продукт, а не пересказ.
 */
@OptIn(ExperimentalTestApi::class)
internal object TransferAppProbe {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: DataTransferViewModel? = null

    /** Сколько раз маршрут позвал переход к разрешениям (ПЕР-25). */
    var permissionsOpened: Int = 0
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            permissionsOpened = 0
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): DataTransferViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): TransferUiState = model(world).state.value

    fun onIntent(world: AcceptanceWorld, intent: TransferIntent) {
        model(world).onIntent(intent)
    }

    fun selectMode(world: AcceptanceWorld, mode: ru.stohastika.sklyanka.domain.transfer.TransferMode) {
        onIntent(world, TransferIntent.SelectMode(mode))
    }

    fun materialize(world: AcceptanceWorld, handle: String = ScenarioTransfer.IMPORT_HANDLE): String {
        val prepared = requireNotNull(world.transfer.prepared) { "no file was prepared for the import" }
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        world.transfer.files.put(handle, world.transfer.codec.encode(prepared))
        return handle
    }

    fun chooseFile(world: AcceptanceWorld, handle: String? = null) {
        model(world)
        val target = handle
            ?: if (world.transfer.prepared != null) materialize(world) else ScenarioTransfer.IMPORT_HANDLE
        onIntent(world, TransferIntent.FileChosen(target))
    }

    fun chooseFileAndContinue(world: AcceptanceWorld, handle: String? = null) {
        chooseFile(world, handle)
        onIntent(world, TransferIntent.ContinueFromSummary)
    }

    /**
     * Подтверждение замены: слово введено, кнопка нажата (ОВ-67).
     *
     * Слово кладётся в состояние тем же намерением, что шлёт поле: без него кнопка
     * недоступна, и «человек подтвердил» было бы неправдой.
     */
    fun apply(world: AcceptanceWorld) {
        onIntent(world, TransferIntent.ConfirmationWordChanged(context.getString(R.string.transfer_replace_word)))
        onIntent(world, TransferIntent.Apply)
    }

    // --- Композиция ----------------------------------------------------------------------------

    /** Собрать настоящий маршрут переноса и задать ему вопрос. */
    fun askRoute(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Pinned { Route(world) } }
            question()
            waitForIdle()
        }
    }

    @Composable
    private fun Route(world: AcceptanceWorld) {
        DataTransferRoute(
            viewModel = model(world),
            onOpenPermissions = { permissionsOpened += 1 },
            onBack = {},
            modifier = Modifier,
        )
    }

    /**
     * Настоящий экран отчёта с содержимым, снятым с ViewModel до композиции.
     *
     * Маршрут `DataTransferRoute` на разборке зовёт `Leave` и возвращает перенос к началу
     * (ПЕР-17) — поэтому отчёт читается отдельным шагом, а связка «отчёт → разрешения»
     * проверяется сборкой маршрута один раз, там, где сценарий говорит о переходе.
     */
    fun askReport(
        report: TransferReport,
        medicationNames: Map<String, String>,
        safetyCopyName: String?,
        question: ComposeUiTest.() -> Unit,
    ) {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    TransferReportScreen(
                        report = report,
                        medicationNames = medicationNames,
                        safetyCopyName = safetyCopyName,
                        onContinue = {},
                    )
                }
            }
            question()
            waitForIdle()
        }
    }

    /** Настоящий экран разрешений — тот же, что открывается при первой установке. */
    fun askPermissions(question: ComposeUiTest.() -> Unit) {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    PermissionsScreen(
                        state = permissionsState(),
                        actions = permissionsActions(),
                        // Плашка перерыва на этом стенде не показывается: пояс не важен.
                        toLocal = { instant -> instant.atZone(java.time.ZoneOffset.UTC).toLocalDateTime() },
                    )
                }
            }
            question()
            waitForIdle()
        }
    }

    /**
     * Владелец реестра результатов без результата: `DataTransferRoute` регистрирует
     * у него системный выбор файла при сборке. Сам выбор сценарий не открывает —
     * файл уже выбран человеком, — и заглушка здесь порт платформы, а не правило.
     */
    private object NoResultRegistryOwner : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }

    @Composable
    private fun Pinned(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoResultRegistryOwner) {
            content()
        }
    }

    private fun permissionsState(): OnboardingUiState = OnboardingUiState(
        source = OnboardingSource.FIRST_LAUNCH,
        checklist = PermissionChecklist(
            vendor = VendorProfile.EMUI,
            notifications = NotificationAccessState(
                permission = PermissionGrant.Granted(Instant.EPOCH),
                channel = PermissionGate.GRANTED,
                fullScreenIntent = PermissionGate.GRANTED,
            ),
            exactAlarms = PermissionGrant.Granted(Instant.EPOCH),
            appStartup = ManualStepState(confirmedByUser = false),
            batterySaving = ManualStepState(confirmedByUser = false),
            recentsPin = ManualStepState(confirmedByUser = false),
        ),
    )

    private fun permissionsActions(): PermissionsActions = PermissionsActions(
        onStepAction = {},
        onManualConfirmed = { _, _ -> },
        onNext = {},
        onSkip = {},
        onDone = {},
    )

    private fun build(world: AcceptanceWorld): DataTransferViewModel {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
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
            // События переноса стенду не проверяются, но шов настоящий (SKL-165).
            analytics = world.analytics.recorder(CoroutineScope(Dispatchers.Unconfined)),
            time = world.clock.time,
        )
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** Контекст в шагах: строки экрана разрешений берутся из него. */
    val context: Context get() = ApplicationProvider.getApplicationContext()
}
