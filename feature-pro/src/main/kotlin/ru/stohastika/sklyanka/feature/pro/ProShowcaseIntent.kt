package ru.stohastika.sklyanka.feature.pro

import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProProductId

/**
 * События поверхности витрины (SKL-122).
 *
 * Экран не решает ничего сам: он передаёт намерение человека, а правила живут
 * в `ProSheetSession` (`docs/tasks/SKL-122/TDD.md` §2). Поэтому здесь нет ни «состояния
 * кнопки», ни «что делать после отказа».
 */
sealed interface ProShowcaseIntent {

    /** Открыть витрину из точки выбора платной функции (Р-67 п. 2). */
    data class Open(val point: ProPoint) : ProShowcaseIntent

    /** Выбрать период: месяц или год (Р-68 п. 1). */
    data class SelectPeriod(val period: ProProductId) : ProShowcaseIntent

    /** «Оформить» — согласие даётся самим нажатием (Р-73 п. 2). */
    data object Buy : ProShowcaseIntent

    /** «Восстановить покупку» (Р-67 п. 4). */
    data object Restore : ProShowcaseIntent

    /**
     * «Управление подпиской» и «Отменить подписку» — обе ведут в магазин (Р-73 п. 1).
     *
     * Это **единственное** действие, ведущее в магазин: «Условия и политика» — документ,
     * а не шаг покупки (§5.9), и у него своё намерение ниже.
     */
    data object Manage : ProShowcaseIntent

    /**
     * «Условия и политика» — открыть документ (ОВ-123).
     *
     * Открывает его тот, кто собирает маршруты, то есть `app`: у экрана нет ни браузера,
     * ни адреса, и подменять документ экраном магазина нельзя — человек шёл за текстом (П-7).
     */
    data object OpenPolicy : ProShowcaseIntent

    /** «Подробнее» — развернуть обязательства магазина в той же шторке (Р-76 п. 3). */
    data object ExpandDisclosure : ProShowcaseIntent

    /** Системный «Назад» и тап по затемнению (Р-54). */
    data object Dismiss : ProShowcaseIntent
}
