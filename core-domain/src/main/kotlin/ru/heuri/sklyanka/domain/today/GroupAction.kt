package ru.heuri.sklyanka.domain.today

/**
 * Форма подписи кнопки групповой отметки.
 *
 * Форма, а не готовая строка: текст собирается из ресурсов в `core-ui`/`feature-*`,
 * а русские «оба» и «все три» механически из числительных форм `plurals` не выводятся
 * — на каждую нужен отдельный ресурс (НФТ-44, `TDD.md §5.3`).
 */
enum class GroupLabelForm {
    /** «Отметить оба» — два одновременных приёма. */
    BOTH,

    /** «Отметить все три». */
    ALL_THREE,

    /** «Отметить все N» — больше трёх. */
    ALL_N,
    ;

    companion object {
        private const val BOTH_COUNT = 2
        private const val ALL_THREE_COUNT = 3

        fun of(count: Int): GroupLabelForm = when (count) {
            BOTH_COUNT -> BOTH
            ALL_THREE_COUNT -> ALL_THREE
            else -> ALL_N
        }
    }
}

/**
 * Групповая отметка одновременных приёмов (М-08-1, решение ОВ-32).
 *
 * Выдаётся только для группы, в которой ещё есть что отмечать и приёмов больше одного.
 * Отказ от групповой отметки — это невыдача [GroupAction], а не правка разметки:
 * карточка тогда вырождается в список строк с индивидуальными кнопками, ровно как
 * требует НАП-36 для напоминаний (`TDD.md §5.3`).
 */
data class GroupAction(
    /** Только ожидающиеся приёмы: уже отмеченные в групповую операцию не входят (ПР-2). */
    val markableIntakeIds: List<String>,
    val labelForm: GroupLabelForm,
) {
    val count: Int get() = markableIntakeIds.size

    companion object {

        /** Группа из одного отмечаемого приёма групповой кнопки не получает. */
        private const val MIN_GROUP_SIZE = 2

        fun of(group: TimeGroup): GroupAction? {
            val markable = group.markable
            if (markable.size < MIN_GROUP_SIZE) return null
            return GroupAction(
                markableIntakeIds = markable.map { it.id },
                labelForm = GroupLabelForm.of(markable.size),
            )
        }
    }
}
