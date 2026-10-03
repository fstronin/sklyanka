package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDateTime

/**
 * Приём глазами планировщика напоминаний: только идентификаторы и признаки отбора.
 *
 * Названия лекарства, дозы и дозировки здесь нет намеренно. Это не экономия полей:
 * через этот тип проходит всё, что попадает в журнал срабатываний, а журнал показывается
 * на экране, копируется текстом и выгружается файлом (НФТ-19, НФТ-51). Медицинских данных
 * в нём не должно быть по построению, а не по внимательности того, кто пишет вызов.
 *
 * Содержимое карточки напоминания живёт отдельным типом [ReminderCard] и в журнал
 * не попадает.
 */
data class ReminderIntake(
    val intakeId: String,
    val medicationId: String,
    val profileId: String,
    /** Назначенное время — локальное (НФТ-34): «08:00» остаётся «08:00» после перелёта. */
    val scheduledAt: LocalDateTime,
    val status: IntakeStatus,
    val scheduleMode: ScheduleMode,
    /** Лекарство в архиве: его приёмы в планировании не участвуют (ПР-11). */
    val medicationArchived: Boolean,
    /** Лекарство мягко удалено: приёмы не напоминают и на часы не уходят (ПР-14). */
    val medicationDeleted: Boolean,
    /** Профиль владельца приложения (ИНВ-1). */
    val profileIsSelf: Boolean,
    /** Дублировать ли напоминания чужого профиля владельцу (ПРФ-6). */
    val profileMirrorsReminders: Boolean,
) {
    /** Слот карточки: несколько приёмов одного профиля на одно время — одна карточка (НАП-36). */
    val slot: ReminderSlot get() = ReminderSlot(profileId, scheduledAt)
}

/**
 * Адрес карточки напоминания: профиль и назначенное время.
 *
 * Профиль входит в ключ, потому что приёмы двух профилей на 14:00 — это две карточки:
 * у чужого профиля своя индикация и свой контекст (ПРФ-8, полная реализация — SKL-15).
 */
data class ReminderSlot(val profileId: String, val scheduledAt: LocalDateTime)
