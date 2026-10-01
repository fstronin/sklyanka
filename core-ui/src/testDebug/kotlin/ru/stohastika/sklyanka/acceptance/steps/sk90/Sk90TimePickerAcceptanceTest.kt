package ru.stohastika.sklyanka.acceptance.steps.sk90

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class Sk90TimePickerAcceptanceTest {

    @Test
    fun theTimePickerScenariosRunOnTheStand() {
        assertNull(Sk90AcceptanceRun.run(tags = "@screen and @layout-picker"))
    }
}
