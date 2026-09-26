package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.NotificationGateway
import ru.heuri.sklyanka.domain.reminder.port.ReminderCardSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.reminder.port.WatchGateway

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
 */
class DismissReminderUseCase(
    private val plan: ReminderPlanSource,
    private val cards: ReminderCardSource,
    private val notifications: NotificationGateway,
    private val watch: WatchGateway,
) {

    suspend operator fun invoke(intakeId: String) {
        val intake = plan.intake(intakeId) ?: return
        // Парная карточка на часах отзывается всегда: отметили её на телефоне или
        // на часах, второй раз спрашивать не о чем (ЧАС-16).
        watch.revoke(intakeId)

        // Если карточки этого слота на экране нет, снимать и обновлять нечего: отметка
        // с экрана «Сегодня» идёт тем же путём, и показывать по ней уведомление о соседнем
        // приёме было бы уведомлением из ниоткуда.
        if (!notifications.isShown(intake.slot)) return

        val remaining = plan.pendingInSlot(intake.slot)
        if (remaining.isEmpty()) {
            notifications.cancel(intake.slot)
            return
        }
        val card = cards.card(intake.slot, mirroredToWatch = false)
        if (card == null || card.isEmpty) {
            notifications.cancel(intake.slot)
        } else {
            notifications.show(card, ReminderChannel.REGULAR)
        }
    }
}
