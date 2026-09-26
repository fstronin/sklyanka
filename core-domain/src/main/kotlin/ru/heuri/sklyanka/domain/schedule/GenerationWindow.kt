package ru.heuri.sklyanka.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Окно генерации приёмов: отрезок календарных дат, на который разворачивается расписание.
 *
 * Обе границы включительны, потому что окно — набор суток, а не интервал моментов:
 * «по 1 октября включительно» и «до 2 октября, 00:00» совпадают только до тех пор, пока
 * в сутках ровно 24 часа (НФТ-37). Граница суток считается по календарю, а не вычитанием
 * суток из метки времени.
 */
data class GenerationWindow(val from: LocalDate, val toInclusive: LocalDate) {

    init {
        require(!toInclusive.isBefore(from)) {
            "Generation window ends before it starts: $from .. $toInclusive"
        }
    }

    /** Число суток в окне, считая обе границы. */
    val days: Int get() = (ChronoUnit.DAYS.between(from, toInclusive) + 1).toInt()

    /** Нижняя граница выборки существующих приёмов — полночь первых суток окна. */
    val start: LocalDateTime get() = from.atStartOfDay()

    /** Верхняя граница выборки, исключительная: полночь суток, следующих за последними. */
    val endExclusive: LocalDateTime get() = toInclusive.plusDays(1).atStartOfDay()

    /** Даты окна по возрастанию. */
    fun dates(): List<LocalDate> = generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(toInclusive) }
        .toList()

    operator fun contains(moment: LocalDateTime): Boolean = !moment.isBefore(start) && moment.isBefore(endExclusive)

    companion object {

        /**
         * Окно от текущей даты на заданную глубину, считая текущие сутки.
         *
         * Глубина приходит параметром, а не константой по месту: ОВ-8 не закрыт, и значение
         * обязано меняться одной правкой настройки, а не переписыванием планировщика.
         */
        fun of(today: LocalDate, depthDays: Int): GenerationWindow {
            require(depthDays >= MIN_DEPTH_DAYS) {
                "Generation window must cover the 48 hour alarm window: depth=$depthDays"
            }
            return GenerationWindow(from = today, toInclusive = today.plusDays((depthDays - 1).toLong()))
        }

        /**
         * Наименьшая допустимая глубина окна.
         *
         * НАП-6 и НАП-7: будильник ставится на уже существующий приём в пределах 48 часов,
         * поэтому окно генерации обязано строго перекрывать эти 48 часов из любого момента
         * суток. Двое суток этого не дают — в 23:00 они кончаются через 25 часов; трое дают.
         */
        const val MIN_DEPTH_DAYS = 3
    }
}
