package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Знаменатель итога курса (ОВ-44).
 *
 * Для курса, дошедшего до своей даты окончания, варианты совпадают. Для прерванного
 * досрочно — нет, и разница принципиальна: «принято 3 из 20» у курса, который человек
 * сознательно прекратил на третий день, сообщает провал, которого не было.
 */
enum class OutcomeDenominator {

    /**
     * Только приёмы, успевшие наступить до архивации. Значение по умолчанию.
     *
     * Выбрано потому, что это единственный вариант, не приписывающий человеку пропуск
     * приёмов, от которых он отказался. Решение продуктовое и требует подтверждения
     * заказчика; смена ответа — правка этой строки вызова, а не экрана.
     */
    UP_TO_ARCHIVING,

    /** Все записи приёмов курса, включая порождённые после архивации. */
    WHOLE_COURSE,
}

/**
 * Итог завершённого курса: «7 дней · принято 13 из 14» (ЭКР-8).
 *
 * Длительность считается по календарным датам включительно, а не вычитанием меток
 * времени: курс, пересёкший переход на летнее время, иначе теряет или приобретает день
 * (НФТ-37).
 */
object CourseOutcome {

    fun of(
        medication: Medication,
        intakes: List<Intake>,
        zone: ZoneId,
        denominator: OutcomeDenominator = OutcomeDenominator.UP_TO_ARCHIVING,
    ): ArchiveCaption.Outcome {
        val last = lastDayOf(medication, zone)
        val counted = when (denominator) {
            OutcomeDenominator.WHOLE_COURSE -> intakes
            // Граница по назначенному времени, а не по моменту отметки: знаменатель — это
            // то, что приложение обещало человеку, а не то, когда он нажимал кнопку.
            OutcomeDenominator.UP_TO_ARCHIVING -> intakes.filterNot { it.scheduledAt.toLocalDate().isAfter(last) }
        }
        return ArchiveCaption.Outcome(
            days = daysOf(medication.startDate, last),
            taken = counted.count { it.status == IntakeStatus.TAKEN },
            planned = counted.size,
        )
    }

    /** Дата помещения в архив в поясе профиля: момент хранится в UTC, показывается локально. */
    fun archivedDate(medication: Medication, zone: ZoneId): LocalDate =
        medication.archivedAt?.atZone(zone)?.toLocalDate() ?: medication.startDate

    /**
     * Последний день, вошедший в курс.
     *
     * Курс, дошедший до своей даты окончания, кончается ею; курс, убранный раньше, —
     * днём архивации: обещать «14 дней» курсу, прерванному на седьмой, нельзя.
     * У бессрочного курса даты окончания нет вовсе (ПР-6), и его длина — срок до архивации.
     *
     * Нижняя граница — день начала курса: курс короче одного дня не существует, а запись,
     * у которой момент архивации раньше начала приёма, испорчена, и отвечать на неё
     * отрицательной длительностью нельзя.
     */
    private fun lastDayOf(medication: Medication, zone: ZoneId): LocalDate {
        val archived = archivedDate(medication, zone)
        val end = medication.endDate?.let { minOf(it, archived) } ?: archived
        return maxOf(medication.startDate, end)
    }

    /** Длина отрезка в днях включительно: «с 15 по 21 августа» — семь дней, а не шесть. */
    private fun daysOf(start: LocalDate, last: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, last) + 1).coerceAtLeast(1).toInt()
}
