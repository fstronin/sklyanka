package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ход курса в блоке «Курс» карточки лекарства.
 *
 * Запечатанный тип, а не поля с `null`: у бессрочного курса доля пройденного не равна
 * нулю, она **не определена** (ПР-6). Иначе в разметке появилась бы полоса на нуле
 * процентов, которую никто не просил.
 */
sealed interface CourseProgress {

    /** Курс с заданной датой окончания: период, остаток дней и доля пройденного. */
    data class Bounded(
        val start: LocalDate,
        val end: LocalDate,
        /**
         * Полные сутки, оставшиеся **после** сегодняшних.
         *
         * Сегодняшний день не засчитан — по тому же правилу, что и в недельной полосе
         * (ЭКР-6): день считается пройденным, когда он закончился. Ноль означает
         * «остался только сегодняшний день», и карточка говорит об этом отдельной
         * строкой, а не «осталось 0 дней».
         */
        val daysLeft: Int,
        /** Доля пройденного от 0 до 1: завершённые сутки курса, делённые на его длину. */
        val fraction: Float,
    ) : CourseProgress {

        /** Сегодня — последний день курса (ИНВ-15: последний день входит в курс целиком). */
        val isLastDay: Boolean get() = daysLeft == 0

        /** Курс уже кончился: дата окончания в прошлом. */
        val isOver: Boolean get() = fraction >= 1f && daysLeft == 0
    }

    /** Бессрочный курс: ни полосы, ни остатка дней — их не существует (ПР-6). */
    data class Open(val start: LocalDate) : CourseProgress

    companion object {

        /**
         * Ход курса лекарства или `null`, если блока «Курс» у него нет вовсе.
         *
         * Блока нет у лекарства «по потребности»: курса как отрезка у него не бывает,
         * приём рождается в момент нажатия «Принять» (ПР-4, ПР-8).
         *
         * Всё считается по календарным датам, а не вычитанием меток времени: в сутках
         * с переводом часов их 23 или 25, и арифметика по секундам теряет либо
         * приобретает день (НФТ-37).
         */
        fun of(medication: Medication, today: LocalDate): CourseProgress? {
            val end = medication.endDate
            return when {
                medication.scheduleMode != ScheduleMode.SCHEDULED -> null
                end == null -> Open(medication.startDate)
                else -> Bounded(
                    start = medication.startDate,
                    end = end,
                    daysLeft = daysLeftOf(today, end),
                    fraction = fractionOf(medication.startDate, end, today),
                )
            }
        }

        /**
         * Остаток дней: «осталось 17 дней» при сегодняшнем 25 сентября и окончании 12 октября.
         *
         * Именно 17, а не 18: сегодняшний день ещё идёт и в остаток не входит. Число
         * взято из макета `MedCard.dc.html`, и формула выбрана под него, а не наоборот —
         * иначе карточка обещала бы человеку день, которым он уже распорядился.
         */
        private fun daysLeftOf(today: LocalDate, end: LocalDate): Int =
            ChronoUnit.DAYS.between(today, end).coerceAtLeast(0).toInt()

        /**
         * Доля пройденного: завершённые сутки курса к его длине.
         *
         * Сегодняшний день не считается пройденным (ЭКР-6), поэтому сумма пройденного и
         * остатка на день меньше длины курса — это не потерянный день, а текущий.
         */
        private fun fractionOf(start: LocalDate, end: LocalDate, today: LocalDate): Float {
            val total = ChronoUnit.DAYS.between(start, end) + 1
            if (total <= 0) return 1f
            val elapsed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
            return elapsed.toFloat() / total.toFloat()
        }
    }
}
