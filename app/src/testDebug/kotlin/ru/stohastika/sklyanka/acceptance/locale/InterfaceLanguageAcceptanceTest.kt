package ru.stohastika.sklyanka.acceptance.locale

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки языка интерфейса (SKL-28, группа `@screen`).
 *
 * Квалификатор задаёт **незнакомый приложению** язык телефона (`fr-FR`): объявленные
 * языки теперь законно переводятся (SKL-62), и запасное русское поведение SKL-28
 * проверяется на языке, которого в приложении нет.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr-rFR-w390dp-h2400dp-mdpi")
class InterfaceLanguageAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheInterfaceLanguageRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-28"
    }
}
