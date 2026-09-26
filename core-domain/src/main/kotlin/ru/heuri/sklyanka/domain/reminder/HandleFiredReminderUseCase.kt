package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.intake.IntakeWindow
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.reminder.port.AlarmGateway
import ru.heuri.sklyanka.domain.reminder.port.NotificationGateway
import ru.heuri.sklyanka.domain.reminder.port.ReminderCardSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettings
import ru.heuri.sklyanka.domain.reminder.port.WatchGateway
import ru.heuri.sklyanka.domain.reminder.port.WatchSendResult
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/** Почему показ не состоялся. Код уходит в журнал служебной строкой (НФТ-42). */
enum class SuppressReason(val code: String) {
    /** Приёма нет: запись снята перепланированием или переносом данных. */
    NOT_FOUND("not_found"),

    /** Приём уже отмечен — сюда попадает отметка с часов, опередившая показ (НАП-32). */
    ALREADY_MARKED("already_marked"),

    /** Окно приёма истекло: показывать нечего, приём становится пропущенным (НАП-33). */
    WINDOW_EXPIRED("window_expired"),

    /** Разрешение на уведомления отозвано — самый опасный отказ, тихий (НАП-15). */
    NOTIFICATIONS_DISABLED("notifications_disabled"),

    /** Нечего показать: все приёмы слота отмечены. */
    EMPTY_SLOT("empty_slot"),
}

/** Чем закончилось срабатывание будильника. */
sealed interface FiredOutcome {

    data class Shown(
        val card: ReminderCard,
        val channel: ReminderChannel,
        val watch: WatchSendResult,
        val repeat: RepeatDecision,
    ) : FiredOutcome

    data class Suppressed(val reason: SuppressReason) : FiredOutcome
}

/**
 * Обработка сработавшего будильника (`TDD.md §9.1`).
 *
 * Живёт в домене целиком, кроме показа: приёмник в `app` только передаёт сюда
 * идентификатор приёма и номер показа. Причина — проверяемость: последовательность
 * «проверить статус → проверить окно → собрать карточку → показать → записать в журнал →
 * поставить повтор» содержит все решения, ошибка в любом из которых стоит пропущенного
 * приёма, и она обязана проверяться на JVM, а не на устройстве, которого у сборки нет.
 *
 * Момент **предъявления** уведомления — это и есть «напоминание сработало» (НАП-2);
 * отклонение считается от планового времени приёма и уходит в журнал (НАП-4).
 */
