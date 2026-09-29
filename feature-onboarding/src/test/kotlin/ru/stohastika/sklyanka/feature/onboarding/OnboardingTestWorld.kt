package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.ConfirmAutostartUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
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
 * Мир проверок экрана разрешений.
 *
 * Сценарии собираются настоящими — из домена, — а подменяются только порты: расчёт состояния
 * цепочки проверяется тот же, что работает в продукте, а не его копия в тесте.
 */
internal class OnboardingTestWorld(now: Instant = NOW, zone: ZoneId = ZoneId.of("Europe/Moscow")) {

    val time = FixedTimeSource(now, zone)
    val permissions = FakePermissions()
    val store = FakeStateStore()
    val hints = FakeHints()
    val requests = FakeRequestSupport()
    val navigator = FakeNavigator()
    val log = RecordingLog()

    private val readChecklist = ReadPermissionChecklistUseCase(
        permissions = permissions,
        store = store,
        hints = hints,
        requests = requests,
        time = time,
    )

    fun viewModel(): OnboardingViewModel = OnboardingViewModel(
        readChecklist = readChecklist,
        confirmAutostart = ConfirmAutostartUseCase(store, log),
        leaveOnboarding = LeaveOnboardingUseCase(store, log),
        navigator = navigator,
        log = log,
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

internal class FakeStateStore(private var seen: Boolean = false, private var confirmed: Boolean = false) :
    OnboardingStateStore {

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        seen = true
    }

    override suspend fun autostartConfirmed(): Boolean = confirmed

    override suspend fun setAutostartConfirmed(confirmed: Boolean) {
        this.confirmed = confirmed
    }
}

internal class FakeHints(var batteryIgnored: Boolean = false) : AutostartHintReader {

    override fun batteryOptimizationIgnored(): Boolean = batteryIgnored
}

internal class FakeRequestSupport : PermissionRequestSupport {

    /** По умолчанию — как на Android 13 и новее (ПЛ-13, ПЛ-14). */
    val requestable: MutableSet<PermissionStep> = mutableSetOf(PermissionStep.NOTIFICATIONS)

    override fun canRequestFromCode(step: PermissionStep): Boolean = step in requestable
}

internal class FakeNavigator(var autostartResult: AutostartOpenResult = AutostartOpenResult.OPENED) :
    SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    override fun openExactAlarmSettings() {
        opened += EXACT_ALARMS
    }

    override fun openNotificationSettings() {
        opened += NOTIFICATIONS
    }

    override fun openAutostartSettings(): AutostartOpenResult {
        opened += AUTOSTART
        return autostartResult
    }

    companion object {
        const val EXACT_ALARMS = "exact-alarms"
        const val NOTIFICATIONS = "notifications"
        const val AUTOSTART = "autostart"
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

    override fun autostartSettingsOpened(result: AutostartOpenResult) {
        records += "autostart:$result"
    }

    override fun autostartConfirmedByUser(confirmed: Boolean) {
        records += "confirmed:$confirmed"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left:$outcome:${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked:${steps.joinToString(",")}"
    }
}
