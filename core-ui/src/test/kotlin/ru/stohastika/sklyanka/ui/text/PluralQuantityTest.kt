package ru.stohastika.sklyanka.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Правило количества для формы слова (`docs/norm/06-design.md` §5.3).
 *
 * Границы проверяются числами, которые человек и вводит: целые (в том числе ноль,
 * одиннадцать и двадцать одно — у них разные формы количества) и дробные
 * («0,5 таблетки» — форма дроби).
 */
class PluralQuantityTest {

    @Test
    fun aWholeNumberIsItsOwnQuantity() {
        assertEquals(0, pluralQuantityOf(BigDecimal("0")))
        assertEquals(1, pluralQuantityOf(BigDecimal("1")))
        assertEquals(2, pluralQuantityOf(BigDecimal("2")))
        assertEquals(5, pluralQuantityOf(BigDecimal("5")))
        assertEquals(11, pluralQuantityOf(BigDecimal("11")))
        assertEquals(21, pluralQuantityOf(BigDecimal("21")))
    }

    /** Нули после запятой дробью не делают: «30.0» — тридцать, а не тридцать с половиной. */
    @Test
    fun trailingZerosDoNotMakeAFraction() {
        assertEquals(30, pluralQuantityOf(BigDecimal("30.0")))
        assertEquals(30, pluralQuantityOf(BigDecimal("30.00")))
    }

    @Test
    fun aFractionTakesTheQuantityOfTwo() {
        assertEquals(2, pluralQuantityOf(BigDecimal("0.5")))
        assertEquals(2, pluralQuantityOf(BigDecimal("1.5")))
        assertEquals(2, pluralQuantityOf(BigDecimal("2.5")))
    }
}
