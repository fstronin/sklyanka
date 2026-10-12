package ru.stohastika.sklyanka.domain.model

import java.math.BigDecimal
import java.time.ZoneId

/**
 * Отказ по нарушенному инварианту модели данных.
 *
 * Коды — из docs/norm/04-behavior.md §9 и записаны латиницей (INV-4), потому что
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

    /** Профиль: имя непустое после обрезки пробелов (docs/norm/04-behavior.md §2). */
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
        if (conflictsWithArchive(medication)) add("INV-21")
        if (medication.shape != null && !medication.form.allowsShape) add("INV-16")
    }

    /** ИНВ-21: «удалено» и «в архиве» не сосуществуют — проверка вынесена ради читаемости списка. */
    private fun conflictsWithArchive(medication: Medication): Boolean =
        medication.deletedAt != null && (medication.archivedAt != null || medication.archiveReason != null)

    /** Приём сам по себе: INV-10 и INV-11 — поля отметки согласованы со статусом. */
    fun violations(intake: Intake): List<String> = buildList {
        val marked = !intake.isPending
        if (marked && (intake.actedAt == null || intake.source == null)) add("INV-10")
        if (!marked && (intake.actedAt != null || intake.source != null)) add("INV-10")
        if (!marked && intake.actorProfileId != null) add("INV-11")
    }

    /** ИНВ-14 (ветвь «не ожидается») для границ, где из пары известен только режим. */
    fun pendingInPrnViolations(intake: Intake, scheduleMode: ScheduleMode): List<String> =
        if (scheduleMode == ScheduleMode.PRN && intake.isPending) listOf("INV-14") else emptyList()

    /**
     * Приём сам по себе + режим лекарства: ИНВ-10, ИНВ-11 и ожидающаяся строка
     * «по потребности» (ИНВ-14). Полную пару проверяют [markingViolations] и
     * [violations] с лекарством; здесь — минимум, который виден по одному режиму.
     */
    fun writeViolations(intake: Intake, scheduleMode: ScheduleMode): List<String> =
        violations(intake) + pendingInPrnViolations(intake, scheduleMode)

    /**
     * ИНВ-3: у созданного лекарства владелец не меняется.
     *
     * Сверка с хранимым, а не с самим собой: принадлежность — факт истории, и проверка
     * имеет смысл только там, где перезаписывают существующую строку, — при сохранении
     * формы и при применении плана переноса.
     */
    fun ownershipViolations(existingProfileId: String, updated: Medication): List<String> =
        if (existingProfileId != updated.profileId) listOf("INV-3") else emptyList()

    /**
     * ИНВ-18: отметивший — не владелец профиля лекарства.
     *
     * Ссылка на существующий профиль держит внешний ключ (ИНВ-12); здесь — то, чего
     * схемой не выразить: совпадение с самим владельцем.
     */
    fun actorOwnershipViolations(actorProfileId: String?, medicationProfileId: String): List<String> =
        if (actorProfileId != null && actorProfileId == medicationProfileId) listOf("INV-18") else emptyList()

    /**
     * Граница отметки живущей записи и строк файла: ИНВ-10, ИНВ-11, чужое лекарство, ИНВ-18.
     *
     * Курс (ИНВ-15) и режим пары (ИНВ-14) здесь не судят: даты правятся после приёмов,
     * при смене режима остаются прежние «ожидается» — оба обычное дело, и отказ им
     * стоил бы человеку реальной истории. Родившуюся строку проверяют [violations]
     * и [prnBirthViolations].
     */
    fun markingViolations(intake: Intake, medication: Medication): List<String> = buildList {
        addAll(violations(intake))
        if (intake.medicationId != medication.id) add(INTAKE_FOREIGN_MEDICATION)
        addAll(actorOwnershipViolations(intake.actorProfileId, medication.profileId))
    }

    /**
     * Рождение приёма «по потребности»: граница отметки плюс ИНВ-14.
     *
     * Пояс нужен для ИНВ-14: назначенное время локальное, момент отметки — в UTC,
     * и сравнивать их иначе, чем приведя момент к местному времени, нельзя. Курс не
     * судится: «принять» фиксирует факт, а кнопка жива, пока курс не убран в архив.
     */
    fun prnBirthViolations(intake: Intake, medication: Medication, zone: ZoneId): List<String> =
        markingViolations(intake, medication) + prnPairViolations(intake, medication, zone)

    /**
     * Приём в паре со своим лекарством: полный набор — ИНВ-14, ИНВ-15, ИНВ-18.
     *
     * Это граница генерации: приём, порождаемый расписанием, обязан родиться внутри
     * курса, и отказ здесь означает баг экспандера, а не данные человека.
     */
    fun violations(intake: Intake, medication: Medication, zone: ZoneId): List<String> =
        markingViolations(intake, medication) +
            prnPairViolations(intake, medication, zone) +
            courseViolations(intake, medication)

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

    /** ИНВ-14: приём «по потребности» не ожидается и отмечен в свою назначенную минуту. */
    private fun prnPairViolations(intake: Intake, medication: Medication, zone: ZoneId): List<String> = buildList {
        addAll(pendingInPrnViolations(intake, medication.scheduleMode))
        if (medication.scheduleMode == ScheduleMode.PRN && !intake.isPending) {
            val actedLocal = intake.actedAt?.atZone(zone)?.toLocalDateTime()?.withSecond(0)?.withNano(0)
            if (actedLocal != intake.scheduledAt) add("INV-14")
        }
    }

    /** ИНВ-15: назначенное время лежит в пределах курса. */
    private fun courseViolations(intake: Intake, medication: Medication): List<String> = buildList {
        if (intake.scheduledAt.toLocalDate() < medication.startDate) add("INV-15")
        val endDate = medication.endDate
        if (endDate != null && intake.scheduledAt.toLocalDate() > endDate) add("INV-15")
    }

    private const val PROFILE_NAME_BLANK = "PROFILE-NAME-BLANK"
    private const val MEDICATION_NAME_BLANK = "MEDICATION-NAME-BLANK"
    private const val INTAKE_FOREIGN_MEDICATION = "INTAKE-FOREIGN-MEDICATION"
}
