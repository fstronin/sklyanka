package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки экрана разрешений (SKL-14, группа `@screen`).
 *
 * Модуль свой: экран разрешений живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Из прогона исключены три группы, и у каждой своя область видимости: сценарий пробного
 * напоминания из диагностики исполняет `feature-reminder` (там живёт её экран), сводное
 * состояние строки настроек — `feature-settings`, первый запуск, пропуск и деградация —
 * `app` (там видны навигация и несколько экранов сразу).
 */
@RunWith(AndroidJUnit4::class)
// Экран с тремя карточками и нижними кнопками: без запаса по высоте действия не собираются,
// и проверка видела бы не экран, а его верх. Язык — часть условий: сценарии приёмки
// записаны по-русски, и тексты на английской локали стенда читались бы не тем, что в документе.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class PermissionsAcceptanceTest {

    @Test
    fun theScreenScenariosOfThePermissionsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-14"
        const val TAGS = "@screen and not @composition and not @diagnostics and not @settings"
    }
}
