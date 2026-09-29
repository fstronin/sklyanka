package ru.stohastika.sklyanka.domain.model

import java.math.BigDecimal
import java.time.ZoneId

/**
 * Отказ по нарушенному инварианту модели данных.
 *
 * Коды — из docs/spec/02-data-model.md §9 и записаны латиницей (INV-4), потому что
 * в код не попадает русский текст: сообщение для человека собирает интерфейс из
 * ресурсов, а этот список нужен разработчику и журналу (НФТ-42).
 */
class InvariantViolationException(val violations: List<String>) :
    IllegalArgumentException("Data model invariants violated: ${violations.joinToString()}")

/**
 * Правила условной обязательности полей.
 *
 * Часть инвариантов держит база (уникальность, внешние ключи), часть выразить схемой
 * нельзя — «strength и strength_unit заполнены оба или ни одного» не ограничение
 * столбца, а связь двух столбцов. Эти проверки живут здесь и применяются до записи.
 */
object Invariants {

    /** Профиль: имя непустое после обрезки пробелов (docs/spec/02-data-model.md §2). */
    fun violations(profile: Profile): List<String> = buildList {
        if (profile.name.isBlank()) add(PROFILE_NAME_BLANK)
    }

    /** Лекарство: INV-4, INV-5, INV-6, INV-7, INV-8, INV-9, INV-16, INV-17, INV-20. */
    fun violations(medication: Medication): List<String> = buildList {
        if (medication.name.isBlank()) add(MEDICATION_NAME_BLANK)
        if (medication.doseAmount <= BigDecimal.ZERO) add("INV-4")
        if ((medication.strength == null) != (medication.strengthUnit == null)) add("INV-5")
        addAll(scheduleViolations(medication))
        if (medication.endDate != null && medication.endDate < medication.startDate) add("INV-7")
        if (medication.stockLeft != null && medication.stockLeft < BigDecimal.ZERO) add("INV-8")
        if (medication.packageSize != null && medication.packageSize <= BigDecimal.ZERO) add("INV-20")
        if ((medication.archivedAt == null) != (medication.archiveReason == null)) add("INV-9")
        if (medication.shape != null && !medication.form.allowsShape) add("INV-16")
    }

    /** Приём сам по себе: INV-10 и INV-11 — поля отметки согласованы со статусом. */
    fun violations(intake: Intake): List<String> = buildList {
        val marked = !intake.isPending
        if (marked && (intake.actedAt == null || intake.source == null)) add("INV-10")
        if (!marked && (intake.actedAt != null || intake.source != null)) add("INV-10")
        if (!marked && intake.actorProfileId != null) add("INV-11")
    }

    /**
     * Приём в паре со своим лекарством: INV-14, INV-15, INV-18.
     *
     * Пояс нужен для INV-14: назначенное время локальное, момент отметки — в UTC,
     * и сравнивать их иначе, чем приведя момент к местному времени, нельзя.
     */
    fun violations(intake: Intake, medication: Medication, zone: ZoneId): List<String> = buildList {
        addAll(violations(intake))
        if (intake.medicationId != medication.id) add(INTAKE_FOREIGN_MEDICATION)
        if (medication.scheduleMode == ScheduleMode.PRN) {
            val actedLocal = intake.actedAt?.atZone(zone)?.toLocalDateTime()?.withSecond(0)?.withNano(0)
            if (intake.isPending || actedLocal != intake.scheduledAt) add("INV-14")
        }
        if (intake.scheduledAt.toLocalDate() < medication.startDate) add("INV-15")
        val endDate = medication.endDate
        if (endDate != null && intake.scheduledAt.toLocalDate() > endDate) add("INV-15")
        if (intake.actorProfileId != null && intake.actorProfileId == medication.profileId) add("INV-18")
    }

    /** Бросает [InvariantViolationException], если список нарушений непуст. */
    fun requireValid(violations: List<String>) {
        if (violations.isNotEmpty()) throw InvariantViolationException(violations)
    }

    /**
     * INV-6 и INV-17 разом: расписание и «по потребности» — взаимоисключающие наборы полей.
     *
     * Ноль вместо «неприменимо» здесь запрещён (ПР-7, ПР-8): пустой список времён у курса
     * по расписанию означал бы курс, который никогда не напомнит о себе.
     */
    private fun scheduleViolations(medication: Medication): List<String> = buildList {
        when (medication.scheduleMode) {
            ScheduleMode.SCHEDULED -> {
                if (medication.times.isEmpty() || medication.times.distinct().size != medication.times.size) {
                    add("INV-6")
                }
                if (medication.maxPerDay != null) add("INV-17")
            }

            ScheduleMode.PRN -> {
                if (medication.times.isNotEmpty()) add("INV-6")
                if (medication.maxPerDay != null && medication.maxPerDay <= 0) add("INV-17")
            }
        }
    }

    private const val PROFILE_NAME_BLANK = "PROFILE-NAME-BLANK"
    private const val MEDICATION_NAME_BLANK = "MEDICATION-NAME-BLANK"
    private const val INTAKE_FOREIGN_MEDICATION = "INTAKE-FOREIGN-MEDICATION"
}
