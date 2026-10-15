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

    /** Вид расписания и его параметры (`docs/norm/04-behavior.md` §1.5, ПР-15…ПР-19). */
    SCHEDULE,
    MAX_PER_DAY,
    PERIOD,
    STOCK,

    /** «Размер упаковки»: та же строка блока, что остаток, но своё поле ошибки. */
    PACKAGE_SIZE,
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

    /** Название пусто после обрезки пробелов (`docs/norm/04-behavior.md` §3). */
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
     * «По дням недели» без единого выбранного дня (ИНВ-23).
     *
     * Пустой набор — не «ежедневно», а недонастроенный вид: человек выбрал вид, но не
     * сказал, в какие дни принимать, и сохранить такое расписание значит не напомнить
     * ни разу.
     */
    data object ScheduleDaysEmpty : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /** «Через N дней» с интервалом меньше двух дней (ИНВ-23, ПР-17). */
    data object ScheduleIntervalTooShort : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /** У цикла приёма или перерыва длина меньше одного дня (ИНВ-23, ПР-18). */
    data object ScheduleCycleLengthNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /** Ступенчатое расписание без единого этапа (ИНВ-23). */
    data object ScheduleStagesEmpty : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /**
     * Этап без дозы (ИНВ-24).
     *
     * Доза и длительность — два разных поля этапа и два разных слова человеку
     * (`docs/norm/06-design.md` §10: `error_stage_dose`, `error_stage_days`): слитая
     * подсказка «заполните этап» заставила бы человека сверять, что именно пропущено.
     */
    data object ScheduleStageDoseNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /** Этап без длительности или с длительностью меньше суток (ИНВ-24). */
    data object ScheduleStageDaysNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
    }

    /**
     * Последний этап выходит за конец курса (ИНВ-24).
     *
     * Этапы идут подряд от даты начала, поэтому длительность последнего этапа — это и есть
     * требование «курс не короче схемы»: назначение, которое не помещается в курс, скорее
     * всего недонабрано или недопонято, и молча обрезать его по дате окончания нельзя.
     */
    data object ScheduleStagesBeyondCourse : DraftViolation {
        override val field: DraftField get() = DraftField.SCHEDULE
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

    /**
     * Заполненный размер упаковки не больше нуля (ИНВ-20).
     *
     * Единственное правило размера: пустое значение — «размер не задан», и оно
     * ошибкой не считается, даже если остаток при этом задан.
     */
    data object PackageSizeNotPositive : DraftViolation {
        override val field: DraftField get() = DraftField.PACKAGE_SIZE
    }
}
