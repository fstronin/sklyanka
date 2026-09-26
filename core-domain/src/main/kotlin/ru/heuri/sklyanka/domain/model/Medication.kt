package ru.heuri.sklyanka.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Лекарство вместе с курсом и расписанием (docs/spec/02-data-model.md §3).
 *
 * Полей много, и дробить их на вложенные объекты здесь вредно: состав полей задан
 * моделью данных один в один, и любое «удобное» перегруппирование пришлось бы
 * разворачивать обратно в маппере, в файле переноса и в отчёте врачу.
 */
@Suppress("LongParameterList")
data class Medication(
    val id: String,
    /** Владелец. Неизменяем после создания (INV-3): перенос между профилями исказил бы историю. */
    val profileId: String,
    val name: String,
    val form: MedicationForm,
    /** Вид таблетки: только для TABLET и CAPSULE (ПР-9, INV-16). */
    val shape: TabletShape?,
    /** Сколько единиц принимается за раз. Строго больше нуля (INV-4). */
    val doseAmount: BigDecimal,
    val doseUnit: String,
    /** Дозировка препарата (500 в «по 500 мг»). Заполняется вместе с единицей (INV-5). */
    val strength: BigDecimal?,
    val strengthUnit: String?,
    val foodRelation: FoodRelation,
    val scheduleMode: ScheduleMode,
    /** Максимум приёмов в сутки: только для PRN (ПР-7, INV-17). */
    val maxPerDay: Int?,
    /**
     * Время каждого приёма в течение суток — локальное, без пояса.
     *
     * Это правило («утром в восемь»), а не момент: после перелёта приём остаётся
     * в 08:00 по местному времени (НФТ-34).
     */
    val times: List<LocalTime>,
    val startDate: LocalDate,
    /** Пустое значение означает бессрочный приём (ПР-6), а не неполные данные. */
    val endDate: LocalDate?,
    /** Остаток упаковки; null — человек остаток не ведёт. Ниже нуля не опускается (INV-8). */
    val stockLeft: BigDecimal?,
    val stockThresholdDays: Int?,
    /** Мягкое удаление: момент отправки в архив (ПР-11). Заполняется вместе с причиной (INV-9). */
    val archivedAt: Instant?,
    val archiveReason: ArchiveReason?,
    /**
     * Дублировать ли напоминания этого лекарства на часы (ЧАС-15).
     *
     * Поля нет в перечне `docs/spec/02-data-model.md` §3 — это пробел модели, а не
     * добавка экрана: тумблер «Напоминать на часах» в карточке лекарства требует
     * настройки на лекарство, и хранить её больше негде (ОВ-58). Поле заведено
     * миграцией схемы 2 → 3.
     *
     * Значение по умолчанию — включено: тумблер в макете `MedCard.dc.html` нарисован
     * включённым, а действующим ограничителем остаётся общее состояние связи с часами
     * в настройках (ЧАС-15: «поверх общего состояния связи»). Для лекарства
     * «по потребности» значение не влияет ни на одно решение: такие приёмы на часы
     * не отправляются вовсе (ЧАС-17).
     *
     * Значение по умолчанию требует подтверждения заказчика.
     */
    val mirrorToWatch: Boolean = true,
) {
    /** Архивированный курс не порождает новых приёмов и не участвует в планировании (ПР-11). */
    val isArchived: Boolean get() = archivedAt != null
}
