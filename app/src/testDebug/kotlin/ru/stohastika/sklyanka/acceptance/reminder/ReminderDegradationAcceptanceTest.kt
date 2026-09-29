package ru.stohastika.sklyanka.acceptance.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки деградации напоминаний (SKL-13, группа `@degradation` с `@screen`).
 *
 * Модуль — `app`: сценарий говорит «ни один экран приложения не становится недоступным»,
 * а шесть его экранов живут в разных модулях (`SKL-26/TDD.md §4.3`). Два сценария той же
 * группы без `@screen` остаются нереализованными намеренно: предупреждение о ненадёжности
 * на экране «Сегодня» принадлежит чужому модулю и решением SKL-13 не делается.
 */
@RunWith(AndroidJUnit4::class)
// Шесть экранов собираются целиком: форме лекарства и карточке нужен запас по высоте.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderDegradationAcceptanceTest {

    @Test
    fun theDegradationScenariosOfTheReminderRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $DEGRADATION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-13"
        const val SCREEN_TAG = "@screen"
        const val DEGRADATION_TAG = "@degradation"
    }
}
