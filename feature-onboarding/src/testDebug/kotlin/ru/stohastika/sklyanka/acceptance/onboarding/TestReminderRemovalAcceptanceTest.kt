package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки удаления пробного напоминания (SKL-32, группа `@screen @onboarding`).
 *
 * Модуль свой: экран разрешений живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Экран с тремя карточками и нижними кнопками: без запаса по высоте действия не собираются.
// Язык — часть условий: сценарии приёмки записаны по-русски.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TestReminderRemovalAcceptanceTest {

    @Test
    fun theRemovalScenariosOfTheOnboardingRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-32"
        const val TAGS = "@screen and @onboarding"
    }
}
