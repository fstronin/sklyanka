package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk64ReminderAcceptanceTest {

    @Test
    fun theReminderScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = "SKL-64", tags = "@screen and @reminder-motion"))
    }
}
