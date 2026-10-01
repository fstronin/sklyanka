package ru.stohastika.sklyanka.platform.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckActivity
import ru.stohastika.sklyanka.feature.reminder.ReminderActivity
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.time.LocalDateTime
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
            // Повторный показ того же слота обновляет карточку, а не добавляет вторую
            // (НАП-36). Обновление живой карточки молчит, но настойчивый повтор звучит
            // и вибрирует даже поверх неё: это новое обращение к человеку (НАП-25).
            // Флаг действует на обновление — не выставлять его повтору обязательно.
            .setOnlyAlertOnce(onlyAlertOnce(channel))
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
     * Карточка тестового напоминания обычным каналом (SKL-86, ОВ-108).
     *
     * Тем же каналом, что первое боевое напоминание: проверка обязана мерить настоящий
     * путь доставки, а не отдельный «тихий» канал, который система не тронет. Полноэкранное
     * намерение ведёт в [DeliveryCheckActivity]; дополнений приёма у уведомления нет,
     * поэтому [retainOnly] его не трогает — снятие у него отдельный вызов.
     */
    override suspend fun showDeliveryCheck(at: LocalDateTime) {
        channels.ensure()
        val notification = NotificationCompat.Builder(context, ReminderChannel.REGULAR.id)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(context.getString(CHECK_OVERLINE))
            .setContentText(context.getString(CHECK_TEXT))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setFullScreenIntent(fullScreenCheck(at), true)
            .setContentIntent(fullScreenCheck(at))
            .build()
        manager.notify(DELIVERY_CHECK_ID, notification)
    }

    /**
     * Показана ли сейчас карточка проверки.
     *
     * Ответ — у системы, а не из памяти процесса: процесс на EMUI выгружается,
     * а уведомление в шторке остаётся (тот же вопрос, что у боевого показа).
     */
    override suspend fun isDeliveryCheckShown(): Boolean = manager.activeNotifications.any {
        it.id == DELIVERY_CHECK_ID
    }

    override suspend fun cancelDeliveryCheck() {
        manager.cancel(DELIVERY_CHECK_ID)
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

    override fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun fullScreen(slot: ReminderSlot): PendingIntent = PendingIntent.getActivity(
        context,
        idOf(slot),
        ReminderActivity.intentFor(context, slot.profileId, slot.scheduledAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Полноэкранное намерение карточки проверки: своя Activity, слоту приёма взяться неоткуда. */
    private fun fullScreenCheck(at: LocalDateTime): PendingIntent = PendingIntent.getActivity(
        context,
        DELIVERY_CHECK_ID,
        DeliveryCheckActivity.intentFor(context, at),
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
        const val EXTRA_INTAKE_IDS = "ru.stohastika.sklyanka.reminder.intake_ids"

        /**
         * Идентификатор уведомления проверки — фиксированный.
         *
         * Он не выводится из слота: у проверки нет ни профиля, ни приёма, а второй
         * тестовый показ обязан обновить первый, а не добавить вторую карточку.
         */
        const val DELIVERY_CHECK_ID = 7_186_004

        /** Тексты карточки живут в модуле экрана проверки (SKL-86, НФТ-42). */
        val CHECK_OVERLINE = ru.stohastika.sklyanka.feature.reminder.R.string.reminder_check_overline
        val CHECK_TEXT = ru.stohastika.sklyanka.feature.reminder.R.string.reminder_check_text
    }
}
