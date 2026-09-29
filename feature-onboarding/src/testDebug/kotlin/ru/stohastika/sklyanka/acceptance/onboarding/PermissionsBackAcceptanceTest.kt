package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки системной «Назад» (SKL-40, группа `@screen`).
 *
 * Модуль свой: «Назад» — обработчик маршрута экрана разрешений, и шаги обязаны видеть
 * собранную композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Экран с тремя карточками и нижними кнопками: без запаса по высоте действия не собираются.
// Язык — часть условий: сценарии приёмки записаны по-русски.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class PermissionsBackAcceptanceTest {

    @Test
    fun theBackScenariosOfThePermissionsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-40"
        const val TAGS = "@screen"
    }
}
