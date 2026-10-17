package ru.stohastika.sklyanka.platform.notification

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderCardItem
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.math.BigDecimal
import java.time.LocalDateTime
import ru.stohastika.sklyanka.feature.reminder.R as ReminderR

/**
 * Уведомление напоминания несёт три встроенных действия из шторки (RT-4, Р-87 п. 3).
 *
 * Проверяется построитель уведомления, а не реакция на нажатие: действие шторки обязано
 * быть видным прямо в уведомлении, иначе отметка требует открыть карточку (два касания).
 * Заголовки действий — из ресурсов языка интерфейса, тем же закреплением языка, что и тело
 * уведомления (НФТ-43а), поэтому сверяются с теми же строками в локали интерфейса.
 */
@RunWith(AndroidJUnit4::class)
class ReminderNotifierActionsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theReminderNotificationCarriesThreeShadeActions(): Unit = runBlocking {
        val notifier = ReminderNotifier(application, ReminderChannels(application))
        val at = LocalDateTime.of(2026, 10, 17, 8, 0)
        notifier.show(card(at), ReminderChannel.REGULAR)

        val manager = requireNotNull(application.getSystemService(NotificationManager::class.java))
        val posted = manager.activeNotifications.singleOrNull { it.notification != null }
        assertNotNull("the reminder notification must be posted", posted)
        val actions = posted!!.notification.actions
        assertNotNull("the notification must carry actions", actions)
        assertEquals("exactly three shade actions", 3, actions!!.size)

        // Язык интерфейса закреплён в построителе уведомления, поэтому ожидаемые заголовки
        // читаются в той же локали, а не в локали процесса проверки.
        val localized = application.withInterfaceLocale()
        val titles = actions.map { it.title.toString() }
        val taken = localized.getString(ReminderR.string.reminder_row_taken)
        val skip = localized.getString(ReminderR.string.reminder_row_skip)
        val snooze = localized.getString(ReminderR.string.reminder_action_snooze)
        assertTrue("«$taken» among $titles", titles.contains(taken))
        assertTrue("«$skip» among $titles", titles.contains(skip))
        assertTrue("«$snooze» among $titles", titles.contains(snooze))
        // Каждое действие несёт намерение: отметка из шторки работает без открытия карточки.
        actions.forEach { action ->
            assertNotNull("action «${action.title}» must carry a PendingIntent", action.actionIntent)
        }
    }

    private fun card(at: LocalDateTime): ReminderCard = ReminderCard(
        slot = ReminderSlot(profileId = "profile-1", scheduledAt = at),
        scheduledAt = at,
        items = listOf(
            ReminderCardItem(
                intakeId = "intake-1",
                medicationId = "medication-1",
                medicationName = "Витамин",
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal.ONE,
                doseUnit = "таблетка",
                strength = null,
                strengthUnit = null,
            ),
        ),
        profileIsSelf = true,
        profileName = "Я",
    )
}
