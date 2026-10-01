package ru.stohastika.sklyanka.platform.notification

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderVibration

/**
 * Вибрация каналов напоминания (НАП-24, SKL-76).
 *
 * Android читает паттерн при создании канала и после этого приложению его не отдаёт
 * (ADR-0004, ПЛ-10), поэтому замороженные значения проверяются здесь — на собранной
 * конфигурации канала, а не на слух. Различимость ощущений остаётся ручным сценарием
 * (`@manual`, эталонное устройство).
 */
@RunWith(AndroidJUnit4::class)
class ReminderChannelsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = requireNotNull(
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
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
}
