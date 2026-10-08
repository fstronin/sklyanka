package ru.stohastika.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись журнала срабатываний напоминаний (НАП-4, НФТ-50, `TDD.md §4.1`).
 *
 * Отдельная таблица рядом с `op_log`, а не расширение его полями: там след операций над
 * данными («кто и когда отметил приём»), здесь — след доставки («почему напоминание
 * не пришло»). Общая таблица заставила бы каждый запрос диагностики фильтровать половину
 * записей и половину колонок держать пустыми.
 *
 * Внешних ключей нет: запись обязана пережить приём, о котором она говорит, — иначе
 * разбирать жалобу будет не по чему.
 *
 * **Медицинских данных здесь нет ни в одной колонке** (НФТ-19): только идентификаторы,
 * моменты, отклонения и служебные коды. Журнал показывается на экране, копируется текстом
 * и может быть выгружен файлом (НФТ-51) — то есть покидает приложение в руках человека,
 * который не обязан понимать, что он кому-то показывает.
 *
 * Идентификатор — автоинкремент, а не UUID из `TDD.md §4.1`: журнал не переносится и не
 * сливается, зато нуждается в порядке и в вытеснении «оставить последние N», а оба
 * механизма на строковом ключе пришлось бы делать через сортировку по времени записи,
 * то есть терять записи одной миллисекунды.
 */
@Entity(
    tableName = "reminder_log",
    indices = [Index(value = ["occurred_at"]), Index(value = ["intake_id"])],
)
@Suppress("LongParameterList")
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    /** Момент записи, epoch millis UTC. */
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "event") val event: String,
    @ColumnInfo(name = "intake_id") val intakeId: String?,
    /** Идентификатор лекарства, а НЕ его название (НФТ-19). */
    @ColumnInfo(name = "medication_id") val medicationId: String?,
    @ColumnInfo(name = "profile_id") val profileId: String?,
    /** Плановое время приёма как момент: с ним сравнивается фактическое (НАП-2). */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long?,
    @ColumnInfo(name = "fired_at") val firedAt: Long?,
    @ColumnInfo(name = "deviation_seconds") val deviationSeconds: Long?,
    @ColumnInfo(name = "channel") val channel: String?,
    @ColumnInfo(name = "delivered") val delivered: Boolean?,
    /** Служебный код: причина пересборки или причина подавления показа. */
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "alarms_cancelled") val alarmsCancelled: Int?,
    @ColumnInfo(name = "alarms_scheduled") val alarmsScheduled: Int?,
    /** Номер показа: 0 — первое напоминание, 1 и 2 — повторы (НАП-26). */
    @ColumnInfo(name = "attempt") val attempt: Int?,
    /** Интервал откладывания в минутах — только у записи об откладывании. */
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int?,
)
