package ru.stohastika.sklyanka.reminder

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Подписка приёмника Direct Boot на события загрузки (SKL-87).
 *
 * Главная проверка — `directBootAware`: без него система не запустит приёмник до разблокировки,
 * и восстановление молча не состоится — тот же отказ, что задача устраняет. Подписка на оба
 * действия проверяется через `PackageManager`, как и у `SystemEventReceiverTest`. Ветвление
 * locked/unlocked покрывается реплеером (`LockedBootAlarmReplayerTest`) и переносом следов
 * (`DirectBootTraceDrain`); сам `onReceive` требует графа Hilt и проверяется на устройстве.
 */
@RunWith(AndroidJUnit4::class)
class DirectBootReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the receiver is subscribed to both direct boot actions and is direct boot aware`() {
        listOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
        ).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(action), 0)
                .filter { it.activityInfo?.name == DirectBootReceiver::class.java.name }

            assertTrue("DirectBootReceiver must be subscribed to $action", receivers.isNotEmpty())
            assertTrue(
                "DirectBootReceiver must be directBootAware for $action",
                receivers.all { it.activityInfo?.directBootAware == true },
            )
        }
    }
}
