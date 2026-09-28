package ru.heuri.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки главного экрана (SKL-26, группа `@screen` файла SKL-08).
 *
 * Модуль свой: «Сегодня» живёт здесь, и шаг о составе его лент обязан видеть собранный
 * экран и его ресурсы (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: блок «По потребности» и нижние ленты иначе не собираются,
// и проверка видела бы не экран, а его верх (тот же приём, что в `TodayScreenTest`).
// Высота — с запасом на развёрнутый плотный день: в нём тридцать приёмов, и ленивый
// список не собрал бы дальние карточки, а сценарий говорит и о них (SKL-26).
// Язык — часть условий: сценарии приёмки записаны по-русски, и даты со склонениями
// на английской локали стенда читаются не тем, что записано в документе.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodayAcceptanceTest {

    @Test
    fun theScreenScenariosOfTodayRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-08"
    }
}
