package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class Sk90TransferHeaderAcceptanceTest {

    @Test
    fun theTransferHeaderScenarioRunsOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN and $HEADER"))
    }

    private companion object {
        const val TASK = "SKL-90"
        const val SCREEN = "@screen"
        const val HEADER = "@layout-transfer"
    }
}
