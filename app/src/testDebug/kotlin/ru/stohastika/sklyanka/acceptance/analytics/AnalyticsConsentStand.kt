package ru.stohastika.sklyanka.acceptance.analytics

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AcceptAnalyticsFromSettingsUseCase
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.domain.analytics.ConsentedAnalytics
import ru.stohastika.sklyanka.domain.analytics.DeclineAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.GrantAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.SetAnalyticsEnabledUseCase
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.watch.WatchLinkState
import ru.stohastika.sklyanka.domain.watch.WatchLinkStateReader
import ru.stohastika.sklyanka.feature.onboarding.consent.ConsentRoute
import ru.stohastika.sklyanka.feature.onboarding.consent.ConsentViewModel
import ru.stohastika.sklyanka.feature.settings.AnalyticsConsentControl
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsNavigation
import ru.stohastika.sklyanka.feature.settings.SettingsRoute
import ru.stohastika.sklyanka.feature.settings.SettingsSystemStateReader
import ru.stohastika.sklyanka.feature.settings.SettingsViewModel
import ru.stohastika.sklyanka.navigation.AddMedicationLaunch
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.startup.StartupDecision
import ru.stohastika.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_SECONDARY_BUTTON
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд экрана согласия и строки настроек (SKL-82, группа `@screen`).
 *
 * Сценарии идут настоящим путём продукта: `ConsentRoute` и `SettingsRoute` на настоящих
 * сценариях согласия (`GrantAnalyticsConsentUseCase` и соседи) поверх настоящего
 * `ConsentedAnalytics` с подменённым sink. «До согласия — ни одного события» здесь
 * проверяется числом вызовов sink, а не чтением кода; маршрут первого запуска —
 * настоящим `SplashRoute`, как его видит `MainActivity`.
 *
 * Подменяется только платформа: системная «Назад» приходит от поддельного владельца
 * диспетчера (образец — `PermissionsStand`), а политика открывается записывающим
 * `UriHandler` вместо браузера. Подменённый главный диспетчер обязана снять прогон
 * (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object AnalyticsConsentStand {

    private val storedConsent = MutableStateFlow(AnalyticsConsent.NOT_ASKED)
    private val sink = RecordingSink()
    private val analytics = ConsentedAnalytics(sink = sink, debuggable = false, log = SilentAnalyticsLog)

    private val consentStore = object : AnalyticsConsentStore {
        override fun observe(): Flow<AnalyticsConsent> = storedConsent

        override suspend fun read(): AnalyticsConsent = storedConsent.value

        override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
            storedConsent.value = AnalyticsConsent(decision, materialVersion)
        }
    }

    private val consentViewModel = ConsentViewModel(
        grant = GrantAnalyticsConsentUseCase(consentStore, analytics),
        decline = DeclineAnalyticsConsentUseCase(consentStore, analytics),
        acceptFromSettings = AcceptAnalyticsFromSettingsUseCase(consentStore, analytics),
        setEnabled = SetAnalyticsEnabledUseCase(consentStore, analytics),
    )

    private val backOwner = RecordingBackOwner()
    private val uriHandler = RecordingUriHandler()
    private val navigation = RootNavigation()

    private var lastWorld: AcceptanceWorld? = null
    private var settingsModel: SettingsViewModel? = null

    /** Куда маршрут отдал решение (`onDecided`); `false` — экран ещё открыт. */
    var exited: Boolean = false
        private set

    /** Сколько раз строка настроек попросила открыть экран согласия. */
    var consentOpenings: Int = 0
        private set

    /** Последний адрес, открытый приложением; `null` — политику не открывали. */
    var openedUri: String? = null
        private set

    /** Решение запуска, посчитанное настоящим правилом (ПМ-25 п. 5). */
    var decision: StartupDecision? = null
        private set

    /** Открыта ли форма нового лекарства запуском ярлыка. */
    var medicationFormOpened: Boolean = false
        private set

    fun reset() {
        storedConsent.value = AnalyticsConsent.NOT_ASKED
        sink.clear()
        exited = false
        consentOpenings = 0
        openedUri = null
        decision = null
        medicationFormOpened = false
        lastWorld = null
        settingsModel = null
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    // --- Первый запуск и решение -------------------------------------------------------------

    /** Запуск первого запуска: решение считается настоящим правилом, затем показывается экран. */
    fun openFirstLaunch() {
        reset()
        decision = SplashRoute.decisionOf(
            StartupConditions(
                firstLaunch = true,
                notificationsAllowed = false,
                exactAlarmsAllowed = true,
                consentAsked = false,
            ),
        )
        showConsent(ConsentSource.FIRST_LAUNCH)
    }

    /** Повторный запуск после решения: считается то же правило, экран не показывается. */
    fun nextLaunchDecision(): StartupDecision = SplashRoute.decisionOf(
        StartupConditions(
            firstLaunch = false,
            notificationsAllowed = true,
            exactAlarmsAllowed = true,
            consentAsked = !storedConsent.value.needsDecision(AnalyticsConsentVersion.MATERIAL),
        ),
    )

    fun showConsent(source: ConsentSource, action: ComposeUiTest.() -> Unit = {}) {
        runComposeUiTest {
            setContent { ConsentContent(source) }
            action(this)
            waitForIdle()
        }
    }

    fun clickAllow() = showConsent(ConsentSource.FIRST_LAUNCH) {
        onNodeWithText(allowLabel(), useUnmergedTree = true).performClick()
    }

    fun clickDecline() = showConsent(ConsentSource.FIRST_LAUNCH) {
        onNodeWithText(declineLabel(), useUnmergedTree = true).performClick()
    }

    fun pressSystemBack() {
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.FIRST_LAUNCH) }
            backOwner.onBackPressedDispatcher.onBackPressed()
            waitForIdle()
        }
    }

    /** Экран согласия показан: заголовок виден. */
    fun titleIsShown() {
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.FIRST_LAUNCH) }
            onNodeWithText(titleLabel(), useUnmergedTree = true).assertExists()
        }
    }

    /** Габариты основной и отказной кнопок: у экрана они обязаны совпадать (ОВ-90). */
    fun buttonBounds(): Pair<Rect, Rect> {
        var bounds: Pair<Rect, Rect>? = null
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.FIRST_LAUNCH) }
            bounds = onNodeWithTag(TAG_PRIMARY_BUTTON).fetchSemanticsNode().boundsInRoot to
                onNodeWithTag(TAG_SECONDARY_BUTTON).fetchSemanticsNode().boundsInRoot
        }
        return requireNotNull(bounds) { "the buttons were not found" }
    }

    /** Тумблеры и галочки на экране: их быть не должно (ПМ-18). */
    fun toggleCount(): Int {
        var count = 0
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.FIRST_LAUNCH) }
            count = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size
        }
        return count
    }

    /** Индикатор шагов онбординга: на экран согласия он не переносится (ПМ-25). */
    fun stepIndicatorCount(): Int {
        var count = 0
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.FIRST_LAUNCH) }
            val texts = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            val descriptions = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.ContentDescription] }
            count = (texts + descriptions).count(STEP_INDICATOR::matches)
        }
        return count
    }

    // --- Ярлык --------------------------------------------------------------------------------

    fun launchByShortcut() {
        reset()
        medicationFormOpened = navigation.openFromLaunch(AddMedicationLaunch.ACTION)
        decision = SplashRoute.decisionOf(
            StartupConditions(
                firstLaunch = true,
                notificationsAllowed = false,
                exactAlarmsAllowed = true,
                consentAsked = false,
                shortcutLaunch = true,
            ),
        )
    }

    fun shortcutFormsTheMedication(): Boolean = navigation.meds == MedsDestination.NewMedication

    // --- Настройки ----------------------------------------------------------------------------

    /** Экран настроек на мире сценария, раздел «Данные» прокручен к строке аналитики. */
    fun openSettings(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit = {}) {
        if (lastWorld !== world) {
            lastWorld = world
            settingsModel = null
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
        val model = settingsModel ?: buildSettings(world).also { settingsModel = it }
        runComposeUiTest {
            setContent { SettingsContent(model) }
            onNodeWithText(analyticsLabel(), useUnmergedTree = true).performScrollTo()
            action(this)
            waitForIdle()
        }
    }

    /** Переключатель «Аналитика» выключен. */
    fun analyticsToggleIsOff(world: AcceptanceWorld) {
        openSettings(world) {
            val node = onNode(hasText(analyticsLabel()) and hasClickAction(), useUnmergedTree = false)
                .fetchSemanticsNode()
            assertEquals(
                "the analytics switch must be off until the consent screen is confirmed",
                ToggleableState.Off,
                node.config.getOrNull(SemanticsProperties.ToggleableState),
            )
        }
    }

    /** Нажать строку «Политика конфиденциальности». */
    fun clickPolicy(world: AcceptanceWorld) {
        openSettings(world) {
            onNodeWithText(policyLabel(), useUnmergedTree = true).performScrollTo().performClick()
        }
    }

    /** Включить переключатель «Аналитика»: ожидается открытие экрана согласия. */
    fun turnAnalyticsOn(world: AcceptanceWorld) {
        openSettings(world) {
            onNodeWithText(analyticsLabel(), useUnmergedTree = true).performScrollTo().performClick()
        }
    }

    fun showSettingsConsent(action: ComposeUiTest.() -> Unit = {}) {
        runComposeUiTest {
            setContent { ConsentContent(ConsentSource.SETTINGS) }
            action(this)
            waitForIdle()
        }
    }

    fun clickAllowOnSettingsConsent() = showSettingsConsent {
        onNodeWithText(allowLabel(), useUnmergedTree = true).performClick()
    }

    // --- Состояние и события ------------------------------------------------------------------

    fun consent(): AnalyticsConsent = storedConsent.value

    /** Записать отказ без экрана: так выглядит состояние после первого отказа. */
    fun recordDeclined() {
        storedConsent.value = AnalyticsConsent(
            decision = AnalyticsConsentDecision.DECLINED,
            materialVersion = AnalyticsConsentVersion.MATERIAL,
        )
    }

    /** Отказная подпись на экране, открытом из настроек (ПМ-25 п. 4). */
    fun settingsDeclineLabel(): String =
        string(ru.stohastika.sklyanka.feature.onboarding.R.string.consent_decline_settings)

    /** Подпись «Разрешить аналитику» — общая для обоих входов. */
    fun allowLabel(): String = string(ru.stohastika.sklyanka.feature.onboarding.R.string.consent_allow)

    fun events(): List<AnalyticsEvent> = sink.events.toList()

    private fun buildSettings(world: AcceptanceWorld): SettingsViewModel = SettingsViewModel(
        profiles = world.repositories.profileStore,
        context = world.profileSession.contextProvider,
        medications = world.repositories.medicationStore,
        switchProfile = world.profileSession.switch,
        system = SettingsSystemStateReader(
            reminders = world.reminders.settingsStore,
            permissions = world.system,
            watch = WatchReader(world),
        ),
        stamp = AppStamp(versionName = "1.0", versionCode = 1),
        analyticsConsent = AnalyticsConsentControl(
            observeEnabled = {
                consentStore.observe().map { it.sendingAllowed(AnalyticsConsentVersion.MATERIAL) }
            },
            setEnabled = { enabled -> SetAnalyticsEnabledUseCase(consentStore, analytics)(enabled) },
        ),
    )

    @Composable
    private fun ConsentContent(source: ConsentSource) {
        CompositionLocalProvider(
            LocalOnBackPressedDispatcherOwner provides backOwner,
            LocalLifecycleOwner provides backOwner,
            LocalUriHandler provides uriHandler,
        ) {
            SklyankaTheme {
                ConsentRoute(
                    viewModel = consentViewModel,
                    source = source,
                    onDecided = { exited = true },
                )
            }
        }
    }

    @Composable
    private fun SettingsContent(viewModel: SettingsViewModel) {
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            SklyankaTheme {
                SettingsRoute(
                    viewModel = viewModel,
                    navigation = SettingsNavigation(openAnalyticsConsent = { consentOpenings += 1 }),
                )
            }
        }
    }

    private fun titleLabel(): String = string(ru.stohastika.sklyanka.feature.onboarding.R.string.consent_title)

    private fun declineLabel(): String = string(ru.stohastika.sklyanka.feature.onboarding.R.string.consent_decline)

    private fun analyticsLabel(): String = string(R.string.settings_analytics)

    private fun policyLabel(): String = string(R.string.settings_policy)

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class RecordingSink : AnalyticsSink {
        val events = mutableListOf<AnalyticsEvent>()
        var activations = 0
            private set

        override suspend fun activate() {
            activations++
        }

        override suspend fun deactivate() = Unit

        override fun send(event: AnalyticsEvent) {
            events += event
        }

        fun clear() {
            events.clear()
            activations = 0
        }
    }

    private object SilentAnalyticsLog : AnalyticsLog {
        override fun activated() = Unit

        override fun deactivated() = Unit

        override fun unavailable(reason: String) = Unit

        override fun debugSuppressed() = Unit

        override fun sendFailed(reason: String) = Unit
    }

    private class RecordingUriHandler : UriHandler {
        override fun openUri(uri: String) {
            AnalyticsConsentStand.openedUri = uri
        }
    }

    private class WatchReader(private val world: AcceptanceWorld) : WatchLinkStateReader {
        override suspend fun read(): WatchLinkState = world.system.watch
    }

    private val STEP_INDICATOR = Regex("^Шаг \\d+ из \\d+$")
}

/**
 * Владелец диспетчера системной «Назад» без Activity (образец — `PermissionsStand`).
 *
 * Настоящий `BackHandler` маршрута регистрируется на владельце, которого даёт композиция;
 * стенд подставляет сюда свой, и нажатие идёт через обработчик продукта.
 */
internal class RecordingBackOwner :
    OnBackPressedDispatcherOwner,
    LifecycleOwner {

    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
