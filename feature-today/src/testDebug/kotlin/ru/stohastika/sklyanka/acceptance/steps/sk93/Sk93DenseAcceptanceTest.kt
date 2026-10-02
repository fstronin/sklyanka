package ru.stohastika.sklyanka.acceptance.steps.sk93

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class Sk93DenseAcceptanceTest {

    @Test
    fun theDenseRowScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = "SKL-93", tags = "@screen and @layout-dense"))
    }
}
