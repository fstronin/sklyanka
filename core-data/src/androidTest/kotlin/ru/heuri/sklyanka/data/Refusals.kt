package ru.heuri.sklyanka.data

import org.junit.Assert.assertTrue

/**
 * Отказ, которого ждёт проверка, — без вложенного прогона сопрограмм.
 *
 * `runTest` внутри `runTest` запрещён самой библиотекой: вложенный вызов бросает
 * `IllegalStateException` раньше, чем дело дойдёт до проверяемого действия. Проверка,
 * написанная так, ждёт отказ базы, а получает отказ прогона — и либо падает не на своём
 * предмете, либо, если ждала как раз `IllegalStateException` или `Exception`, проходит,
 * не проверив ничего. Второе хуже: зелёная проверка, не касавшаяся предмета.
 *
 * Поэтому действие вызывается в текущей области сопрограмм, а тип отказа сверяется по факту.
 */
suspend inline fun <reified T : Throwable> assertRefused(crossinline action: suspend () -> Unit) {
    val thrown = runCatching { action() }.exceptionOrNull()
    assertTrue(
        "expected ${T::class.simpleName}, got ${thrown ?: "no failure at all"}",
        thrown is T,
    )
}
