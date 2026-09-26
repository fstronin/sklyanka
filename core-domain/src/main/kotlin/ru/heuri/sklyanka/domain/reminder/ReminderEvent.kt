package ru.heuri.sklyanka.domain.reminder

import java.time.Instant

/**
 * Вид записи журнала срабатываний (НАП-4, НФТ-50, `TDD.md §11.1`).
 *
 * Журнал напоминаний ведётся отдельно от журнала операций над данными (`OpEvent`): вопросы
 * у них разные. Там — «кто и когда отметил приём», здесь — «почему напоминание не пришло».
 */
enum class ReminderEvent(val code: String) {
    /** Будильник сработал: плановое и фактическое время, отклонение. */
    FIRED("fired"),

    /** Уведомление предъявлено человеку — это и есть «напоминание сработало» (НАП-2). */
    SHOWN("shown"),

    /** Показ подавлен: приём уже отмечен, окно истекло или нет разрешения. */
    SUPPRESSED("suppressed"),

    /** Повтор настойчивого напоминания (НАП-25). */
    REPEATED("repeated"),

    /** Человек отложил напоминание (НАП-30). */
    SNOOZED("snoozed"),

    /** Пересборка окна с причиной и числом снятых и поставленных будильников (НАП-14). */
    RESCHEDULED("rescheduled"),

    /** Автоперевод в «пропущено без отметки»; уведомлений не порождает (НАП-34). */
    MISSED_SWEEP("missed_sweep"),

    /** Пробное напоминание: боевой путь без записи приёма (НАП-22). */
    TEST("test"),

    /** Обнаружен перерыв в напоминаниях — принудительная остановка приложения (НАП-38). */
    OUTAGE("outage"),
}

/**
 * Одна запись журнала срабатываний.
 *
 * Полей много и они почти все необязательные: запись о пересборке окна и запись о показе
 * отвечают на разные вопросы, а два типа записей в одном журнале пришлось бы сводить
 * в диагностике вручную. Названий лекарств, дозировок и имён профилей здесь нет
 * ни в одном поле (НФТ-19).
 */
@Suppress("LongParameterList")
data class ReminderLogEntry(
    /**
     * Момент записи в UTC.
     *
     * Идентификатора записи в доменном типе нет: он нужен только хранилищу как первичный
     * ключ и присваивается там. Диагностика читает записи временем и видом, а не ключом.
     */
    val occurredAt: Instant,
    val event: ReminderEvent,
    val intakeId: String? = null,
    val medicationId: String? = null,
    val profileId: String? = null,
    /** Плановое время приёма как момент: журнал сравнивает его с фактическим (НАП-2). */
    val scheduledAt: Instant? = null,
    val firedAt: Instant? = null,
    /** `firedAt − scheduledAt`; отрицательное значение — дефект планирования (НАП-2). */
    val deviationSeconds: Long? = null,
    val channel: ReminderChannel? = null,
    val delivered: Boolean? = null,
    /** Причина пересборки или код подавления. Служебная строка, не текст для человека. */
    val reason: String? = null,
    val alarmsCancelled: Int? = null,
    val alarmsScheduled: Int? = null,
    val attempt: Int? = null,
    /** Интервал откладывания в минутах — только у записи [ReminderEvent.SNOOZED]. */
    val minutes: Int? = null,
    val watchResult: String? = null,
)
