package ru.stohastika.sklyanka.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import java.time.Instant

@DisplayName("The notifications step names its issue in the words of the banner and never fakes a vendor check")
class StepStateTest {

    private val readAt: Instant = Instant.parse("2026-10-01T11:08:00Z")

    private fun state(
        permission: PermissionGrant = PermissionGrant.Granted(readAt),
        channel: PermissionGate = PermissionGate.GRANTED,
        fullScreenIntent: PermissionGate = PermissionGate.GRANTED,
    ) = NotificationAccessState(permission, channel, fullScreenIntent)

    @Test
    fun `a fully granted notifications step is resolved and has no issue`() {
        val granted = state()

        assertTrue(granted.isResolved)
        assertNull(granted.issue)
    }

    @Test
    fun `a revoked permission is the senior issue even when the channel is off`() {
        // ОВ-107: канал не называется при выключенных уведомлениях — он внутри разрешения.
        val revoked = state(
            permission = PermissionGrant.Denied(canAsk = true),
            channel = PermissionGate.DENIED,
        )

        assertEquals(ReminderWarning.NOTIFICATIONS_OFF, revoked.issue)
        assertFalse(revoked.isResolved)
    }

    @Test
    fun `a disabled channel is named when the permission is granted`() {
        assertEquals(ReminderWarning.NOTIFICATION_CHANNEL_OFF, state(channel = PermissionGate.DENIED).issue)
    }

    @Test
    fun `full screen intent is named only when everything else is in order`() {
        assertEquals(ReminderWarning.FULL_SCREEN_INTENT_OFF, state(fullScreenIntent = PermissionGate.DENIED).issue)
        assertEquals(
            ReminderWarning.NOTIFICATION_CHANNEL_OFF,
            state(channel = PermissionGate.DENIED, fullScreenIntent = PermissionGate.DENIED).issue,
        )
    }

    @Test
    fun `no combination of hints makes a vendor step verified by the system`() {
        val everything = ManualStepState(confirmedByUser = true, hints = ManualStepHint.entries.toSet())

        assertFalse(everything.verifiedBySystem, "REMINDER-20: the app never verifies EMUI settings")
        assertEquals(ManualStepHint.entries.size, everything.hints.size)
    }

    @Test
    fun `a vendor step is resolved by the person's word and only by it`() {
        assertFalse(ManualStepState(confirmedByUser = false).isResolved)
        assertTrue(ManualStepState(confirmedByUser = true).isResolved)
    }

    @Test
    fun `only permissions are read from the system`() {
        assertTrue(PermissionStep.NOTIFICATIONS.verifiedBySystem)
        assertTrue(PermissionStep.EXACT_ALARMS.verifiedBySystem)
        assertFalse(PermissionStep.APP_STARTUP.verifiedBySystem)
        assertFalse(PermissionStep.BATTERY_SAVING.verifiedBySystem)
        assertFalse(PermissionStep.RECENTS_PIN.verifiedBySystem)
    }

    @Test
    fun `the vendor state is not a permission grant and has no granted variant`() {
        // Главное утверждение дизайна: ложно-зелёный вендорский шаг невозможен по типу,
        // а не по дисциплине вызова (TDD §5.2).
        val vendor: StepState = ManualStepState(confirmedByUser = true)

        assertFalse(vendor is PermissionGrant, "the vendor step must not reuse the permission type")

        // Исчерпывающий when вместо рефлексии: третий вариант разрешения перестал бы
        // компилироваться здесь, а подставить сюда ManualStepState нельзя — у него другой тип.
        val variants = listOf(PermissionGrant.Granted(readAt), PermissionGrant.Denied(canAsk = true)).map { grant ->
            when (grant) {
                is PermissionGrant.Granted -> "Granted"
                is PermissionGrant.Denied -> "Denied"
            }
        }
        assertEquals(listOf("Granted", "Denied"), variants)
    }

    @Test
    fun `the step indicator belongs to onboarding and not to the screen`() {
        assertTrue(OnboardingSource.FIRST_LAUNCH.showsStepIndicator)
        assertFalse(OnboardingSource.SETTINGS.showsStepIndicator, "re-entry from settings shows no indicator")
        assertFalse(OnboardingSource.TODAY_WARNING.showsStepIndicator)
        assertFalse(OnboardingSource.OUTAGE_BANNER.showsStepIndicator)
    }

    @Test
    fun `only the banner entries focus the first unresolved step`() {
        // ОВ-107: фокус есть у предупреждения на «Сегодня» и у сообщения о перерыве;
        // первый запуск и настройки ведут на экран с начала.
        assertTrue(OnboardingSource.TODAY_WARNING.focusesFirstUnresolved)
        assertTrue(OnboardingSource.OUTAGE_BANNER.focusesFirstUnresolved)
        assertFalse(OnboardingSource.FIRST_LAUNCH.focusesFirstUnresolved)
        assertFalse(OnboardingSource.SETTINGS.focusesFirstUnresolved)
    }
}
