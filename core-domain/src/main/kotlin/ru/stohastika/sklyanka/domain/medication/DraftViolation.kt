package ru.stohastika.sklyanka.domain.medication

import java.time.LocalTime

/**
 * Поле формы, на которое указывает отказ.
 *
 * Отказ без поля — это «что-то не так»: на форме в одиннадцать блоков такое сообщение
 * заставляет человека перебирать блоки самому. Перечисление живёт в домене, а не на
 * экране, потому что правило «какое правило про какое поле» предметное, а не оформительское.
 */
enum class DraftField {
    NAME,
    DOSE,
    STRENGTH,
    TIMES,
    MAX_PER_DAY,
    PERIOD,
    STOCK,
}

/**
 * Нарушение правила формы (`TDD.md §5.3`).
 *
 * Правила формы существуют отдельно от инвариантов уровня данных намеренно.
 * Сработавший инвариант выглядит как отказ записи, а не как подсказка: он последняя
 * линия обороны и говорит разработчику. Эти правила говорят человеку и указывают
 * на конкретное поле.
 *
 * Дублирование с [ru.stohastika.sklyanka.domain.model.Invariants] осознанное: назначение
 * у двух проверок разное, и снимать нижнюю, потому что «сверху уже проверено»,
 * нельзя — записи приходят ещё и из файла переноса данных (SKL-19).
 */
sealed interface DraftViolation {

    /** Поле, к которому относится отказ: по нему экран прокручивается к первому нарушению. */
    val field: DraftField

    /** Название пусто после обрезки пробелов (`docs/spec/02-data-model.md` §3). */
    data object NameEmpty : DraftViolation {
        override val field: DraftField get() = DraftField.NAME
    }

    /** Доза на приём не больше нуля (ИНВ-4). */
    data object DoseNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.DOSE
    }

    /** Дозировка задана, единица — нет (ИНВ-5). */
    data object StrengthWithoutUnit : DraftViolation {
        override val field: DraftField get() = DraftField.STRENGTH
    }

    /** Единица задана, дозировка — нет (ИНВ-5). */
    data object UnitWithoutStrength : DraftViolation {
        override val field: DraftField get() = DraftField.STRENGTH
    }

    /** Режим «по расписанию» без единого времени приёма (ИНВ-6, ПР-8). */
    data object NoIntakeTimes : DraftViolation {
        override val field: DraftField get() = DraftField.TIMES
    }

    /**
     * Время приёма повторяется (ИНВ-6).
     *
     * Время несётся в самом нарушении: сказать «есть повтор» и не сказать какой —
     * значит заставить человека сверять список глазами.
     */
    data class DuplicateTime(val time: LocalTime) : DraftViolation {
        override val field: DraftField get() = DraftField.TIMES
    }

    /** Дата окончания раньше даты начала (ИНВ-7). */
    data object EndDateBeforeStart : DraftViolation {
        override val field: DraftField get() = DraftField.PERIOD
    }

    /** Режим «по потребности» без ограничения числа приёмов (ИНВ-17, ПР-7). */
    data object MaxPerDayMissing : DraftViolation {
        override val field: DraftField get() = DraftField.MAX_PER_DAY
    }

    /** Ограничение числа приёмов не больше нуля (ИНВ-17). */
    data object MaxPerDayNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.MAX_PER_DAY
    }

    /** Остаток в упаковке отрицателен (ИНВ-8). */
    data object StockNegative : DraftViolation {
        override val field: DraftField get() = DraftField.STOCK
    }
}
