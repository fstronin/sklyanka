package ru.stohastika.sklyanka.domain.today

import java.time.LocalDateTime

/**
 * Выбор вида экрана: обычный день или плотный со свёрткой по времени суток.
 *
 * Порог — параметр правила, а не константа по месту (ОВ-33). В макетах он не задан,
 * `docs/norm/04-behavior.md` предлагает «больше десяти приёмов в сутки», и значение
 * почти наверняка будет уточняться по живым данным. Константа в разметке сделала бы
 * уточнение правкой экрана.
 *
 * Начальное состояние секций выводится из времени: прошедшее время суток свёрнуто с
 * итогом, текущее развёрнуто со счётчиком, будущее свёрнуто с числом приёмов впереди.
 * Ручное разворачивание здесь не живёт — оно часть состояния экрана (`TDD.md §5.2`).
 */
class DenseLayoutRule(private val threshold: Int = DEFAULT_THRESHOLD) {

    operator fun invoke(groups: List<TimeGroup>, now: LocalDateTime): TodayLayout {
        val total = groups.sumOf { it.items.size }
        return if (total > threshold) TodayLayout.Dense(sections(groups, now)) else TodayLayout.Plain(groups)
    }

    private fun sections(groups: List<TimeGroup>, now: LocalDateTime): List<DaySection> {
        val current = PartOfDay.of(now.toLocalTime())
        return groups
            .groupBy { it.partOfDay }
            .toSortedMap()
            .map { (partOfDay, sectionGroups) ->
                DaySection(
                    partOfDay = partOfDay,
                    state = stateOf(partOfDay, current, sectionGroups, now),
                    groups = sectionGroups,
                )
            }
    }

    /** Текущее время суток развёрнуто, остальные свёрнуты с подходящим итогом. */
    private fun stateOf(
        partOfDay: PartOfDay,
        current: PartOfDay,
        groups: List<TimeGroup>,
        now: LocalDateTime,
    ): SectionState =
        if (partOfDay == current) SectionState.Expanded else SectionState.Collapsed(summaryOf(groups, now))

    /**
     * Итог свёрнутой секции.
     *
     * «Впереди» решается по времени приёмов, а не по их статусу: секция, в которой
     * ничего не наступило, впереди целиком, даже если один приём уже отмечен досрочно
     * круглой кнопкой-галочкой.
     */
    private fun summaryOf(groups: List<TimeGroup>, now: LocalDateTime): SectionSummary {
        val items = groups.flatMap { it.items }
        return when {
            items.all { !it.isMarkable } -> SectionSummary.ALL_MARKED
            items.all { it.intake.scheduledAt.isAfter(now) } -> SectionSummary.AHEAD
            else -> SectionSummary.PARTIAL
        }
    }

    companion object {

        /**
         * Предложение `docs/norm/04-behavior.md`: свёртка включается, когда приёмов в
         * сутки **больше** десяти. Ровно десять — ещё обычный день.
         */
        const val DEFAULT_THRESHOLD: Int = 10
    }
}
