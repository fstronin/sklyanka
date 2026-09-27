package ru.heuri.sklyanka.domain.history

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import java.time.LocalDate
import java.time.YearMonth

/**
 * Окно расчёта истории: отрезок местного календаря, обе границы включительно.
 *
 * Отрезок дат, а не пара меток времени: в сутках с переводом времени 23 или 25 часов,
 * и арифметика «минус 30 суток» теряет или приобретает день (НФТ-37). Месяц как
 * единица расчёта задан макетом: переключатель листает месяцы, и сводка считается
 * на месяц целиком (`TDD.md §9.2`).
 */
data class HistoryWindow(val from: LocalDate, val to: LocalDate) {

    init {
        require(!to.isBefore(from)) { "history window ends before it starts: $from..$to" }
    }

    /** Все дни окна подряд — по дню на клетку календаря. */
    val dates: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    companion object {

        /** Месяц целиком: от первого до последнего числа. */
        fun of(month: YearMonth): HistoryWindow = HistoryWindow(month.atDay(1), month.atEndOfMonth())
    }
}

/**
 * Сырые данные истории за период — ровно то, что читает слой данных и считает домен.
 *
 * Одно значение, а не три параметра: профиль, приёмы и лекарства обязаны приходить
 * из одной выборки. Собранные из разных источников, они однажды разойдутся по профилю —
 * и это будет не ошибка экрана, а ложный отчёт врачу (ПР-10).
 */
data class HistorySlice(
    val profileId: String,
    /** Приёмы периода, включая приёмы архивированных курсов (ПР-11). */
    val intakes: List<Intake>,
    /**
     * Лекарства профиля **без фильтра архива** (ПР-11).
     *
     * Единственное место в приложении, где отсутствие фильтра архива правильно: архив
     * придуман ровно для того, чтобы завершённый курс остался в истории.
     */
    val medications: List<Medication>,
    /** Дата первой записи истории профиля; `null` — истории нет вовсе. */
    val historyStartsAt: LocalDate?,
) {

    companion object {

        fun empty(profileId: String): HistorySlice = HistorySlice(
            profileId = profileId,
            intakes = emptyList(),
            medications = emptyList(),
            historyStartsAt = null,
        )
    }
}
