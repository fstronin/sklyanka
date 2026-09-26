package ru.heuri.sklyanka.domain.today

/**
 * Два способа показать сутки: плоский список лент и свёртка по времени суток.
 *
 * Запечатанный тип, а не флаг «плотный день»: у видов разный состав (у плотного есть
 * секции, у обычного их нет), и флаг заставил бы разметку держать оба набора данных
 * сразу, из которых половина всегда пуста.
 */
sealed interface TodayLayout {

    /** Группы времени в порядке возрастания — то, что видно на экране. */
    val groups: List<TimeGroup>

    val isEmpty: Boolean get() = groups.isEmpty()

    /** Обычный день (`Main.dc.html`): ленты приёмов подряд. */
    data class Plain(override val groups: List<TimeGroup>) : TodayLayout

    /** Плотный день (`MainDense.dc.html`): секции по времени суток. */
    data class Dense(val sections: List<DaySection>) : TodayLayout {
        override val groups: List<TimeGroup> get() = sections.flatMap { it.groups }
    }
}

/**
 * Секция плотного дня: «Утро · 07:00 — 11:00» и её содержимое.
 *
 * [state] — только начальное состояние, выведенное из времени: прошедшее свёрнуто,
 * текущее развёрнуто, будущее свёрнуто. Ручное разворачивание живёт в состоянии
 * экрана, а не здесь: иначе оно сбрасывалось бы при каждой отметке (`TDD.md §5.2`).
 */
data class DaySection(val partOfDay: PartOfDay, val state: SectionState, val groups: List<TimeGroup>) {
    val items: List<IntakeItem> get() = groups.flatMap { it.items }

    /** Всего приёмов в секции — первое число счётчика «3 из 9». */
    val total: Int get() = items.size

    /** Сколько уже отмечено: принято, пропущено осознанно или пропущено без отметки. */
    val marked: Int get() = items.count { !it.isMarkable }
}

/** Свёрнута секция или развёрнута — и что написано в её подписи. */
sealed interface SectionState {

    data class Collapsed(val summary: SectionSummary) : SectionState

    data object Expanded : SectionState

    val isExpanded: Boolean get() = this is Expanded
}

/**
 * Итог свёрнутой секции.
 *
 * Три вида, а не два из макета: макет рисует прошедшее утро, где отмечено всё, и
 * будущий вечер, где не наступило ничего. Прошедшее время суток с неотмеченными
 * приёмами существует ровно так же (человек проспал), и подписать его «все отмечены»
 * значило бы соврать, а «13 приёмов впереди» — соврать дважды.
 */
enum class SectionSummary {
    /** «8 приёмов, все отмечены». */
    ALL_MARKED,

    /** «13 приёмов впереди»: ни один приём секции ещё не наступил. */
    AHEAD,

    /** «отмечено 5 из 8»: время прошло, отмечено не всё. */
    PARTIAL,
}
