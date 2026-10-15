package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки вида расписания в форме лекарства (SKL-117, группа `@form @screen`).
 *
 * Модуль свой: блок расписания живёт в форме лекарства, и его шаги обязаны видеть
 * собранный экран и ресурсы модуля (`SKL-26/TDD.md §4.3`). Стенд — общий
 * [MedsFormStand]; своего стенда у вида расписания нет.
 *
 * Сценарии размечаются тегом `@screen`, а не `@composition`: форма и её блоки живут
 * в этом модуле целиком, и разделять их с «Сегодня» нечем.
 */
@RunWith(AndroidJUnit4::class)
// Форма длиннее окна телефона: блок расписания и периоды иначе не собираются, и проверка
// видела бы не экран, а его верх (тот же приём, что в `MedsAcceptanceTest`).
// Язык — часть условий: сценарии приёмки записаны по-русски, и дни недели со склонениями
// на английской локали стенда читаются не тем, что записано в документе.
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk117ScheduleAcceptanceTest {

    @Test
    fun theScheduleScreenScenariosRunOnTheFormStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-117"
    }
}
