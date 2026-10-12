package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate

/**
 * Состояние одного дня календаря (`TDD.md §5.3`).
 *
 * Правило живёт в домене, а не в разметке: «частично принятый день» — предметное
 * определение, и проверяется он тестом на JVM, а не разглядыванием календаря.
 *
 * Определение «частично» в спецификации не зафиксировано и предложено этой задачей:
 * день, в котором есть и принятые, и непринятые приёмы. Требует подтверждения вместе
 * с ОВ-12.
 */
object DayOutcomeRule {

    /**
     * @param intakesOfDay приёмы, назначенные на [date]; лишние даты отбрасывает вызывающий
     * @param historyStartsAt дата первой записи истории профиля; `null` — истории нет вовсе
     * @param today сегодняшняя дата из источника времени, а не из системных часов (НФТ-48)
     */
    fun state(intakesOfDay: List<Intake>, date: LocalDate, today: LocalDate, historyStartsAt: LocalDate?): DayState =
        when {
            // Дни до начала ведения истории рисуются нейтрально и не считаются пропусками
            // (`docs/norm/04-behavior.md`, состояние «Истории меньше месяца»). Проверка идёт
            // первой: приёмов там нет по природе, а не по недосмотру, и подменять причину
            // на «день без приёмов» значило бы потерять её в сводке.
            historyStartsAt == null || date < historyStartsAt -> DayState.BEFORE_HISTORY

            intakesOfDay.isEmpty() -> DayState.NO_INTAKES

            // Сутки, которые ещё не кончились, исхода не имеют (ЭКР-6): иначе каждое утро
            // календарь показывал бы пропуск там, где приём просто ещё не наступил.
            !date.isBefore(today) -> DayState.AHEAD

            intakesOfDay.all { it.status == IntakeStatus.TAKEN } -> DayState.ALL_TAKEN

            // Пропуск, отмеченный человеком, и пропуск автоматический в календаре не
            // различаются: приём не состоялся в обоих случаях.
            intakesOfDay.none { it.status == IntakeStatus.TAKEN } -> DayState.MISSED

            else -> DayState.PARTIAL
        }
}
