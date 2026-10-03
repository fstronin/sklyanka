package ru.stohastika.sklyanka.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.VariantKey

/**
 * Конвенция имён AGC Remote Configuration (SKL-83, TDD §10.1).
 *
 * Консоль обязана совпасть с этими именами; несовпадение не ломает продукт —
 * вариант не применяется, действует базовый, — и проверяется чек-листом устройства.
 */
class ExperimentCodesTest {

    @Test
    fun `the parameter name is derived from the experiment name`() {
        assertEquals(
            "experiment_today_empty_state",
            ExperimentCodes.parameterName(ExperimentId.TODAY_EMPTY_STATE),
        )
        assertEquals(
            "experiment_settings_transfer_hint",
            ExperimentCodes.parameterName(ExperimentId.SETTINGS_TRANSFER_HINT),
        )
    }

    @Test
    fun `console values map to the closed variant keys`() {
        assertEquals(VariantKey.BASE, ExperimentCodes.variantOf(ExperimentId.TODAY_EMPTY_STATE, "base"))
        assertEquals(VariantKey.A, ExperimentCodes.variantOf(ExperimentId.TODAY_EMPTY_STATE, "A"))
        assertEquals(VariantKey.A, ExperimentCodes.variantOf(ExperimentId.SETTINGS_TRANSFER_HINT, "a"))
    }

    @Test
    fun `an unknown value is no variant`() {
        assertNull(ExperimentCodes.variantOf(ExperimentId.TODAY_EMPTY_STATE, "holiday"))
        assertNull(ExperimentCodes.variantOf(ExperimentId.TODAY_EMPTY_STATE, ""))
    }

    @Test
    fun `a variant the experiment does not allow is no variant`() {
        assertNull(ExperimentCodes.variantOf(ExperimentId.TODAY_EMPTY_STATE, "b"))
        assertNull(ExperimentCodes.variantOf(ExperimentId.SETTINGS_TRANSFER_HINT, "c"))
    }
}
