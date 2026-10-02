package ru.stohastika.sklyanka.acceptance.locale

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки локализации (SKL-62, группа `@screen`).
 *
 * Квалификатор нейтрален: сценарии сами называют язык и собирают контекст продукта —
 * от языка телефона до выбора приложения. Проверяются шесть объявленных языков
 * и русское падение на незнакомом.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class LocalizationAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheLocalizationRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-62"
    }
}
