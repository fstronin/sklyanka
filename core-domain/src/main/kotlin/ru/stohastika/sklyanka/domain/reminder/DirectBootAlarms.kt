package ru.stohastika.sklyanka.domain.reminder

import java.time.Instant

/**
 * Один будильник, зеркалируемый в device-protected storage.
 *
 * Только адрес и момент: идентификатор приёма, абсолютный момент срабатывания и номер
 * показа. Названий лекарств, дозировок и имён профилей здесь нет и не появится (НФТ-19
 * распространяется на DE-хранилище, где данные лежат под ключом устройства, а не человека).
 */
data class DirectBootAlarm(val intakeId: String, val at: Instant, val attempt: Int)

/**
 * Снимок: что должно стоять в системе на момент последнего изменения.
 *
 * Вторичен и восстановим из CE-данных: он зеркалирует поставленные будильники, а не копирует
 * расписание. [updatedAt] — момент последнего изменения, по нему отбраковывается устаревшее.
 */
data class DirectBootAlarms(val version: Int, val updatedAt: Instant, val alarms: List<DirectBootAlarm>)

/**
 * След попытки восстановления в locked-фазе — для журнала после разблокировки.
 *
 * Журнал (CE) недоступен до разблокировки, поэтому факт восстановления живёт здесь, в DE,
 * пока его не заберёт [ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore].
 * Медицинских данных в следе нет по построению (НФТ-19).
 */
data class DirectBootTrace(
    val occurredAt: Instant,
    val replayed: Int,
    val skipped: Int,
    val failed: Int,
    /** Код исхода, не текст для человека (НФТ-42). */
    val reason: String,
)

/** Исход попытки восстановления; код уходит в трассу, не текст для человека. */
enum class DirectBootTraceReason(val code: String) {
    REPLAYED("replayed"),
    EMPTY("empty"),
    MISSING("missing"),
    UNREADABLE("unreadable"),
    STALE("stale"),
    EXACT_DENIED("exact_denied"),
}
