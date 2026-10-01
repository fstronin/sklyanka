package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.delay
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.AppForeground
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Запуск самопроверки доставки человеком (ПМ-21, ОВ-108).
 *
 * Запас [DeliveryCheckRule.DELAY] даёт человеку время смахнуть приложение из недавних:
 * проверка при открытом приложении не состоится по построению. Запись о назначенной
 * проверке ложится в журнал до постановки будильника: след обязан остаться, даже если
 * система откажет в точном будильнике, — иначе поддержке неоткуда узнать о проверке.
 */
class StartDeliveryCheckUseCase(
    private val alarms: AlarmGateway,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): DeliveryCheckState {
        val now = time.now()
        val dueAt = now.plus(DeliveryCheckRule.DELAY)
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                reason = DeliveryCheckReason.SCHEDULED,
            ),
        )
        alarms.scheduleDeliveryCheck(dueAt)
        return DeliveryCheckState.Running(dueAt)
    }
}

/**
 * Обработка тестового срабатывания (SKL-86, ОВ-108).
 *
 * Три исхода, и ни один не молчит (НФТ-8):
 * - приложение открыто — `not_completed` и никакого показа: фоновый путь не проверялся;
 * - уведомление не предъявлено — `not_delivered`: вызов показа ещё не показ;
 * - предъявлено — `shown`, а «вовремя» или «с опозданием» решает отклонение от dueAt.
 *
 * Порог подавления SKL-88 здесь не применяется ни в каком виде: тестовое срабатывание
 * показывается и сильно опоздавшим (ОВ-109), иначе «не дошло» не отличить от «дошло
 * слишком поздно».
 */
class HandleFiredDeliveryCheckUseCase(
    private val journal: ReminderJournal,
    private val notifications: NotificationGateway,
    private val foreground: AppForeground,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): DeliveryCheckState {
        val last = journal.last(ReminderEvent.DELIVERY_CHECK)
        return when {
            last == null -> DeliveryCheckState.None
            // Срабатывание, о котором уже решено (отмена, показ, исход), ничего не меняет:
            // поздний будильник не спорит с названным исходом.
            last.reason != DeliveryCheckReason.SCHEDULED -> DeliveryCheckRule.stateOf(last)
            else -> {
                val dueAt = last.scheduledAt ?: time.now()
                if (foreground.isForeground()) notCompleted(dueAt) else deliver(dueAt)
            }
        }
    }

    private suspend fun notCompleted(dueAt: Instant): DeliveryCheckState {
        val now = time.now()
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                firedAt = now,
                delivered = false,
                reason = DeliveryCheckReason.NOT_COMPLETED,
            ),
        )
        notifications.cancelDeliveryCheck()
        return DeliveryCheckState.Finished(
            outcome = DeliveryCheckOutcome.NOT_COMPLETED,
            dueAt = dueAt,
            resolvedAt = now,
            lateMinutes = null,
        )
    }

    private suspend fun deliver(dueAt: Instant): DeliveryCheckState {
        val firedAt = time.now()
        val firedDeviation = Duration.between(dueAt, firedAt).seconds
        journal.record(
            ReminderLogEntry(
                occurredAt = firedAt,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                firedAt = firedAt,
                deviationSeconds = firedDeviation,
                delivered = false,
                reason = DeliveryCheckReason.FIRED,
            ),
        )
        notifications.showDeliveryCheck(dueAt.atZone(time.zone()).toLocalDateTime())
        val presentedAt = awaitPresentation()
        return if (presentedAt == null) {
            notDelivered(dueAt)
        } else {
            shown(dueAt, presentedAt)
        }
    }

    private suspend fun notDelivered(dueAt: Instant): DeliveryCheckState {
        val now = time.now()
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                delivered = false,
                reason = DeliveryCheckReason.NOT_DELIVERED,
            ),
        )
        return DeliveryCheckState.Finished(
            outcome = DeliveryCheckOutcome.NOT_DELIVERED,
            dueAt = dueAt,
            resolvedAt = now,
            lateMinutes = null,
        )
    }

    private suspend fun shown(dueAt: Instant, presentedAt: Instant): DeliveryCheckState {
        val deviation = Duration.between(dueAt, presentedAt).seconds
        journal.record(
            ReminderLogEntry(
                occurredAt = presentedAt,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                firedAt = presentedAt,
                deviationSeconds = deviation,
                delivered = true,
                reason = DeliveryCheckReason.SHOWN,
            ),
        )
        val outcome = DeliveryCheckRule.outcomeOf(deviation)
        return DeliveryCheckState.Finished(
            outcome = outcome,
            dueAt = dueAt,
            resolvedAt = presentedAt,
            lateMinutes = if (outcome == DeliveryCheckOutcome.LATE) DeliveryCheckRule.lateMinutes(deviation) else null,
        )
    }

    /**
     * Момент, в который тестовое уведомление оказалось в шторке, либо `null`.
     *
     * Тот же опрос, что у боевого показа (`HandleFiredReminderUseCase`): спрашивается
     * у системы несколько раз, а не у памяти процесса, потому что уведомление появляется
     * не в тот же миг, в который приложение о нём попросило.
     */
    private suspend fun awaitPresentation(): Instant? {
        repeat(PRESENTATION_ATTEMPTS) { attempt ->
            if (notifications.isDeliveryCheckShown()) return time.now()
            if (attempt < PRESENTATION_ATTEMPTS - 1) delay(PRESENTATION_POLL_MILLIS)
        }
        return null
    }

    private companion object {
        const val PRESENTATION_ATTEMPTS = 10
        const val PRESENTATION_POLL_MILLIS = 100L
    }
}

