package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-105 на стенде настроек.
 *
 * Модуль свой: сценарии задачи говорят о блоке «Профили» и о слове-подтверждении удаления
 * профиля, а это экраны `feature-settings`, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Из прогона SKL-105 берутся ровно экранные сценарии этой задачи — тег `@settings`: раздел
 * «Профили» и подтверждение словом. Остальные группы (`@history`, `@meds`, `@ui`, `@nav`,
 * `@today`) исполняют модули своих экранов.
 *
 * Язык — часть условий: сценарии приёмки записаны по-русски, и склонения на английской
 * локали стенда читались бы не тем, что в документе.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class Sk105SettingsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSk105RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            SettingsScreenProbe.dropMainDispatcher()
            DeleteProfileScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-105"
        const val TAGS = "@screen and @settings"
    }
}
