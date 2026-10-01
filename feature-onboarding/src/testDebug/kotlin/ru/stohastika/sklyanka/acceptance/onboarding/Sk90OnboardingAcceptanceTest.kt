package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class Sk90OnboardingAcceptanceTest {

    @Test
    fun theStepCardScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN and $STEP_CARD"))
    }

    private companion object {
        const val TASK = "SKL-90"
        const val SCREEN = "@screen"
        const val STEP_CARD = "@layout-onboarding"
    }
}
