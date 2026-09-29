package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки снэкбара отмены на «Сегодня» (SKL-30, 13 сценариев).
 *
 * Модуль свой: «Сегодня» живёт здесь, и шаг о составе снэкбара обязан видеть собранный
 * экран и его ресурсы (`SKL-26/TDD.md §4.3`). Сценарии карточки напоминания
 * (`@reminder-screen`) исполняет прогон `feature-reminder`, сценарии композиции
 * карточки и «Сегодня» (`@composition`) — прогон `app`: каждому — там, где видны
 * его экраны. Вместе три прогона исполняют все `@screen`-сценарии задачи ровно по разу
 * (`TDD.md §12.2`).
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: блок «По потребности» и нижние ленты иначе не собираются,
// а снэкбар не находит нижнюю панель; язык — часть условий: сценарии записаны по-русски,
// и склонения счётных форм читаются не на английской локали стенда.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodayUndoAcceptanceTest {

    @Test
    fun theUndoSnackbarScenariosOfTodayRunOnTheStand() {
        assertNull(
            ScreenAcceptanceRun.run(
                task = TASK,
                tags = "$SCREEN_TAG and not $COMPOSITION_TAG and not $REMINDER_TAG",
            ),
        )
    }

    private companion object {
        const val TASK = "SKL-30"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
        const val REMINDER_TAG = "@reminder-screen"
    }
}
