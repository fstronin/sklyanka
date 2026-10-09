package ru.stohastika.sklyanka.acceptance.palette

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Экранный прогон приёмки SKL-109 — палитра «олива на бумаге» (Р-20).
 *
 * Сценарии говорят о свойствах цвета и о согласии кода с нормативом, а не о пикселях
 * экрана: палитра живёт в токенах, и проверять её надо там же, где она живёт. Кадры
 * экрана переснимаются отдельно — эталонами Roborazzi и контактными листами локалей.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk109PaletteAcceptanceTest {

    @Test
    fun thePaletteScenariosRunOnTheStand() {
        assertNull(Sk109AcceptanceRun.run())
    }
}
