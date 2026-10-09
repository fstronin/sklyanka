package ru.stohastika.sklyanka.acceptance.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки плиток сводки истории (SKL-105, группа `@screen` файла SKL-105).
 *
 * Модуль свой: шаги говорят о собранной композиции истории и о её ресурсах, а это видно
 * только здесь (`SKL-26/TDD.md §4.3`). Теги выбраны так, чтобы прогон был один и точный:
 * сценарии этой задачи о плитках сводки несут `@history` и `@screen` и больше нигде
 * не исполняются — остальные группы SKL-105 живут в своих модулях.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: сводка, календарь и блок «По лекарствам» иначе не собираются,
// и проверка увидела бы не экран, а его верх (тот же приём, что в `HistoryAcceptanceTest`).
// Язык — часть условий: сценарии записаны по-русски, и множественные формы подписи плитки
// на английской локали стенда читались бы не тем, что в документе.
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk105HistoryAcceptanceTest {

    @Test
    fun theSummaryTileScenariosOfTheHistoryRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $HISTORY_TAG"))
    }

    private companion object {
        const val TASK = "SKL-105"
        const val SCREEN_TAG = "@screen"
        const val HISTORY_TAG = "@history"
    }
}
