package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки строки «Разрешения и автозапуск» по сценарию SKL-14 (группа `@settings`).
 *
 * Модуль свой: строка живёт на экране настроек, и шаг обязан видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Прогон SKL-20 исполняет свои сценарии настроек
 * тем же стендом — сценарии двух задач различаются файлами, а не механизмом.
 */
@RunWith(AndroidJUnit4::class)
// Экран настроек выше окна: строка разрешений и нижняя панель иначе не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class OnboardingSettingsAcceptanceTest {

    @Test
    fun theSettingsScenarioOfTheOnboardingRunsOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-14"
        const val TAGS = "@screen and @settings"
    }
}
