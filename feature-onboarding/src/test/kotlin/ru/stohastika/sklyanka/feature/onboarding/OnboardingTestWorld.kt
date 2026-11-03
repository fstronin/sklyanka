package ru.stohastika.sklyanka.feature.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.onboarding.ConfirmReliabilityStepUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorProfileReader
import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Мир проверок экрана «Настроить надёжность».
 *
 * Сценарии собираются настоящими — из домена, — а подменяются только порты: расчёт состояния
 * цепочки проверяется тот же, что работает в продукте, а не его копия в тесте.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OnboardingTestWorld(
    scheduler: TestCoroutineScheduler,
    now: Instant = NOW,
    zone: ZoneId = ZoneId.of("Europe/Moscow"),
    analyticsConsent: AnalyticsConsentState = AnalyticsConsentState.ALLOWED,
) {

    val time = FixedTimeSource(now, zone)
    val permissions = FakePermissions()
    val store = FakeStateStore()
    val hints = FakeHints()
    val requests = FakeRequestSupport()
    val vendor = FakeVendor()
    val navigator = FakeNavigator()
    val log = RecordingLog()

    /** Аналитика по умолчанию разрешена: иначе проверять было бы нечего (АН-2). */
    val analytics = AnalyticsWorld(analyticsConsent)

    /** Запись асинхронна и идёт в планировщик прогона: события видны после `advanceUntilIdle()`. */
    private val recorder = analytics.recorder(CoroutineScope(StandardTestDispatcher(scheduler)))

    private val readChecklist = ReadPermissionChecklistUseCase(
        permissions = permissions,
        store = store,
        hints = hints,
        requests = requests,
        time = time,
        vendor = vendor,
    )

    fun viewModel(): OnboardingViewModel = OnboardingViewModel(
        readChecklist = readChecklist,
        confirmStep = ConfirmReliabilityStepUseCase(store, log),
        leaveOnboarding = LeaveOnboardingUseCase(store, log),
        navigator = navigator,
        log = log,
        recorder = recorder,
        time = time,
    )

    companion object {
        val NOW: Instant = Instant.parse("2026-09-25T07:00:00Z")
    }
}

internal class FakePermissions : PermissionStateReader {

    val states: MutableMap<ReminderPermission, PermissionState> = ReminderPermission.entries
        .associateWith { PermissionState.GRANTED }
        .toMutableMap()
        .also { it[ReminderPermission.AUTOSTART] = PermissionState.UNKNOWN }

    override fun state(permission: ReminderPermission): PermissionState = states[permission] ?: PermissionState.UNKNOWN

    fun deny(permission: ReminderPermission) {
        states[permission] = PermissionState.DENIED
    }

    fun grant(permission: ReminderPermission) {
        states[permission] = PermissionState.GRANTED
    }
}

internal class FakeStateStore(
    private var seen: Boolean = false,
    private var appStartup: Boolean = false,
    private var batterySaving: Boolean = false,
    private var recentsPin: Boolean = false,
) : OnboardingStateStore {

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        seen = true
    }

    override suspend fun appStartupConfirmed(): Boolean = appStartup

    override suspend fun setAppStartupConfirmed(confirmed: Boolean) {
        appStartup = confirmed
    }

    override suspend fun batterySavingConfirmed(): Boolean = batterySaving

    override suspend fun setBatterySavingConfirmed(confirmed: Boolean) {
        batterySaving = confirmed
    }

    override suspend fun recentsPinConfirmed(): Boolean = recentsPin

    override suspend fun setRecentsPinConfirmed(confirmed: Boolean) {
        recentsPin = confirmed
    }
}

internal class FakeHints(var batteryIgnored: Boolean = false) : AutostartHintReader {

    override fun batteryOptimizationIgnored(): Boolean = batteryIgnored
}

internal class FakeVendor(var vendor: VendorProfile = VendorProfile.EMUI) : VendorProfileReader {

    override fun profile(): VendorProfile = vendor
}

internal class FakeRequestSupport : PermissionRequestSupport {

    /** По умолчанию — как на Android 13 и новее (ПЛ-13, ПЛ-14). */
    val requestable: MutableSet<PermissionStep> = mutableSetOf(PermissionStep.NOTIFICATIONS)

    override fun canRequestFromCode(step: PermissionStep): Boolean = step in requestable
}

internal class FakeNavigator(
    var appStartupResult: StepOpenResult = StepOpenResult.OPENED,
    var batterySavingResult: StepOpenResult = StepOpenResult.OPENED,
) : SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    /** Какой профиль получил навигатор: шаг обязан вести в раздел своей прошивки. */
    val openedProfiles: MutableList<VendorProfile> = mutableListOf()

    override fun openExactAlarmSettings(): StepOpenResult {
        opened += EXACT_ALARMS
        return StepOpenResult.OPENED
    }

    override fun openNotificationSettings(channelId: String?): StepOpenResult {
        opened += NOTIFICATIONS
        return StepOpenResult.OPENED
    }

    override fun openFullScreenIntentSettings(): StepOpenResult {
        opened += FULL_SCREEN
        return StepOpenResult.OPENED
    }

    override fun openVendorStep(profile: VendorProfile, step: PermissionStep): StepOpenResult {
        openedProfiles += profile
        return when (step) {
            PermissionStep.APP_STARTUP -> {
                opened += APP_STARTUP
                appStartupResult
            }

            PermissionStep.BATTERY_SAVING -> {
                opened += BATTERY_SAVING
                batterySavingResult
            }

            // У закрепления в недавних системного экрана нет, и сюда оно не приходит.
            else -> StepOpenResult.OPENED
        }
    }

    companion object {
        const val EXACT_ALARMS = "exact-alarms"
        const val NOTIFICATIONS = "notifications"
        const val FULL_SCREEN = "full-screen"
        const val APP_STARTUP = "app-startup"
        const val BATTERY_SAVING = "battery-saving"
    }
}

internal class RecordingLog : OnboardingLog {

    val records: MutableList<String> = mutableListOf()

    override fun screenOpened(source: OnboardingSource, checklist: PermissionChecklist) {
        records += "opened:$source:${checklist.unresolvedCount}"
    }

    override fun permissionRequested(step: PermissionStep) {
        records += "requested:$step"
    }

    override fun permissionResult(step: PermissionStep, granted: Boolean) {
        records += "result:$step:$granted"
    }

    override fun vendorStepOpened(step: PermissionStep, result: StepOpenResult) {
        records += "vendor:$step:$result"
    }

    override fun manualStepConfirmed(step: PermissionStep, confirmed: Boolean) {
        records += "manual:$step:$confirmed"
    }

    override fun reliabilityRouteTaken(source: OnboardingSource, firstUnresolved: PermissionStep?) {
        records += "route:$source:$firstUnresolved"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left:$outcome:${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked:${steps.joinToString(",")}"
    }
}
