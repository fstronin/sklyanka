package ru.stohastika.sklyanka.startup

import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.MigrationCopyBudget

/**
 * Почему приложение не открывается, хотя установка исправна (`ADR-0012` пп. 3, 7).
 *
 * Причин две, и обе — не ошибка человека: [TooNew] — база создана более новой сборкой;
 * [NotEnoughSpace] — на копию перед миграцией не хватает места. Отличать их обязательно:
 * «обновите приложение» и «освободите место» — разные действия, и совет не по причине
 * человек выполнить не может.
 *
 * Правило перевода состояния в блок живёт здесь одним местом: и старт, и будильник судят
 * по одному и тому же состоянию базы, а второй перевод неизбежно разошёлся бы с первым.
 */
sealed interface DatabaseBlock {

    /** База новее установленной сборки: остаётся обновиться (SKL-128). */
    data object TooNew : DatabaseBlock

    /**
     * Места на копию перед миграцией не хватает ([MigrationCopyBudget.missingMegabytes]
     * и есть то число, которое видит человек).
     */
    data class NotEnoughSpace(val budget: MigrationCopyBudget) : DatabaseBlock

    companion object {

        /** Блок для состояния базы; `null` — обычная работа. */
        fun of(state: DatabaseState): DatabaseBlock? = when (state) {
            DatabaseState.Ready -> null
            DatabaseState.Newer -> TooNew
            is DatabaseState.NotEnoughSpace -> NotEnoughSpace(state.budget)
        }
    }
}
