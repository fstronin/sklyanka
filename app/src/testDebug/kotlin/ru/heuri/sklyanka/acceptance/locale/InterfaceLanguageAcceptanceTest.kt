package ru.heuri.sklyanka.acceptance.locale

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки языка интерфейса (SKL-28, группа `@screen`).
 *
 * Квалификатор задаёт **чужой** язык телефона — то, чего не делает ни один другой экранный
 * прогон: все они идут с русским телефоном (`ProjectDefaults.SCREEN_TEST_QUALIFIERS`),
 * и потому дефект был им невидим.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h2400dp-mdpi")
class InterfaceLanguageAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheInterfaceLanguageRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-28"
    }
}
