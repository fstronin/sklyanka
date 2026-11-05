package ru.stohastika.sklyanka.domain.widget

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Правило завершения установки виджета (ПР-23, §5.12).
 *
 * Доступа к «Про» у правила больше нет: виджеты бесплатны (Р-86). Закрывает установку
 * только невыбранное лекарство у типа «по лекарству».
 */
class WidgetSetupGateTest {

    @Test
    fun `a type without a choice completes`() {
        assertTrue(
            WidgetSetupGate.canComplete(medicationRequired = false, medicationChosen = false),
        )
    }

    @Test
    fun `the medication type waits for its choice`() {
        assertFalse(
            WidgetSetupGate.canComplete(medicationRequired = true, medicationChosen = false),
        )
        assertTrue(
            WidgetSetupGate.canComplete(medicationRequired = true, medicationChosen = true),
        )
    }
}
