package ru.stohastika.sklyanka.platform.notification

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderVibration

/**
 * Параметры каналов напоминания: вибрация и звук (НАП-24, SKL-76, SKL-168).
 *
 * Android читает паттерн и звук при создании канала и после этого приложению их не отдаёт
 * (ADR-0004, ПЛ-10), поэтому замороженные значения проверяются здесь — на собранной
 * конфигурации канала, а не на слух. Узнаваемость сигнала и его громкость остаются ручным
 * сценарием (`@manual`, эталонное устройство).
 */
@RunWith(AndroidJUnit4::class)
class ReminderChannelsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = requireNotNull(
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
    )

    /** Свой сигнал по имени ресурса, а не по номеру: канал хранит строку (Р-99). */
    private val ownSignal = Uri.parse(
        "android.resource://${application.packageName}" +
            "/raw/${application.resources.getResourceEntryName(R.raw.reminder_chime)}",
    )

    @Test
    fun `both channels exist and vibrate with the frozen patterns`() {
        ReminderChannels(application).ensure()

        ReminderChannel.entries.forEach { channel ->
            val created = requireNotNull(manager.getNotificationChannel(channel.id)) {
                "channel ${channel.id} must exist"
            }

            assertTrue("channel ${channel.id} must vibrate", created.shouldVibrate())
            assertArrayEquals(
                "channel ${channel.id} must carry the frozen pattern",
                ReminderVibration.of(channel),
                created.vibrationPattern,
            )
        }
    }

    @Test
    fun `a repeated ensure keeps the vibration pattern`() {
        // `createNotificationChannel` для существующего канала вибрацию не трогает (ПЛ-10):
        // повторный старт приложения не имеет права перетереть замороженное значение.
        val channels = ReminderChannels(application)
        channels.ensure()
        channels.ensure()

        ReminderChannel.entries.forEach { channel ->
            val created = requireNotNull(manager.getNotificationChannel(channel.id))
            assertArrayEquals(ReminderVibration.of(channel), created.vibrationPattern)
        }
    }

    @Test
    fun `the regular channel plays the product's own signal at alarm usage`() {
        ReminderChannels(application).ensure()

        val created = requireNotNull(manager.getNotificationChannel(ReminderChannel.REGULAR.id))

        assertEquals("the product's own chime", ownSignal, created.sound)
        // Системный сигнал уведомления — ровно то, от чего уходим: его играет любое приложение.
        assertNotEquals(Settings.System.DEFAULT_NOTIFICATION_URI, created.sound)
        assertEquals(AudioAttributes.USAGE_ALARM, created.audioAttributes?.usage)
    }

    @Test
    fun `the own signal is a readable asset`() {
        // Звук живёт в APK: система перечитывает его при каждом срабатывании, поэтому файл
        // обязан открываться по схеме `android.resource`, а не только существовать в ресурсах.
        val descriptor = application.resources.openRawResourceFd(R.raw.reminder_chime)

        assertNotNull(descriptor)
        assertTrue("the chime must not be empty", descriptor.length > 0)
        descriptor.close()
    }

    @Test
    fun `the persistent channel keeps the system alarm signal`() {
        ReminderChannels(application).ensure()

        val created = requireNotNull(manager.getNotificationChannel(ReminderChannel.PERSISTENT.id))

        assertEquals(Settings.System.DEFAULT_ALARM_ALERT_URI, created.sound)
        assertNotEquals(ownSignal, created.sound)
    }

    @Test
    fun `the retired regular channel is removed`() {
        manager.createNotificationChannel(legacyRegular())

        ReminderChannels(application).ensure()

        assertNull(manager.getNotificationChannel(ReminderChannel.RETIRED_REGULAR_ID))
    }

    @Test
    fun `the retired regular channel survives while its reminder is in the shade`() {
        // Удаление канала снимает и уведомления, а показанное напоминание — обещание (НАП-1):
        // уборка ждёт следующего запуска, когда карточку уже отметили или закрыли.
        manager.createNotificationChannel(legacyRegular())
        manager.notify(LEGACY_NOTIFICATION_ID, legacyNotification())

        ReminderChannels(application).ensure()

        assertNotNull(manager.getNotificationChannel(ReminderChannel.RETIRED_REGULAR_ID))
        assertTrue(manager.activeNotifications.any { it.id == LEGACY_NOTIFICATION_ID })
    }

    private fun legacyRegular() = NotificationChannel(
        ReminderChannel.RETIRED_REGULAR_ID,
        "legacy",
        NotificationManager.IMPORTANCE_HIGH,
    )

    private fun legacyNotification() = NotificationCompat.Builder(application, ReminderChannel.RETIRED_REGULAR_ID)
        .setSmallIcon(R.drawable.ic_notification_mark)
        .setContentTitle("legacy")
        .build()

    private companion object {
        const val LEGACY_NOTIFICATION_ID = 4242
    }
}
