package ru.stohastika.sklyanka.acceptance.locale.matrix

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон локализационной матрицы (SKL-91, группа `@screen`).
 *
 * Окно высокое намеренно: экраны компонуются по шесть языков друг под другом, и каждому
 * варианту нужен вьюпорт не меньше содержимого, чтобы Lazy-списки дорендерились целиком.
 * Язык вариантов задаётся не квалификатором, а контекстом в композиции: сценарии
 * называют языки словами, и прогон у них один.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h27000dp-mdpi")
class LocalizationMatrixAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheMatrixRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-91"
    }
}
