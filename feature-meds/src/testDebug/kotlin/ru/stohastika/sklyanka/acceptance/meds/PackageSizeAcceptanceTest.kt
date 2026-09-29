package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки размера упаковки (SKL-34, группа `@screen`).
 *
 * Модуль свой: сценарии проверяют строку формы и строку «N из M» с полосой в карточке,
 * а оба экрана живут в `feature-meds` (`SKL-26/TDD.md §4.3`). Форма длиннее окна
 * телефона, карточка — тем более: высота стенда взята с запасом.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class PackageSizeAcceptanceTest {

    @Test
    fun theScreenScenariosOfThePackageSizeRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-34"
        const val SCREEN_TAG = "@screen"
    }
}
