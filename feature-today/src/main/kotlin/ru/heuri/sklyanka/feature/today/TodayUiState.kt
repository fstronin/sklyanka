package ru.heuri.sklyanka.feature.today

import androidx.compose.runtime.Immutable
import ru.heuri.sklyanka.domain.today.DayProgress
import ru.heuri.sklyanka.domain.today.DaySection
import ru.heuri.sklyanka.domain.today.PartOfDay
import ru.heuri.sklyanka.domain.today.PrnEntry
import ru.heuri.sklyanka.domain.today.StockNotice
import ru.heuri.sklyanka.domain.today.TimeGroup
import ru.heuri.sklyanka.domain.today.TodayLayout
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние экрана «Сегодня» — единственный источник правды разметки.
 *
 * Состояний «загрузка» и «ошибка» здесь нет намеренно (`TDD.md §5.5`): данные
 * локальные, скелет загрузки не показывается, а отказ чтения базы — аварийная
 * ситуация, а не экранное состояние. [Opening] — не загрузка: это первый кадр до
 * первого значения потока, в нём не рисуется ничего, а не крутится индикатор.
 */
sealed interface TodayUiState {

    /** Поток ещё не выдал ни одного значения. Живёт один кадр. */
    data object Opening : TodayUiState

    /**
     * У профиля нет ни одного активного лекарства — `Empty.dc.html`.
     *
     * Именно «нет лекарств», а не «нет приёмов на сегодня»: второму случаю этот
     * экран сообщал бы неправду (`docs/spec/03-screens.md`, ОВ-34).
     */
    data class Empty(val date: LocalDate) : TodayUiState

    @Immutable
    data class Content(
        val date: LocalDate,
        /**
         * Точка расширения SKL-15.
         *
         * Вводится сразу, хотя в этой задаче всегда означает свой профиль: SKL-15
         * добавляет шесть признаков чужого профиля одновременно, и если акцент
         * останется константой в каждом компоненте, задача профилей превратится
         * в правку каждого места (ПРФ-11, ПРФ-16).
         */
        val isForeignProfile: Boolean,
        val profileAccent: ProfileAccent,
        val progress: DayProgress,
        val stockWarning: StockNotice?,
        val layout: TodayLayout,
        val prn: List<PrnEntry>,
        /**
         * Секции, состояние которых человек изменил рукой.
         *
         * Живёт в состоянии экрана, а не в локальном состоянии Compose: иначе оно
         * теряется при пересборке состояния после отметки, и группа схлопывается
         * под пальцем (`TDD.md §5.2`). Множество хранит **переключения**, а не
         * «развёрнутые»: начальное состояние секции задаёт домен, а человек его
         * обращает.
         */
        val toggledSections: Set<PartOfDay>,
        /** Группы, развёрнутые кнопкой «По одному»: групповая карточка стала списком. */
        val expandedGroups: Set<LocalTime>,
        /** Блок «по потребности» в плотном дне свёрнут такой же секцией. */
        val prnCollapsed: Boolean,
        /** Лекарства есть, а приёмов на сегодня нет (ОВ-34). */
        val nothingToday: Boolean,
    ) : TodayUiState {

        /** Плотный день показывает секции по времени суток, обычный — ленты подряд. */
        val isDense: Boolean get() = layout is TodayLayout.Dense

        /** Начальное состояние секции, обращённое ручным переключением. */
        fun isExpanded(section: DaySection): Boolean =
            section.state.isExpanded != (section.partOfDay in toggledSections)

        /** Групповая карточка развёрнута в отдельные строки кнопкой «По одному». */
        fun isGroupExpanded(group: TimeGroup): Boolean = group.time in expandedGroups
    }
}
