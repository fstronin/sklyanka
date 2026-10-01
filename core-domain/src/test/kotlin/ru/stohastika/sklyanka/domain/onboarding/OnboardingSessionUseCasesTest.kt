package ru.stohastika.sklyanka.domain.onboarding

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import java.time.Instant

@DisplayName("Leaving, skipping and confirming store one fact each and leave a trace")
class OnboardingSessionUseCasesTest {

    private val readAt: Instant = Instant.parse("2026-09-25T10:00:00Z")
    private val store = FakeOnboardingStateStore()
    private val log = RecordingOnboardingLog()

    private val grantedNotifications = NotificationAccessState(
        permission = PermissionGrant.Granted(readAt),
        channel = PermissionGate.GRANTED,
        fullScreenIntent = PermissionGate.GRANTED,
    )

    private fun chain(
        vendor: VendorProfile = VendorProfile.EMUI,
        notifications: NotificationAccessState = grantedNotifications,
        exactAlarms: PermissionGrant = PermissionGrant.Granted(readAt),
        appStartup: ManualStepState = ManualStepState(confirmedByUser = false),
        batterySaving: ManualStepState = ManualStepState(confirmedByUser = false),
        recentsPin: ManualStepState = ManualStepState(confirmedByUser = false),
    ) = PermissionChecklist(vendor, notifications, exactAlarms, appStartup, batterySaving, recentsPin)

    private fun allVendorConfirmed() = chain(
        appStartup = ManualStepState(confirmedByUser = true),
        batterySaving = ManualStepState(confirmedByUser = true),
        recentsPin = ManualStepState(confirmedByUser = true),
    )

    @Test
    fun `skipping stores that the screen was seen and nothing else`() = runTest {
        LeaveOnboardingUseCase(store, log)(OnboardingOutcome.SKIPPED, chain())

        assertTrue(store.seen())
        assertEquals(listOf("left SKIPPED unresolved=3"), log.records)
    }

    @Test
    fun `passing the chain stores exactly the same fact as skipping it`() = runTest {
        LeaveOnboardingUseCase(store, log)(OnboardingOutcome.NEXT, allVendorConfirmed())

        // НАП-21: разницы «прошёл» и «пропустил» в хранимом состоянии нет — она
        // вычисляется из текущего состояния разрешений (НФТ-52).
        assertTrue(store.seen())
        assertEquals(listOf("left NEXT unresolved=0"), log.records)
    }

    @Test
    fun `the screen opens itself only until the person has seen it once`() = runTest {
        val shouldShow = ShouldShowOnboardingUseCase(store)

        assertTrue(shouldShow())
        store.markSeen()
        assertFalse(shouldShow())
    }

    @Test
    fun `manual confirmation of each vendor step is stored and logged with its step`() = runTest {
        val confirm = ConfirmReliabilityStepUseCase(store, log)

        confirm(PermissionStep.APP_STARTUP, true)
        confirm(PermissionStep.BATTERY_SAVING, true)
        confirm(PermissionStep.RECENTS_PIN, true)

        assertTrue(store.appStartupConfirmed())
        assertTrue(store.batterySavingConfirmed())
        assertTrue(store.recentsPinConfirmed())
        assertEquals(
            listOf(
                "manual APP_STARTUP confirmed=true",
                "manual BATTERY_SAVING confirmed=true",
                "manual RECENTS_PIN confirmed=true",
            ),
            log.records,
        )
    }

    @Test
    fun `manual confirmation can be withdrawn`() = runTest {
        val confirm = ConfirmReliabilityStepUseCase(store, log)
        confirm(PermissionStep.BATTERY_SAVING, true)

        confirm(PermissionStep.BATTERY_SAVING, false)

        assertFalse(store.batterySavingConfirmed())
        assertEquals(
            listOf("manual BATTERY_SAVING confirmed=true", "manual BATTERY_SAVING confirmed=false"),
            log.records,
        )
    }

    @Test
    fun `a permission step cannot be confirmed by the person`() = runTest {
        val confirm = ConfirmReliabilityStepUseCase(store, log)

        // У разрешений состояние читает система: запись «человек подтвердил» была бы ложью.
        val notificationError = runCatching { confirm(PermissionStep.NOTIFICATIONS, true) }.exceptionOrNull()
        val exactAlarmError = runCatching { confirm(PermissionStep.EXACT_ALARMS, true) }.exceptionOrNull()

        assertTrue(notificationError is IllegalArgumentException, "was: $notificationError")
        assertTrue(exactAlarmError is IllegalArgumentException, "was: $exactAlarmError")
        assertTrue(log.records.isEmpty())
    }

    @Test
    fun `a revoked permission is found by comparing two reads and not by a stored flag`() {
        val before = chain()
        val after = chain(
            notifications = grantedNotifications.copy(permission = PermissionGrant.Denied(canAsk = true)),
        )

        assertEquals(listOf(PermissionStep.NOTIFICATIONS), RevokedPermissions.between(before, after))
    }

    @Test
    fun `a newly granted permission is not a revocation`() {
        val before = chain(exactAlarms = PermissionGrant.Denied(canAsk = false))
        val after = chain()

        assertTrue(RevokedPermissions.between(before, after).isEmpty())
    }

    @Test
    fun `a withdrawn vendor confirmation counts as a revoked step too`() {
        val before = chain(appStartup = ManualStepState(confirmedByUser = true))
        val after = chain()

        assertEquals(listOf(PermissionStep.APP_STARTUP), RevokedPermissions.between(before, after))
    }

    @Test
    fun `two revocations at once are both reported in the fixed step order`() {
        val before = allVendorConfirmed()
        val after = chain(
            notifications = grantedNotifications.copy(permission = PermissionGrant.Denied(canAsk = true)),
            exactAlarms = PermissionGrant.Denied(canAsk = false),
            appStartup = ManualStepState(confirmedByUser = true),
            batterySaving = ManualStepState(confirmedByUser = true),
            recentsPin = ManualStepState(confirmedByUser = true),
        )

        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS),
            RevokedPermissions.between(before, after),
        )
    }
}

/**
 * Журнал онбординга в памяти.
 *
 * Записи сводятся к строкам без доменных данных — и это не упрощение теста, а проверка
 * НФТ-19: в записях онбординга нечего сокращать, потому что названий лекарств, дозировок
 * и имён профилей там нет ни в одном поле.
 */
internal class RecordingOnboardingLog : OnboardingLog {

    val records: MutableList<String> = mutableListOf()

    override fun screenOpened(source: OnboardingSource, checklist: PermissionChecklist) {
        records += "opened $source unresolved=${checklist.unresolvedCount}"
    }

    override fun permissionRequested(step: PermissionStep) {
        records += "requested $step"
    }

    override fun permissionResult(step: PermissionStep, granted: Boolean) {
        records += "result $step granted=$granted"
    }

    override fun vendorStepOpened(step: PermissionStep, result: StepOpenResult) {
        records += "vendor $step opened=$result"
    }

    override fun manualStepConfirmed(step: PermissionStep, confirmed: Boolean) {
        records += "manual $step confirmed=$confirmed"
    }

    override fun reliabilityRouteTaken(source: OnboardingSource, firstUnresolved: PermissionStep?) {
        records += "route $source focus=$firstUnresolved"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left $outcome unresolved=${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked $steps"
    }
}
