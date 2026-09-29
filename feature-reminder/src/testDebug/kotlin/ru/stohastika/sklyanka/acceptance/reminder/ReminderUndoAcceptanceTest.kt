package ru.stohastika.sklyanka.acceptance.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки карточки напоминания в объёме SKL-30 (3 сценария).
 *
 * Модуль свой: карточка напоминания живёт здесь, и шаги обязаны видеть собранную
 * композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`). Сценарии «Сегодня» исполняет
 * прогон `feature-today`, сценарии композиции «карточка → Сегодня» — прогон `app`;
 * вместе три прогона исполняют все `@screen`-сценарии задачи ровно по разу.
 */
@RunWith(AndroidJUnit4::class)
// Экран напоминания высокий: действия стоят внизу, и на низком окне их не было бы видно.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderUndoAcceptanceTest {

    @Test
    fun theUndoScenariosOfTheReminderCardRunOnTheStand() {
        try {
            assertNull(
                ScreenAcceptanceRun.run(
                    task = TASK,
                    tags = "$SCREEN_TAG and $CARD_TAG and not $COMPOSITION_TAG",
                ),
            )
        } finally {
            ReminderStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-30"
        const val SCREEN_TAG = "@screen"
        const val CARD_TAG = "@reminder-screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
