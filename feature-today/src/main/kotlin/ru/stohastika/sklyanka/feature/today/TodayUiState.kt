package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.today.DayProgress
import ru.stohastika.sklyanka.domain.today.DaySection
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.PrnEntry
import ru.stohastika.sklyanka.domain.today.StockNotice
import ru.stohastika.sklyanka.domain.today.TodayLayout
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние экрана «Сегодня» — единственный источник правды разметки.
 *
 * Состояний «загрузка» и «ошибка» здесь нет намеренно (`TDD.md §5.5`): данные
 * локальные, скелет загрузки не показывается, а отказ чтения базы — аварийная
 * ситуация, а не экранное состояние. [Opening] — не загрузка: это первый кадр до
 * первого значения потока, в нём не рисуется ничего, а не крутится индикатор.
 *
 * **Полей `isForeignProfile` и `profileAccent` здесь больше нет** (SKL-15). SKL-08 завела
 * их как точку расширения, ожидая, что оформление профиля будет приходить в состояние
 * экрана. Оказалось наоборот: индикация обязана быть на **всех** экранах в контексте
 * профиля (ПРФ-12), и её место — корень приложения (`ProfileChrome`), а не состояние
 * каждого экрана. Состояние экрана, несущее оформление, — это одиннадцать состояний,
 * в каждом из которых оформление можно забыть; в корне забыть нечего. Признак «чужой
 * профиль» экран вообще не хранит: он его не спрашивает, потому что ни одно его решение
 * от профиля не зависит.
 */
/**
 * Последнее действие, которое ещё можно отменить (SKL-30).
 *
 * Готовый к показу факт, а не магазин и не срок: срок — забота таймера `TodayViewModel`,
 * а разметке нужен только текст снэкбара и идентификаторы для нажатия «Отменить».
 */
@Immutable
data class UndoNotice(
    val intakeIds: List<String>,
    val result: MarkResult,
    /** Имена для текста; у действия из нескольких приёмов текст счётный, имена не читаются. */
    val medicationNames: List<String>,
)

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
        val progress: DayProgress,
        val stockWarning: StockNotice?,
        /**
         * Причина ненадёжности напоминаний; `null` — выдано всё (НАП-18, НАП-21).
         *
         * Состояние читается из системы при каждом построении экрана, а не хранится
         * флагом (НФТ-52): отозванное разрешение обязано отразиться без перезапуска.
         * Несётся свёрнутая причина, а не два флага: разметке нужен текст, а не правило
         * «какая причина» — его уже решил домен (`ReminderReliability.warning`).
         */
        val reliability: ReminderWarning?,
        /**
         * Последнее действие и его окно отмены; `null` — отменять нечего.
         *
         * Та же форма, что у баннера надёжности: разметке нужен готовый факт. `expiresAt`
         * сюда не попадает — срок живёт в таймере `TodayViewModel`, а не в состоянии.
         */
        val undo: UndoNotice? = null,
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
        /**
         * Группа, у которой батч-кнопка показывает вопрос; `null` — подтверждения нет.
         *
         * Живёт рядом с [toggledSections] и по той же причине: подтверждение обязано
         * пережить пересборку повестки после отметки и не смеет жить в локальном
         * состоянии Compose, где оно терялось бы на первом же обновлении данных.
         * Срок — забота таймера `TodayViewModel`; разметке нужен только факт
         * «какая группа ждёт подтверждения».
         */
        val confirmingGroup: LocalTime? = null,
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
    }
}
