package ru.stohastika.sklyanka.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("The chain is five steps on EMUI and two elsewhere, and it never calls a vendor step verified")
class PermissionChecklistTest {

    private val readAt: Instant = Instant.parse("2026-09-25T10:00:00Z")

    private val grantedNotifications = NotificationAccessState(
        permission = PermissionGrant.Granted(readAt),
        channel = PermissionGate.GRANTED,
        fullScreenIntent = PermissionGate.GRANTED,
    )

    private fun checklist(
        vendor: VendorProfile = VendorProfile.EMUI,
        notifications: NotificationAccessState = grantedNotifications,
        exactAlarms: PermissionGrant = PermissionGrant.Granted(readAt),
        appStartup: ManualStepState = ManualStepState(confirmedByUser = false),
        batterySaving: ManualStepState = ManualStepState(confirmedByUser = false),
        recentsPin: ManualStepState = ManualStepState(confirmedByUser = false),
    ) = PermissionChecklist(vendor, notifications, exactAlarms, appStartup, batterySaving, recentsPin)

    @Test
    fun `on EMUI the five steps come in the fixed order of OV-106`() {
        assertEquals(
            listOf(
                PermissionStep.NOTIFICATIONS,
                PermissionStep.EXACT_ALARMS,
                PermissionStep.APP_STARTUP,
                PermissionStep.BATTERY_SAVING,
                PermissionStep.RECENTS_PIN,
            ),
            checklist().steps.map { it.first },
        )
    }

    @Test
    fun `on any other phone only the two common steps are shown`() {
        val chain = checklist(vendor = VendorProfile.GENERIC)

        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS),
            chain.steps.map { it.first },
        )
        assertEquals(2, chain.steps.size, "there are no vendor settings to show on a non-Huawei phone")
    }

    @Test
    fun `the chain is not complete while a vendor step is unconfirmed`() {
        // Оба разрешения Android выданы, но цепочка из пяти шагов пройдена на двух.
        val chain = checklist()

        assertFalse(chain.allResolved, "two of five steps is not a passed chain")
        assertEquals(3, chain.unresolvedCount)
        assertEquals(
            listOf(PermissionStep.APP_STARTUP, PermissionStep.BATTERY_SAVING, PermissionStep.RECENTS_PIN),
            chain.unresolved,
        )
    }

    @Test
    fun `the chain is complete only when all vendor steps are confirmed too`() {
        val chain = checklist(
            appStartup = ManualStepState(confirmedByUser = true),
            batterySaving = ManualStepState(confirmedByUser = true),
            recentsPin = ManualStepState(confirmedByUser = true),
        )

        assertTrue(chain.allResolved)
        assertEquals(0, chain.unresolvedCount)
        assertNull(chain.firstUnresolved)
        assertFalse(chain.remindersUnreliable)
    }

    @Test
    fun `on a non-Huawei phone the chain is complete with the two permissions alone`() {
        val chain = checklist(vendor = VendorProfile.GENERIC)

        // Вендорских шагов на экране нет — незакрытыми они цепочку не держат.
        assertTrue(chain.allResolved)
        assertEquals(0, chain.unresolvedCount)
    }

    @Test
    fun `the first unresolved step is the focus of the banner entries`() {
        val chain = checklist(
            exactAlarms = PermissionGrant.Denied(canAsk = false),
            appStartup = ManualStepState(confirmedByUser = true),
        )

        // Порядок фиксирован: первым незакрытым остаётся «Энергосбережение», хотя
        // «Закрепление» тоже не подтверждено — оно позже.
        assertEquals(PermissionStep.EXACT_ALARMS, chain.firstUnresolved)
        assertEquals(
            listOf(PermissionStep.EXACT_ALARMS, PermissionStep.BATTERY_SAVING, PermissionStep.RECENTS_PIN),
            chain.unresolved,
        )
    }

    @Test
    fun `one missing permission is counted as one and not as none`() {
        val chain = checklist(
            exactAlarms = PermissionGrant.Denied(canAsk = false),
            appStartup = ManualStepState(confirmedByUser = true),
            batterySaving = ManualStepState(confirmedByUser = true),
            recentsPin = ManualStepState(confirmedByUser = true),
        )

        assertEquals(1, chain.unresolvedCount)
        assertEquals(listOf(PermissionStep.EXACT_ALARMS), chain.unresolved)
    }

    @Test
    fun `a granted permission carries the moment it was read from the system`() {
        // «Выдано» — утверждение о прошлом длиной в один показ экрана: разрешение отзывают
        // в системных настройках, и момент чтения показывает, насколько свежи данные.
        val state = checklist().notifications.permission

        assertEquals(PermissionGrant.Granted(readAt), state)
    }

    @Test
    fun `an unresolved chain is what raises the unreliable-reminders warning`() {
        assertTrue(
            checklist(
                notifications = grantedNotifications.copy(permission = PermissionGrant.Denied(canAsk = true)),
            ).remindersUnreliable,
        )
        assertTrue(checklist().remindersUnreliable)
    }

    @Test
    fun `the chain has five steps and Do Not Disturb is not one of them`() {
        // НАП-17: доступ к политике «Не беспокоить» запрашивается отдельно, на экране
        // настроек напоминаний, и в цепочку онбординга не входит.
        assertEquals(5, PermissionStep.entries.size)
        assertFalse(PermissionStep.entries.any { it.name.contains("DND") })
        assertFalse(PermissionStep.entries.any { it.name.contains("POLICY") })
    }

    @Test
    fun `the permissions screen is the second of three onboarding steps as the mock-up shows it`() {
        // ОВ-51: первый шаг в макетах не нарисован, и приложение его не придумывает.
        assertEquals(3, OnboardingFlow.TOTAL_STEPS)
        assertEquals(2, OnboardingFlow.PERMISSIONS_STEP)
    }

    @Test
    fun `the state of one named step is readable without walking the list`() {
        val chain = checklist(
            notifications = grantedNotifications.copy(permission = PermissionGrant.Denied(canAsk = true)),
        )

        assertEquals(
            PermissionGrant.Denied(canAsk = true),
            (chain.state(PermissionStep.NOTIFICATIONS) as NotificationAccessState).permission,
        )
        assertEquals(PermissionGrant.Granted(readAt), chain.state(PermissionStep.EXACT_ALARMS))
        assertEquals(ManualStepState(confirmedByUser = false), chain.state(PermissionStep.APP_STARTUP))
        assertEquals(ManualStepState(confirmedByUser = false), chain.state(PermissionStep.BATTERY_SAVING))
        assertEquals(ManualStepState(confirmedByUser = false), chain.state(PermissionStep.RECENTS_PIN))
    }

    @Test
    fun `the checklist has no fixed step count constant any more`() {
        // Число шагов зависит от вендора; экран берёт его из steps, а не из константы.
        val emui = checklist().steps.size
        val generic = checklist(vendor = VendorProfile.GENERIC).steps.size

        assertEquals(5, emui)
        assertEquals(2, generic)
    }
}
