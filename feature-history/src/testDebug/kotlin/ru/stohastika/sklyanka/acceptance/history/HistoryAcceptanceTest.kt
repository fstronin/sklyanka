package ru.stohastika.sklyanka.acceptance.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки истории и отчёта врачу (SKL-26, группа `@screen` файла SKL-18).
 *
 * Модуль свой: экран истории живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Сценарий «Возврат ведёт туда, откуда человек пришёл» несёт `@composition`: истории
 * нужен вход из настроек, а настройки живут в другом модуле — единственное место, где оба
 * экрана существуют одновременно, `app`. Его исполняет прогон `app`, и здесь тег исключён —
 * иначе сценарий остался бы в этом прогоне без шагов.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: блок «По лекарствам», строка-вывод и лист отчёта иначе
// не собираются, и проверка видела бы не экран, а его верх (тот же приём, что в
// `HistoryScreenTest`). Язык — часть условий: сценарии приёмки записаны по-русски,
// и даты со склонениями на английской локали стенда читаются не тем, что в документе.
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class HistoryAcceptanceTest {

    @Test
    fun theScreenScenariosOfHistoryRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-18"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
