package ru.stohastika.sklyanka.domain.widget

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Правило завершения установки виджета (Р-32, ПР-23, §5.12).
 */
class WidgetSetupGateTest {

    @Test
    fun `without access the setup never completes`() {
        assertFalse(
            WidgetSetupGate.canComplete(proIncluded = false, medicationRequired = false, medicationChosen = false),
        )
        assertFalse(
            WidgetSetupGate.canComplete(proIncluded = false, medicationRequired = true, medicationChosen = true),
        )
    }

    @Test
    fun `with access a type without a choice completes`() {
        assertTrue(
            WidgetSetupGate.canComplete(proIncluded = true, medicationRequired = false, medicationChosen = false),
        )
    }

    @Test
    fun `with access the medication type waits for its choice`() {
        assertFalse(
            WidgetSetupGate.canComplete(proIncluded = true, medicationRequired = true, medicationChosen = false),
        )
        assertTrue(
            WidgetSetupGate.canComplete(proIncluded = true, medicationRequired = true, medicationChosen = true),
        )
    }
}
