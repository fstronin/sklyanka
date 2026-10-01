package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки выключенной строки «Добавить профиль» (SKL-58, группа `@screen`).
 *
 * Строка живёт на экране настроек, и шаги обязаны видеть собранную композицию и ресурсы
 * `feature-settings` (`SKL-26/TDD.md §4.3`). Сценарий `@manual @device` сюда не попадает:
 * его тег не `@screen`, и он остаётся пунктом ручной проверки на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: плашка о приложении и нижняя панель иначе не собираются.
// Язык — часть условий: подписи и дата читаются с ресурсов русской локали.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SettingsAddProfileAcceptanceTest {

    @Test
    fun theDisabledRowScenariosRunOnTheScreenStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-58"
    }
}
