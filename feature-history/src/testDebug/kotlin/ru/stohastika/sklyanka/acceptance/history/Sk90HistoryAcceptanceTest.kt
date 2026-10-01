package ru.stohastika.sklyanka.acceptance.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk90HistoryAcceptanceTest {

    @Test
    fun theHistoryHeaderScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN and $HEADER"))
    }

    @Test
    fun theDoctorReportSheetScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN and $REPORT"))
    }

    private companion object {
        const val TASK = "SKL-90"
        const val SCREEN = "@screen"
        const val HEADER = "@layout-history"
        const val REPORT = "@layout-report"
    }
}
