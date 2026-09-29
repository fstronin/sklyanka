package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки предупреждения о ненадёжности напоминаний (SKL-29, группа
 * `@screen`).
 *
 * Модуль свой: предупреждение живёт в шапке «Сегодня», и шаг обязан видеть собранный
 * экран и его ресурсы (`SKL-26/TDD.md §4.3`). Сценарии, помеченные `@composition`,
 * исполняет прогон `app`: им нужен возврат на экран разрешений сразу с «Сегодня»,
 * а это два экрана в одном прогоне — единственное такое место `app`.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: без запаса по высоте ленивый список не собрал бы карточки
// лент, и проверка «приём можно отметить вручную» видела бы не экран, а его верх.
// Язык — часть условий: строки предупреждения читаются по-русски из ресурсов (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodayReliabilityAcceptanceTest {

    @Test
    fun theReliabilityScenariosOfTodayRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-29"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
