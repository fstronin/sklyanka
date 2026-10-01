package ru.stohastika.sklyanka.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason

/**
 * Системные события → пересборка плана (НАП-11, НАП-16).
 *
 * Проверяется и соответствие действий причинам, и подписка манифеста: незаявленное
 * действие неотличимо от «событие не пришло», и на устройстве это выглядит одинаково —
 * будильники остаются невосстановленными.
 */
@RunWith(AndroidJUnit4::class)
class SystemEventReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `granting the exact alarm right rebuilds the plan`() {
        assertEquals(
            RescheduleReason.EXACT_ALARM_GRANTED,
            SystemEvents.reasonOf(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
        )
    }

    @Test
    fun `the known system events keep their reasons`() {
        assertEquals(RescheduleReason.BOOT, SystemEvents.reasonOf(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(RescheduleReason.BOOT, SystemEvents.reasonOf(SystemEvents.ACTION_QUICKBOOT_POWERON))
        assertEquals(RescheduleReason.PACKAGE_REPLACED, SystemEvents.reasonOf(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(RescheduleReason.TIMEZONE_CHANGED, SystemEvents.reasonOf(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(RescheduleReason.TIME_CHANGED, SystemEvents.reasonOf(Intent.ACTION_TIME_CHANGED))
    }

    @Test
    fun `an unknown action asks for nothing`() {
        assertNull(SystemEvents.reasonOf("android.intent.action.SOMETHING_ELSE"))
        assertNull(SystemEvents.reasonOf(null))
    }

    /** Манифест обязан подписаться на событие: неподписанный приёмник его не увидит вовсе. */
    @Test
    fun `the receiver is subscribed to the exact alarm grant action`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
            0,
        )

        assertTrue(
            "SystemEventReceiver must be subscribed to the grant action",
            receivers.any { it.activityInfo?.name == SystemEventReceiver::class.java.name },
        )
    }
}
