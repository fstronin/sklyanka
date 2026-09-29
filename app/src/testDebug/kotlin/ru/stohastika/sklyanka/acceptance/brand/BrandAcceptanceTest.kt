package ru.stohastika.sklyanka.acceptance.brand

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.acceptance.settings.SettingsAppProbe

/**
 * Экранный прогон приёмки знака, иконки и заставки (SKL-21, группа `@composition`).
 *
 * Один прогон на всю задачу: сценариям нужны и компоненты `core-ui`, и ресурсы иконки,
 * и решение запуска, и экраны, где знак стоит иллюстрацией, — всё это видит только `app`
 * (`SKL-26/TDD.md §4.3`). Отдельный прогон в `core-ui` невозможен: Предыстория файла
 * («сейчас…», «релизная сборка…», «системная тема…») объявлена общим словарём, которого
 * `core-ui` не видит по `ModuleGraphRules`, а её второе объявление погасило бы прогон.
 *
 * Группа `@screen` печатается задачей `acceptanceScreenList`; `@manual` остаётся
 * чек-листом устройства (маски иконки, шторка, время холодного старта).
 */
@RunWith(AndroidJUnit4::class)
// Светлая системная тема Предыстории, русский язык и запас по высоте: «О приложении»
// и нижняя панель иначе не собираются, а тексты на чужой локали читались бы не тем.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class BrandAcceptanceTest {

    @Test
    fun theBrandScenariosRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            SettingsAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-21"
        const val TAGS = "@composition"
    }
}
