package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import java.time.LocalDate

/**
 * Блок «Последние 7 дней» карточки лекарства.
 *
 * [taken] и [planned] — числитель и знаменатель счётчика «12 из 14 приёмов». Знаменатель
 * считает приёмы всех семи дней, включая сегодняшний; числитель — только принятые
 * в завершившихся сутках (ЭКР-6). Отсюда и берётся «12 из 14» при шести полностью
 * принятых днях по два приёма: сегодняшний приём в числитель не входит, а в знаменателе
 * стоит план всей недели.
 */
data class WeekAdherenceStrip(
    /** Ровно семь ячеек: от шести дней назад до сегодняшнего включительно. */
    val days: List<DayCell>,
    val taken: Int,
    val planned: Int,
)

/**
 * Один день недельной полосы.
 *
 * Запечатанный тип, а не день с флагом «сегодня»: сегодняшний день не участвует в счёте
 * вовсе (ЭКР-6), и число принятых у него не существует, а не равно нулю.
 */
sealed interface DayCell {

    val date: LocalDate

    /** Завершившиеся сутки: столько приёмов было и столько отмечено принятыми. */
    data class Completed(override val date: LocalDate, val taken: Int, val planned: Int) : DayCell

    /** Сегодняшний день: полый кружок, в счётчик не входит (ЭКР-6). */
    data class Today(override val date: LocalDate) : DayCell

    /** День до начала курса: пустой кружок без цвета — приёмов тогда и не могло быть. */
    data class BeforeCourse(override val date: LocalDate) : DayCell

    companion object {

        /** Ширина полосы: семь дней (`design/screens/MedCard.dc.html`). */
        const val WEEK_LENGTH = 7
    }
}

/**
 * Сборка недельной полосы.
 *
 * Правило живёт в домене, а не в разметке: «сегодня не засчитывается» — предметное
 * решение (ЭКР-6), и проверяется он тестом на JVM, а не разглядыванием экрана.
 *
 * Для лекарства «по потребности» понятия «пропущено» нет: плана не существует (ПР-4),
 * поэтому знаменатель равен числу фактических записей, и пропусков в полосе не бывает.
 */
object WeekAdherence {

    /**
     * @param intakes приёмы этого лекарства; лишние даты отбрасываются здесь, а не запросом
     * @param today сегодняшняя дата из источника времени, а не из системных часов (НФТ-48)
     */
    fun of(medication: Medication, intakes: List<Intake>, today: LocalDate): WeekAdherenceStrip {
        val firstDay = today.minusDays((DayCell.WEEK_LENGTH - 1).toLong())
        val byDate = intakes
            .filter { it.medicationId == medication.id }
            .groupBy { it.scheduledAt.toLocalDate() }

        val cells = (0 until DayCell.WEEK_LENGTH).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            val onDate = byDate[date].orEmpty()
            when {
                date == today -> DayCell.Today(date)
                // Дни до начала курса пусты по природе, а не «без данных»: приёма
                // тогда не существовало, и серый кружок «пропущено» был бы ложью.
                date < medication.startDate -> DayCell.BeforeCourse(date)
                else -> DayCell.Completed(
                    date = date,
                    taken = onDate.count { it.status == IntakeStatus.TAKEN },
                    planned = onDate.size,
                )
            }
        }

        return WeekAdherenceStrip(
            days = cells,
            // Числитель — только завершившиеся сутки (ЭКР-6): иначе каждое утро сводка
            // показывала бы провал, которого нет.
            taken = cells.filterIsInstance<DayCell.Completed>().sumOf { it.taken },
            // Знаменатель — приёмы всей недели, включая сегодняшние: «12 из 14» в макете
            // это семь дней по два приёма, а не шесть.
            planned = cells.sumOf { cell -> byDate[cell.date].orEmpty().size },
        )
    }
}
