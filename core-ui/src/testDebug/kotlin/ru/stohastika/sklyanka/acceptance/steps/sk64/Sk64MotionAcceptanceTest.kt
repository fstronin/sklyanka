package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk64MotionAcceptanceTest {

    @Test
    fun theTimingScenariosRunOnTheStand() {
        assertNull(Sk64AcceptanceRun.run(tags = "@screen and @motion-timing"))
    }

    @Test
    fun theSecondLevelScenariosRunOnTheStand() {
        assertNull(Sk64AcceptanceRun.run(tags = "@screen and @second-level"))
    }

    @Test
    fun theAdjacentScenariosRunOnTheStand() {
        assertNull(Sk64AcceptanceRun.run(tags = "@screen and @adjacent"))
    }
}
