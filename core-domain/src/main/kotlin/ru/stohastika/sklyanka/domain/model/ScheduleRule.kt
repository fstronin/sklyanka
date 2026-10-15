package ru.stohastika.sklyanka.domain.model

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Вид расписания курса: чем назначение врача отличается от «каждый день в 08:00»
 * (docs/norm/04-behavior.md §1.5, ПР-15).
 *
 * Вид — часть курса, а не отдельная сущность: у лекарства либо ежедневное правило, либо
 * ровно один из четырёх сложных видов. Так «вид у курса один» (ИНВ-25) держится типом,
 * а не проверкой: комбинации «два вида сразу» не существует, потому что её нечем выразить.
 *
 * Правило говорит **про дни**, а не про моменты: интервал и циклы считаются сутками
 * локального календаря от [Medication.startDate], поэтому перелёт и перевод часов на сетку
 * не влияют (НФТ-34, НФТ-37, открытый вопрос 2 в `docs/tasks/SKL-117/task.md`).
 * Времена приёма внутри дня общие для всех видов и живут в [Medication.times] (ПР-19).
 *
 * Значения типа **не проверяются конструктором**: проверки ИНВ-23…ИНВ-24 живут в
 * [Invariants] и [ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft], иначе
 * испорченную строку из файла переноса нельзя было бы ни прочитать, ни назвать словами
 * (МГ-6). Неверное правило поэтому «не падает», а ведёт себя по своей букве.
 */
sealed interface ScheduleRule {

    /**
     * Сложный ли вид — то есть требует ли он подписки «Про» (Р-17, Р-22, ПР-16, ПР-17).
     *
     * Базовое: ежедневное, приём в один выбранный день недели (Р-24) и «через каждый день»
     * (вырожденный интервал, ПР-17). Всё остальное — сложное, и его создание без подписки
     * закрыто.
     */
    val isComplex: Boolean

    /**
     * Идёт ли приём в этот день курса.
     *
     * День до начала курса приёма не несёт **ни у одного вида**: у ежедневного,
     * «по дням недели» и «по этапам» его приходилось бы отсекать по дате начала, у
     * «через N дней» он попадал под `index < 0`, а у циклов — нет. Три разных ответа
     * на один вопрос — это три правила вместо одного (SKL-117, критика F8): следующий
     * вызывающий получил бы от правила не то, что получил предыдущий.
     *
     * Внутри курса правило читается буквально и проверок не делает: параметры вида судит
     * [Invariants] и `ValidateMedicationDraft`, а неверное значение ведёт себя по своей букве
     * (МГ-6).
     */
    fun includes(date: LocalDate, startDate: LocalDate): Boolean

    /**
     * Доза за приём этого дня или `null`, если дозу даёт лекарство ([Medication.doseAmount]).
     *
     * Ненулевой ответ даёт только ступенчатое расписание (ПР-19): снимок дозы нужен затем,
     * чтобы переход на следующую ступень не переписывал уже созданные приёмы.
     *
     * Спрашивать дозу имеет смысл только у дня, который правило включает: день до начала
     * курса приёма не несёт (см. [includes]), и дозы у него нет.
     */
    fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal?

    /**
     * Ежедневное расписание — вид по умолчанию, в том числе для всех курсов, заведённых
     * до появления видов: миграция 9 → 10 оставляет их колонкой `NULL`.
     */
    data object Daily : ScheduleRule {
        override val isComplex: Boolean get() = false
        override fun includes(date: LocalDate, startDate: LocalDate): Boolean = !date.isBefore(startDate)
        override fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal? = null
    }

    /**
     * «По дням недели»: приём идёт только в выбранные дни недели (ПР-16).
     *
     * Один выбранный день — база (Р-24), два и более — «Про» (Р-17). Пустой набор —
     * нарушение ИНВ-23; разворачиватель читает его буквально, и приёмов не возникает.
     */
    data class Weekdays(val days: Set<DayOfWeek>) : ScheduleRule {
        override val isComplex: Boolean get() = days.size > 1

        override fun includes(date: LocalDate, startDate: LocalDate): Boolean =
            !date.isBefore(startDate) && date.dayOfWeek in days

        override fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal? = null
    }

    /**
     * «Через N дней»: каждый N-й день, считая от даты начала курса (ПР-17).
     *
     * `N ≤ 1` вырождается в ежедневное расписание само собой — форма такого значения
     * не создаёт (ПР-17 требует N ≥ 2), а испорченное значение обязано вести себя как
     * ежедневное, а не останавливать курс.
     */
    data class EveryNDays(val days: Int) : ScheduleRule {
        override val isComplex: Boolean get() = days > 1

        override fun includes(date: LocalDate, startDate: LocalDate): Boolean {
            val index = dayIndex(date, startDate)
            return index >= 0 && (days <= 1 || index % days.toLong() == 0L)
        }

        override fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal? = null
    }

    /**
     * «Циклами»: N дней приёма подряд, затем M дней перерыва, и так до конца курса (ПР-18).
     *
     * Длины меньше единицы — нарушение ИНВ-23; нулевой цикл приёмов прочитается буквально,
     * и приёмов в перерыве, естественно, не будет.
     */
    data class Cycles(val onDays: Int, val offDays: Int) : ScheduleRule {
        override val isComplex: Boolean get() = true

        override fun includes(date: LocalDate, startDate: LocalDate): Boolean {
            val index = dayIndex(date, startDate)
            if (index < 0) return false
            val period = onDays.toLong() + offDays.toLong()
            return period > 0 && index % period < onDays.toLong()
        }

        override fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal? = null
    }

    /**
     * «Ступенчатое»: этапы идут подряд от даты начала курса и меняют только дозу (ПР-19).
     *
     * День за концом последнего этапа продолжает курс последней ступенью, а не отменяет
     * приём: этап за концом курса — нарушение ИНВ-24, которое ловит форма, и молча
     * потерять приёмы из-за испорченного значения расписание не имеет права.
     */
    data class Staged(val stages: List<Stage>) : ScheduleRule {
        override val isComplex: Boolean get() = true

        override fun includes(date: LocalDate, startDate: LocalDate): Boolean = !date.isBefore(startDate)

        override fun doseFor(date: LocalDate, startDate: LocalDate): BigDecimal? {
            val index = dayIndex(date, startDate)
            if (index < 0 || stages.isEmpty()) return null
            var consumed = 0L
            val ends = stages.map { stage ->
                consumed += stage.days
                consumed
            }
            val position = ends.indexOfFirst { end -> index < end }
            return (if (position >= 0) stages[position] else stages.last()).doseAmount
        }
    }
}

/**
 * Этап ступенчатого расписания: доза за приём и длительность в днях (ИНВ-24).
 *
 * Доза — десятичная дробь текстом, а не `Double`: половина таблетки, списанная сотни раз,
 * обязана складываться с остатком без хвоста «-0,0000001» (МГ-6).
 */
data class Stage(val doseAmount: BigDecimal, val days: Int)

/**
 * Номер дня курса, считая от даты начала: 0 — первый день.
 *
 * Отрицательный ответ означает день до начала курса, и приёма в нём нет ни у одного вида:
 * правило отвечает на такой день «нет» (SKL-117, критика F8).
 */
private fun dayIndex(date: LocalDate, startDate: LocalDate): Long = ChronoUnit.DAYS.between(startDate, date)
