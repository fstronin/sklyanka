package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки настроек (SKL-26, группа `@screen` файла SKL-20).
 *
 * Модуль свой: экран настроек живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Из прогона исключены три группы, и у каждой своя область видимости:
 * сценарии диагностики исполняет `feature-reminder` (там живёт её экран), сценарий
 * единственного входа в настройки — `feature-today` (экран «Сегодня» и его панель),
 * композиционные сценарии — `app`.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: плашка о приложении и нижняя панель иначе не собираются,
// и проверка видела бы не экран, а его верх. Язык — часть условий: сценарии приёмки
// записаны по-русски, и склонения на английской локали стенда читаются не тем, что
// в документе.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SettingsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSettingsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-20"
        const val TAGS = "@screen and not @composition and not @diagnostics and not @defect"
    }
}