/**
 * Закрытие проверки при входе в приложение (НФТ-8, НФТ-52).
 *
 * Если срок тестового срабатывания прошёл, а срабатывания так и не было, исход называется
 * здесь: «не дошло» честнее молчания. Тестовый будильник при этом снимается — позднее
 * срабатывание не имеет права спорить с уже названным исходом.
 *
 * До срока состояние не трогается: проверка идёт, и вход в приложение — не повод её
 * закрывать. Отдельный сценарий, а не часть сборки снимка: сборка диагностики — чтение,
 * и менять журнал она не имеет права.
 */
class FinishDeliveryCheckOnEntryUseCase(
    private val journal: ReminderJournal,
    private val alarms: AlarmGateway,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): DeliveryCheckState {
        val last = journal.last(ReminderEvent.DELIVERY_CHECK)
        val state = last?.let(DeliveryCheckRule::stateOf) ?: DeliveryCheckState.None
        // До срока состояние не трогается: проверка идёт, и вход — не повод её закрывать.
        // После срока без срабатывания исход называется здесь, а будильник снимается,
        // чтобы позднее срабатывание не спорило с уже названным исходом.
        return if (state is DeliveryCheckState.Running && !time.now().isBefore(state.dueAt)) {
            closeExpired(state)
        } else {
            state
        }
    }

    private suspend fun closeExpired(state: DeliveryCheckState.Running): DeliveryCheckState.Finished {
        val now = time.now()
        alarms.cancelDeliveryCheck()
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = state.dueAt,
                delivered = false,
                reason = DeliveryCheckReason.NOT_DELIVERED,
            ),
        )
        return DeliveryCheckState.Finished(
            outcome = DeliveryCheckOutcome.NOT_DELIVERED,
            dueAt = state.dueAt,
            resolvedAt = now,
            lateMinutes = null,
        )
    }
}

/**
 * Отмена проверки человеком (ОВ-108).
 *
 * Только для незавершённой проверки: отменять завершённую нечего, и повторное нажатие
 * ничего не меняет. Отмена снимает тестовый будильник и остаётся в журнале записью
 * `cancelled` — след обязан быть отличим от «не дошло».
 */
class CancelDeliveryCheckUseCase(
    private val alarms: AlarmGateway,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke() {
        val last = journal.last(ReminderEvent.DELIVERY_CHECK) ?: return
        val state = DeliveryCheckRule.stateOf(last)
        if (state !is DeliveryCheckState.Running) return
        alarms.cancelDeliveryCheck()
        journal.record(
            ReminderLogEntry(
                occurredAt = time.now(),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = state.dueAt,
                reason = DeliveryCheckReason.CANCELLED,
            ),
        )
    }
}

/**
 * Подтверждение объявленного результата (ОВ-108).
 *
 * Отдельный сценарий, а не память держателя баннера: подтверждение обязано пережить
 * перезапуск, иначе провалившаяся проверка возвращалась бы баннером вечно.
 */
class AcknowledgeDeliveryCheckUseCase(private val acknowledgements: DeliveryCheckAcknowledgements) {

    suspend operator fun invoke(dueAt: Instant) {
        acknowledgements.remember(dueAt)
    }
}
