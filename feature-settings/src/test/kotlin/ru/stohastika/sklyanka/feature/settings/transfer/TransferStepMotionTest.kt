package ru.stohastika.sklyanka.feature.settings.transfer

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.ui.motion.LayerMotion

class TransferStepMotionTest {

    @Test
    fun `a later step goes forward`() {
        assertEquals(LayerMotion.Forward, transferStepMotion(TransferStep.OVERVIEW, TransferStep.SUMMARY))
        assertEquals(LayerMotion.Forward, transferStepMotion(TransferStep.SUMMARY, TransferStep.MATCHES))
        assertEquals(LayerMotion.Forward, transferStepMotion(TransferStep.MATCHES, TransferStep.REPLACE_CONFIRM))
        assertEquals(LayerMotion.Forward, transferStepMotion(TransferStep.REPLACE_CONFIRM, TransferStep.REPORT))
    }

    @Test
    fun `an earlier step comes back`() {
        assertEquals(LayerMotion.Back, transferStepMotion(TransferStep.SUMMARY, TransferStep.OVERVIEW))
        assertEquals(LayerMotion.Back, transferStepMotion(TransferStep.REPORT, TransferStep.OVERVIEW))
        assertEquals(LayerMotion.Back, transferStepMotion(TransferStep.MATCHES, TransferStep.SUMMARY))
    }

    @Test
    fun `the same step does not move`() {
        TransferStep.entries.forEach { step ->
            assertEquals(LayerMotion.Crossfade, transferStepMotion(step, step))
        }
    }
}