// Портов много, и свести их в один «контекст доставки» нельзя: каждый прячет свою часть
// платформы, и объединяющий тип пришлось бы подменять целиком в каждом тесте.
@Suppress("LongParameterList")
class HandleFiredReminderUseCase(
    private val plan: ReminderPlanSource,
    private val cards: ReminderCardSource,
    private val notifications: NotificationGateway,
    private val watch: WatchGateway,
    private val alarms: AlarmGateway,
    private val journal: ReminderJournal,
    private val settings: ReminderSettings,
    private val window: IntakeWindow,
    private val time: TimeSource,
) {

    suspend operator fun invoke(intakeId: String, attempt: Int = 0): FiredOutcome {
        val now = time.now()
        val intake = plan.intake(intakeId)
        return if (intake == null) {
            suppress(now, SuppressReason.NOT_FOUND, intakeId = intakeId)
        } else {
            deliver(intake, attempt, now)
        }
    }

    /** Проверки, после которых показывать либо можно, либо уже незачем. */
    private suspend fun deliver(intake: ReminderIntake, attempt: Int, now: Instant): FiredOutcome {
        val scheduledAt = intake.scheduledAt.atZone(time.zone()).toInstant()
        val deviation = Duration.between(scheduledAt, now).seconds
        val blocked = blockedBy(intake)
        return if (blocked == null) {
            present(intake, attempt, now, scheduledAt, deviation)
        } else {
            // Отметка опередила показ: заодно снимаем карточку, которая могла остаться
            // от предыдущего показа этого же слота (НАП-32).
            if (blocked == SuppressReason.ALREADY_MARKED) notifications.cancel(intake.slot)
            suppress(now, blocked, intake, scheduledAt, deviation)
        }
    }

    /**
     * Почему показывать не нужно.
     *
     * Окно приёма истекло: перевод в пропущенные делает проход по просроченным (НАП-34),
     * здесь — только отказ от показа. Иначе телефон, включённый через сутки, напомнил бы
     * о вчерашнем приёме задним числом (НФТ-35).
     */
    private fun blockedBy(intake: ReminderIntake): SuppressReason? = when {
        intake.status != IntakeStatus.PENDING -> SuppressReason.ALREADY_MARKED
        !window.deadlineFor(intake.scheduledAt).isAfter(time.nowLocal()) -> SuppressReason.WINDOW_EXPIRED
        !notifications.areNotificationsEnabled() -> SuppressReason.NOTIFICATIONS_DISABLED
        else -> null
    }

    private suspend fun present(
        intake: ReminderIntake,
        attempt: Int,
        now: Instant,
        scheduledAt: Instant,
        deviation: Long,
    ): FiredOutcome {
        val slotCard = cards.card(intake.slot, mirroredToWatch = false)
        return if (slotCard == null || slotCard.isEmpty) {
            suppress(now, SuppressReason.EMPTY_SLOT, intake, scheduledAt, deviation)
        } else {
            show(intake, slotCard, attempt, now, scheduledAt, deviation)
        }
    }

    @Suppress("LongParameterList")
    private suspend fun show(
        intake: ReminderIntake,
        slotCard: ReminderCard,
        attempt: Int,
        now: Instant,
        scheduledAt: Instant,
        deviation: Long,
    ): FiredOutcome {
        // Часы получают карточку одновременно с телефоном (ЧАС-13). Недоступность часов —
        // не ошибка сценария: чип «Продублировано на часы» просто не появляется (ЧАС-14).
        val watchResult = watch.send(slotCard)
        val card = slotCard.copy(mirroredToWatch = watchResult == WatchSendResult.SENT)
        val channel = PersistentRepeatPolicy.channelFor(attempt)

        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.FIRED,
                intakeId = intake.intakeId,
                medicationId = intake.medicationId,
                profileId = intake.profileId,
                scheduledAt = scheduledAt,
                firedAt = now,
                deviationSeconds = deviation,
                attempt = attempt,
            ),
        )
        notifications.show(card, channel)
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                // Повтор отличается от первого показа видом записи: по нему в диагностике
                // видно, что настойчивое напоминание действительно сработало (НАП-25).
                event = if (attempt == 0) ReminderEvent.SHOWN else ReminderEvent.REPEATED,
                intakeId = intake.intakeId,
                medicationId = intake.medicationId,
                profileId = intake.profileId,
                scheduledAt = scheduledAt,
                firedAt = now,
                deviationSeconds = deviation,
                channel = channel,
                delivered = true,
                attempt = attempt,
                watchResult = watchResult.code,
            ),
        )
        return FiredOutcome.Shown(card, channel, watchResult, planRepeat(intake, attempt, now))
    }

    /** Повтор настойчивого напоминания, если он разрешён правилом (НАП-25, НАП-26). */
    private suspend fun planRepeat(intake: ReminderIntake, attempt: Int, now: Instant): RepeatDecision {
        val values = settings.read()
        val repeat = PersistentRepeatPolicy.decide(
            now = now,
            state = RepeatState(
                shownAttempt = attempt,
                stillPending = true,
                persistentEnabled = values.persistentEnabled,
                snoozeMinutes = values.snoozeMinutes,
            ),
        )
        if (repeat is RepeatDecision.Repeat) {
            alarms.scheduleRepeat(intake.intakeId, repeat.at, repeat.attempt)
        }
        return repeat
    }

    private suspend fun suppress(
        now: Instant,
        reason: SuppressReason,
        intake: ReminderIntake? = null,
        scheduledAt: Instant? = null,
        deviation: Long? = null,
        intakeId: String? = null,
    ): FiredOutcome {
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.SUPPRESSED,
                intakeId = intake?.intakeId ?: intakeId,
                medicationId = intake?.medicationId,
                profileId = intake?.profileId,
                scheduledAt = scheduledAt,
                firedAt = now,
                deviationSeconds = deviation,
                delivered = false,
                reason = reason.code,
            ),
        )
        return FiredOutcome.Suppressed(reason)
    }
}
