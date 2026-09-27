package ru.heuri.sklyanka.platform.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.heuri.sklyanka.R
import ru.heuri.sklyanka.domain.reminder.ReminderCard
import ru.heuri.sklyanka.domain.reminder.ReminderChannel
import ru.heuri.sklyanka.domain.reminder.ReminderSlot
import ru.heuri.sklyanka.domain.reminder.port.NotificationGateway
import ru.heuri.sklyanka.feature.reminder.ReminderActivity
import ru.heuri.sklyanka.ui.locale.InterfaceLocale
import ru.heuri.sklyanka.ui.locale.withInterfaceLocale
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показ напоминания уведомлением с полноэкранным намерением (НАП-29).
 *
 * Одно уведомление на слот (профиль плюс назначенное время), а не на приём: два приёма
 * на 14:00 дают одну карточку со списком, потому что три полноэкранных уведомления подряд
 * человек закрывает не глядя (НАП-36). Идентификатор уведомления выводится из слота, поэтому
 * второй сработавший будильник того же времени обновляет уже показанное уведомление,
 * а не добавляет второе.
 *
 * Идентификаторы приёмов кладутся в дополнения уведомления. Это единственный способ
 * ответить на вопрос «какие приёмы сейчас показаны», не храня состояние в процессе, который
 * EMUI выгружает: список показанных уведомлений система отдаёт вместе с их дополнениями.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val channels: ReminderChannels,
) : NotificationGateway {

    /**
     * Контекст с закреплённым языком интерфейса (НФТ-43а, `ADR-0008`).
     *
     * Уведомление собирается вне экрана, из контекста приложения, и язык этого контекста —
     * язык телефона. Закрепляется он здесь, а не в `Application`: обёртка базового
     * контекста приложения ломает разрешение ресурсов под имитацией Android, то есть
     * уносит с собой весь экранный прогон. Точка, из которой строится текст, — эта.
     */
    private val context: Context = applicationContext.withInterfaceLocale()

    private val manager: NotificationManager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))

    override suspend fun show(card: ReminderCard, channel: ReminderChannel) {
        channels.ensure()
        val time = card.scheduledAt.format(
            // Локаль интерфейса, а не локаль процесса: уведомление строится из контекста
            // приложения, и на телефоне с чужим языком время в подписи выглядело бы
            // иначе, чем на экране (НФТ-43а).
            DateTimeFormatter.ofPattern(context.getString(R.string.reminder_time_pattern), InterfaceLocale.VALUE),
        )
        val names = card.items.joinToString(context.getString(R.string.reminder_notification_separator)) {
            it.medicationName
        }
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(names)
            .setContentText(
                context.getString(
                    R.string.reminder_notification_text,
                    time,
                    context.getString(R.string.reminder_overline),
                ),
            )
            // Категория будильника: от неё зависит и вид карточки на экране блокировки,
            // и поведение в режиме «Не беспокоить» (НАП-24).
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Уведомление остаётся в шторке, пока приём не отмечен (НАП-33), и снимается
            // отметкой, откладыванием или истечением окна — но не смахиванием.
            .setAutoCancel(false)
            .setOngoing(false)
            // Повторный показ того же слота обновляет карточку и не звучит второй раз:
            // звучит повтор, который приходит отдельным будильником (НАП-25).
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(fullScreen(card.slot), true)
            .setContentIntent(fullScreen(card.slot))
            .addExtras(
                android.os.Bundle().apply {
                    putStringArray(EXTRA_INTAKE_IDS, card.items.map { it.intakeId }.toTypedArray())
                },
            )
            .build()
        manager.notify(idOf(card.slot), notification)
    }

    override suspend fun cancel(slot: ReminderSlot) {
        manager.cancel(idOf(slot))
    }

    /**
     * Показана ли карточка слота.
     *
     * Ответ берётся у системы, а не из памяти процесса: процесс на EMUI выгружается,
     * а уведомление в шторке остаётся.
     */
    override suspend fun isShown(slot: ReminderSlot): Boolean = manager.activeNotifications.any {
        it.id == idOf(slot)
    }

    /**
     * Снять карточки, приёмы которых больше не ожидаются (НАП-34).
     *
     * Новых уведомлений не порождает ни одного: телефон, включённый после суток простоя,
     * не должен выдавать очередь карточек о том, что всё пропущено (испытание И-10).
     */
    override suspend fun retainOnly(pendingIntakeIds: Set<String>) {
        manager.activeNotifications
            .filter { it.notification?.extras?.containsKey(EXTRA_INTAKE_IDS) == true }
            .forEach { shown ->
                val ids = shown.notification.extras.getStringArray(EXTRA_INTAKE_IDS).orEmpty()
                if (ids.none { it in pendingIntakeIds }) manager.cancel(shown.id)
            }
    }

    /**
     * Пробное напоминание (НАП-22).
     *
     * Тот же канал, то же полноэкранное намерение, тот же звук — иначе проверка
     * не доказывала бы работоспособность боевого пути. Приёма за ним не стоит,
     * и его дополнения пусты: снятие по списку ожидающихся приёмов его не тронет.
     */
    override suspend fun showTest() {
        channels.ensure()
        val intent = ReminderActivity.testIntent(context)
        val pending = PendingIntent.getActivity(
            context,
            TEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderChannel.REGULAR.id)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(context.getString(R.string.reminder_test_title))
            .setContentText(context.getString(R.string.reminder_test_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .build()
        manager.notify(TEST_NOTIFICATION_ID, notification)
    }

    override fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun fullScreen(slot: ReminderSlot): PendingIntent = PendingIntent.getActivity(
        context,
        idOf(slot),
        ReminderActivity.intentFor(context, slot.profileId, slot.scheduledAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Идентификатор уведомления из слота.
     *
     * Устойчив между запусками процесса: он вычисляется из данных слота, а не выдаётся
     * счётчиком, — иначе после выгрузки приложения снять показанную карточку было бы нечем.
     */
    private fun idOf(slot: ReminderSlot): Int = (slot.profileId + AT + slot.scheduledAt).hashCode()

    private companion object {
        const val AT = "@"
        const val EXTRA_INTAKE_IDS = "ru.heuri.sklyanka.reminder.intake_ids"
        const val TEST_NOTIFICATION_ID = 1
        const val TEST_REQUEST_CODE = 1
    }
}
