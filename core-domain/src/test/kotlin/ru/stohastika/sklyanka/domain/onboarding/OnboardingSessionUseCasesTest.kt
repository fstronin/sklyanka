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

    private fun chain(
        notifications: PermissionGrant = PermissionGrant.Granted(readAt),
        exactAlarms: PermissionGrant = PermissionGrant.Granted(readAt),
        autostart: AutostartState = AutostartState(confirmedByUser = false),
    ) = PermissionChecklist(notifications, exactAlarms, autostart)

    @Test
    fun `skipping stores that the screen was seen and nothing else`() = runTest {
        LeaveOnboardingUseCase(store, log)(OnboardingOutcome.SKIPPED, chain())

        assertTrue(store.seen())
        assertEquals(listOf("left SKIPPED unresolved=1"), log.records)
    }

    @Test
    fun `passing the chain stores exactly the same fact as skipping it`() = runTest {
        LeaveOnboardingUseCase(store, log)(
            OnboardingOutcome.NEXT,
            chain(autostart = AutostartState(confirmedByUser = true)),
        )

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
    fun `manual confirmation of the autostart step is stored and logged`() = runTest {
        ConfirmAutostartUseCase(store, log)(true)

        assertTrue(store.autostartConfirmed())
        assertEquals(listOf("autostart confirmed=true"), log.records)
    }

    @Test
    fun `manual confirmation can be withdrawn`() = runTest {
        val confirm = ConfirmAutostartUseCase(store, log)
        confirm(true)

        confirm(false)

        assertFalse(store.autostartConfirmed())
        assertEquals(listOf("autostart confirmed=true", "autostart confirmed=false"), log.records)
    }

    @Test
    fun `a revoked permission is found by comparing two reads and not by a stored flag`() {
        val before = chain()
        val after = chain(notifications = PermissionGrant.Denied(canAsk = true))

        assertEquals(listOf(PermissionStep.NOTIFICATIONS), RevokedPermissions.between(before, after))
    }

    @Test
    fun `a newly granted permission is not a revocation`() {
        val before = chain(exactAlarms = PermissionGrant.Denied(canAsk = false))
        val after = chain()

        assertTrue(RevokedPermissions.between(before, after).isEmpty())
    }

    @Test
    fun `a withdrawn autostart confirmation counts as a revoked step too`() {
        val before = chain(autostart = AutostartState(confirmedByUser = true))
        val after = chain()

        assertEquals(listOf(PermissionStep.AUTOSTART), RevokedPermissions.between(before, after))
    }

    @Test
    fun `two revocations at once are both reported in the fixed step order`() {
        val before = chain(autostart = AutostartState(confirmedByUser = true))
        val after = PermissionChecklist(
            notifications = PermissionGrant.Denied(canAsk = true),
            exactAlarms = PermissionGrant.Denied(canAsk = false),
            autostart = AutostartState(confirmedByUser = true),
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

    override fun autostartSettingsOpened(result: AutostartOpenResult) {
        records += "autostart settings $result"
    }

    override fun autostartConfirmedByUser(confirmed: Boolean) {
        records += "autostart confirmed=$confirmed"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left $outcome unresolved=${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked $steps"
    }
}
