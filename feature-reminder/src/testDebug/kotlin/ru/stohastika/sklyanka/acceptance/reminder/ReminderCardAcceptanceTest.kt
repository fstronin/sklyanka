package ru.stohastika.sklyanka.acceptance.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки полноэкранного напоминания (SKL-13, группа `@reminder-screen`).
 *
 * Модуль свой: экран напоминания живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Сценарий состава карточки на экране блокировки
 * помечен `@device` и остаётся вне JVM-прогонов.
 */
@RunWith(AndroidJUnit4::class)
// Экран напоминания высокий: действия стоят внизу, и на низком окне их не было бы видно.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderCardAcceptanceTest {

    @Test
    fun theCardScenariosOfTheReminderRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $CARD_TAG"))
        } finally {
            ReminderStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-13"
        const val SCREEN_TAG = "@screen"
        const val CARD_TAG = "@reminder-screen"
    }
}
