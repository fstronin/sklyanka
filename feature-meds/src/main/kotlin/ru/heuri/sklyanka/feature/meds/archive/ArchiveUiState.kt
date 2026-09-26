package ru.heuri.sklyanka.feature.meds.archive

import androidx.compose.runtime.Immutable
import ru.heuri.sklyanka.domain.medication.ArchiveContent

/**
 * Состояние вкладки «Архив» экрана «Лекарства» (`design/screens/Archive.dc.html`).
 *
 * Оба счётчика живут в одном значении: «Активные · 5» и «Архив · 12» пересчитываются
 * одним значением потока, иначе они разойдутся на одно нажатие «Вернуть».
 *
 * Состояний «загрузка» и «ошибка» нет: данные локальные, отказ чтения базы — аварийная
 * ситуация, а не экранное состояние. [loading] существует лишь затем, чтобы не показать
 * пустой архив раньше, чем он прочитан.
 */
@Immutable
data class ArchiveUiState(
    /** «Активные · 5» — только курсы по расписанию (ЭКР-3). */
    val activeCount: Int = 0,
    /** «Архив · 12» — обе причины архивации. */
    val archiveCount: Int = 0,
    /** `null` — архив пуст: сегмент остаётся доступным, вместо списка идёт пояснение. */
    val content: ArchiveContent? = null,
    val notice: ArchiveNotice? = null,
    val loading: Boolean = true,
)

/** То, что архив обязан сказать человеку вслух, а не проглотить. */
@Immutable
sealed interface ArchiveNotice {

    /**
     * Возврат не состоялся: дата окончания курса в прошлом (ОВ-14).
     *
     * Молчаливого возврата курса, не порождающего ни одного приёма, не происходит:
     * человек нажал «Вернуть» и получает объяснение, а не тишину.
     */
    @Immutable
    data object PeriodNeedsUpdate : ArchiveNotice
}
