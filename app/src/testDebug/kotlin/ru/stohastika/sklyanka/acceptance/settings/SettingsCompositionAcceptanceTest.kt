package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки композиции настроек (SKL-26, `@composition` файла SKL-20).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: сценарию переключения профиля нужны настройки
 * и «Сегодня» одновременно, а сценарию раздела «Данные» — договор навигации всего
 * приложения; оба видны сразу только здесь.
 *
 * Остальные `@screen`-сценарии SKL-20 исполняются прогонами своих модулей:
 * настройки — `feature-settings`, диагностика — `feature-reminder`, единственный вход
 * в настройки — `feature-today`.
 */
@RunWith(AndroidJUnit4::class)
// Экраны длиннее окна телефона: нижние строки настроек и карточки приёмов иначе
// не собираются. Язык — часть условий: склонения и подписи берутся из русских ресурсов.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SettingsCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfSettingsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
        } finally {
            SettingsAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-20"
        const val COMPOSITION_TAG = "@composition"
    }
}
