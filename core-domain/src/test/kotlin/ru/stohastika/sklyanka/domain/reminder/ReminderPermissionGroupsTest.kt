package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission

@DisplayName("Permissions are grouped by consequence and marked by state")
class ReminderPermissionGroupsTest {

    @Test
    fun `only permissions that need action are shown`() {
        assertFalse(
            ReminderPermissionGroups.requiresAction(
                ReminderPermission.NOTIFICATIONS,
                PermissionState.GRANTED,
            ),
        )
        assertTrue(
            ReminderPermissionGroups.requiresAction(ReminderPermission.NOTIFICATIONS, PermissionState.DENIED),
        )
        assertTrue(
            ReminderPermissionGroups.requiresAction(ReminderPermission.AUTOSTART, PermissionState.UNKNOWN),
        )
    }

    @Test
    fun `the boot receiver is never a row there is nothing to fix`() {
        assertFalse(
            ReminderPermissionGroups.requiresAction(ReminderPermission.BOOT_RECEIVER, PermissionState.UNKNOWN),
        )
    }

    @Test
    fun `notifications channel exact alarms and autostart are fixed first`() {
        listOf(
            ReminderPermission.NOTIFICATIONS,
            ReminderPermission.NOTIFICATION_CHANNEL,
            ReminderPermission.EXACT_ALARMS,
            ReminderPermission.AUTOSTART,
        ).forEach { permission ->
            assertEquals(PermissionGroup.FIX_FIRST, ReminderPermissionGroups.groupOf(permission))
        }
    }

    @Test
    fun `full screen and do not disturb can wait`() {
        listOf(
            ReminderPermission.FULL_SCREEN_INTENT,
            ReminderPermission.DND_POLICY_ACCESS,
        ).forEach { permission ->
            assertEquals(PermissionGroup.CAN_DEFER, ReminderPermissionGroups.groupOf(permission))
        }
    }

    @Test
    fun `denied system permissions warn while manual checks and limitations stay neutral`() {
        assertEquals(
            PermissionMarker.WARNING,
            ReminderPermissionGroups.markerOf(ReminderPermission.NOTIFICATIONS, PermissionState.DENIED),
        )
        assertEquals(
            PermissionMarker.WARNING,
            ReminderPermissionGroups.markerOf(ReminderPermission.EXACT_ALARMS, PermissionState.DENIED),
        )
        assertEquals(
            PermissionMarker.NEUTRAL,
            ReminderPermissionGroups.markerOf(ReminderPermission.AUTOSTART, PermissionState.UNKNOWN),
        )
        assertEquals(
            PermissionMarker.NEUTRAL,
            ReminderPermissionGroups.markerOf(ReminderPermission.DND_POLICY_ACCESS, PermissionState.DENIED),
        )
    }

    @Test
    fun `a denied do not disturb access is shown as a manual check`() {
        // Ограничение, а не право: слово состояния — «проверьте вручную» (AC-10).
        assertEquals(
            PermissionState.UNKNOWN,
            ReminderPermissionGroups.stateShown(ReminderPermission.DND_POLICY_ACCESS, PermissionState.DENIED),
        )
        assertEquals(
            PermissionState.UNKNOWN,
            ReminderPermissionGroups.stateShown(ReminderPermission.DND_POLICY_ACCESS, PermissionState.UNKNOWN),
        )
    }

    @Test
    fun `a granted do not disturb access keeps its granted state`() {
        assertEquals(
            PermissionState.GRANTED,
            ReminderPermissionGroups.stateShown(ReminderPermission.DND_POLICY_ACCESS, PermissionState.GRANTED),
        )
    }

    @Test
    fun `other permissions name their state as is`() {
        assertEquals(
            PermissionState.DENIED,
            ReminderPermissionGroups.stateShown(ReminderPermission.EXACT_ALARMS, PermissionState.DENIED),
        )
        assertEquals(
            PermissionState.UNKNOWN,
            ReminderPermissionGroups.stateShown(ReminderPermission.AUTOSTART, PermissionState.UNKNOWN),
        )
    }
}
