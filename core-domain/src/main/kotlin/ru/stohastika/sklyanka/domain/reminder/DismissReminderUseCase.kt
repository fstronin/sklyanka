package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.WatchGateway

/**
 * Снятие напоминания вслед за состоявшейся отметкой (НАП-30, ЧАС-16).
 *
 * Вызывается из единственной точки отметки (`MarkIntakeUseCase`) — и поэтому работает
 * одинаково для телефона, часов и общей карточки. Отдельной «отметки из напоминания»
 * в проекте нет: второй путь записи означал бы второй способ обойти проверку статуса
 * и двойное списание остатка в гонке (ЧАС-24).
 *
 * Отметка одной строки общей карточки её не закрывает (НАП-36): карточка показывается
 * заново без отмеченной строки, а снимается только когда в слоте не осталось ожидающихся
 * приёмов. Повторный показ того же слота не звучит второй раз — уведомление обновляется.
 *
 * Отметка снимает и отложенное срабатывание (SKL-35, ОВ-75): приём закрыт, повтору ждать
 * нечего. Будильник снимается **только вместе с записью** — обычный будильник будущего
 * приёма отметка не трогает, иначе отмена отметки (SKL-30) оставила бы приём ожидающимся
 * без напоминания.
 */
class DismissReminderUseCase(
    private val plan: ReminderPlanSource,
    private val cards: ReminderCardSource,
    private val notifications: NotificationGateway,
    private val watch: WatchGateway,
    private val deferred: DeferredReminders,
    private val alarms: AlarmGateway,
) {

    suspend operator fun invoke(intakeId: String) {
        val intake = plan.intake(intakeId) ?: return
        // Парная карточка на часах отзывается всегда: отметили её на телефоне или
        // на часах, второй раз спрашивать не о чем (ЧАС-16).
        watch.revoke(intakeId)

        // Запись отвечает, было ли откладывание: у обычного будильника будущего приёма
        // её нет, и снимать его нельзя — отмена отметки вернула бы приём без напоминания.
        if (deferred.remove(intakeId)) alarms.cancel(intakeId)

        // Если карточки этого слота на экране нет, снимать и обновлять нечего: отметка
        // с экрана «Сегодня» идёт тем же путём, и показывать по ней уведомление о соседнем
        // приёме было бы уведомлением из ниоткуда.
        if (notifications.isShown(intake.slot)) refresh(intake.slot)
    }

    /**
     * Показать карточку слота заново без отмеченной строки либо снять её совсем.
     *
     * Отметка одной строки общей карточки её не закрывает (НАП-36); повторный показ того же
     * слота не звучит второй раз — уведомление обновляется.
     */
    private suspend fun refresh(slot: ReminderSlot) {
        val card = cards.card(slot, mirroredToWatch = false)
        if (card == null || card.isEmpty) {
            notifications.cancel(slot)
        } else {
            notifications.show(card, ReminderChannel.REGULAR)
        }
    }
}
