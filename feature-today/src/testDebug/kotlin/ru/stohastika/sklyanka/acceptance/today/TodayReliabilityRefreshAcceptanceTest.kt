package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки живого обновления предупреждения о ненадёжности (SKL-48,
 * группа `@screen`).
 *
 * Модуль свой: сценарию нужен живой `TodayViewModel` и собранный экран «Сегодня»
 * с их ресурсами. Сценарии SKL-29 (`TodayReliabilityAcceptanceTest`) остаются её
 * прогоном и продолжают исполняться без изменений.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: без запаса по высоте лента дня не собралась бы,
// и проверка «приёмы на месте» видела бы не экран, а его верх.
// Язык — часть условий: строки предупреждения читаются по-русски из ресурсов (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodayReliabilityRefreshAcceptanceTest {

    @Test
    fun theLiveRefreshScenariosOfReliabilityRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-48"
        const val TAGS = "@screen and not @composition and not @manual"
    }
}
