package ru.stohastika.sklyanka.acceptance.shortcut

import org.junit.Assert.assertNull
import org.junit.Test
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки ярлыка «Добавить лекарство» (SKL-80, группа `@screen`).
 *
 * Прогон — обычный JVM-тест без Robolectric: сценарии проверяют маршрут запуска и стек
 * раздела (`RootNavigation`, `MedsStack`), а не собранный экран, и Android им не нужен.
 * Так прогон не поднимает имитацию Android ради проверки двух чистых классов.
 */
class AddMedicationShortcutAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl80RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-80"
    }
}
