package ru.stohastika.sklyanka.acceptance.steps.sk93

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class Sk93GlyphAndStringsAcceptanceTest {

    @Test
    fun theGlyphScenariosRunOnTheStand() {
        assertNull(Sk93AcceptanceRun.run(tags = "@screen and @layout-glyph"))
    }

    @Test
    fun theLocalizationScenariosRunOnTheStand() {
        assertNull(Sk93AcceptanceRun.run(tags = "@screen and @layout-i18n"))
    }
}
