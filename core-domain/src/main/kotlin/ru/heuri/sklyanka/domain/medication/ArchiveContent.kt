package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import java.time.LocalDate
import java.time.ZoneId

/**
 * Содержимое экрана «Архив»: две группы и общий счётчик.
 *
 * Группы — прямое следствие двух значений причины архивации, а не оформление
 * (`docs/spec/02-data-model.md` §5, ЭКР-8). [total] считает обе группы: счётчик
 * «Архив · N» не различает причин.
 */
data class ArchiveContent(val finished: List<ArchiveCard>, val manual: List<ArchiveCard>, val total: Int) {
    /** Архив пуст: сегмент остаётся доступным, а вместо списка показывается пояснение. */
    val isEmpty: Boolean get() = total == 0
}

/** Карточка архивного курса. Форма выпуска нужна плитке значка, а не тексту. */
data class ArchiveCard(
    val medicationId: String,
    val title: String,
    val form: MedicationForm,
    val caption: ArchiveCaption,
)

/**
 * Подпись внутри карточки архива (ЭКР-8).
 *
 * Запечатанный тип, а не два необязательных поля: подпись — **следствие** причины
 * архивации, и типом это выражено так, что карточка завершённого курса физически не
 * может получить подпись «в архиве с», и наоборот. Показать приверженность у курса,
 * прерванного на третий день из десяти, значит сообщить человеку неверный вывод;
 * типом это становится невозможным, а не оставляется на внимательность.
 */
sealed interface ArchiveCaption {

    /** «7 дней · принято 13 из 14» — только у завершённого курса. */
    data class Outcome(val days: Int, val taken: Int, val planned: Int) : ArchiveCaption

    /** «в архиве с 3 августа» — только у убранного вручную. */
    data class ArchivedOn(val date: LocalDate) : ArchiveCaption
}

/**
 * Раскладка архива по причине архивации.
 *
 * Группировка в домене, а не двумя запросами к базе: правило «раздел определяется
 * причиной архивации» предметное и проверяется тестом на JVM.
 */
object ArchiveGrouping {

    /**
     * @param archived архивные лекарства ровно одного профиля (ПР-10)
     * @param intakes их приёмы; из них считается итог завершённого курса
     * @param zone пояс профиля: момент архивации хранится в UTC, а показывается локально
     */
    fun of(
        archived: List<Medication>,
        intakes: List<Intake>,
        zone: ZoneId,
        denominator: OutcomeDenominator = OutcomeDenominator.UP_TO_ARCHIVING,
    ): ArchiveContent {
        val byMedication = intakes.groupBy { it.medicationId }
        // Лекарство без момента архивации в архив не попадает (ИНВ-9): пара полей
        // согласована, и причина без момента — испорченная запись, а не третья группа.
        val cards = archived.filter { it.isArchived }.map { medication ->
            medication.archiveReason to ArchiveCard(
                medicationId = medication.id,
                title = medication.name,
                form = medication.form,
                caption = captionOf(medication, byMedication[medication.id].orEmpty(), zone, denominator),
            )
        }
        return ArchiveContent(
            finished = cards.filter { it.first == ArchiveReason.COURSE_FINISHED }.map { it.second },
            // Причина, которую не удалось прочитать, попадает к убранным вручную вместе
            // со своей подписью-датой: скрыть лекарство из архива значило бы потерять
            // единственный способ его вернуть.
            manual = cards.filterNot { it.first == ArchiveReason.COURSE_FINISHED }.map { it.second },
            total = cards.size,
        )
    }

    private fun captionOf(
        medication: Medication,
        intakes: List<Intake>,
        zone: ZoneId,
        denominator: OutcomeDenominator,
    ): ArchiveCaption = when (medication.archiveReason) {
        ArchiveReason.COURSE_FINISHED -> CourseOutcome.of(medication, intakes, zone, denominator)
        // Причина отсутствует только у испорченной записи; такую карточку подписываем
        // датой архивации — это факт, который есть всегда, когда лекарство в архиве.
        else -> ArchiveCaption.ArchivedOn(CourseOutcome.archivedDate(medication, zone))
    }
}
