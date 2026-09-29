package ru.stohastika.sklyanka.domain.reminder

import java.time.Instant
import java.time.LocalDateTime

/**
 * Одно напоминание, которое обязано стоять в системе.
 *
 * [at] — момент срабатывания, вычисленный из локального [scheduledAt] и **текущего**
 * часового пояса. Пояс входит в вычисление, а не в хранение (НФТ-34), и именно поэтому
 * смена пояса требует полной пересборки окна (НАП-11).
 *
 * [attempt] — номер показа, который доедет до `AlarmGateway.schedule`: у срабатывания
 * из окна он нулевой, у восстановленного откладывания или повтора — из записи (SKL-35).
 * В сравнении с системой номера не участвуют: сравниваются моменты, а не номера.
 */
data class PlannedReminder(
    val intakeId: String,
    val at: Instant,
    val scheduledAt: LocalDateTime,
    val medicationId: String,
    val profileId: String,
    val attempt: Int = 0,
)

/**
 * Чем закончилась пересборка окна — то, что уходит в журнал (НАП-14) и в диагностику (НАП-37).
 *
 * [exactAlarmsAllowed] возвращается наружу, а не прячется в журнал: молчаливая деградация
 * до неточных будильников запрещена (НАП-16), и «Сегодня» обязан показать предупреждение.
 */
data class RescheduleOutcome(
    val reason: RescheduleReason,
    val cancelled: Int,
    val scheduled: Int,
    val total: Int,
    val nextAt: Instant?,
    val nextIntakeId: String?,
    val exactAlarmsAllowed: Boolean,
    val notificationsEnabled: Boolean,
)
