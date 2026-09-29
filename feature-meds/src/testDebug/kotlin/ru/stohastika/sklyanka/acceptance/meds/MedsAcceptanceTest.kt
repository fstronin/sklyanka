package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки списка и формы лекарств (SKL-26, группа `@screen` файла SKL-09).
 *
 * Модуль свой: список и форма живут здесь, и их шаги обязаны видеть собранный экран
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`).
 *
 * Сценарий «Выключенное напоминание о покупке отменяет предупреждение об остатке»
 * несёт `@composition`: ему нужны форма и «Сегодня» одновременно, а «Сегодня» живёт
 * в другом модуле. Его исполняет прогон `app`, и здесь тег исключён — иначе сценарий
 * остался бы в этом прогоне без шагов.
 */
@RunWith(AndroidJUnit4::class)
// Форма длиннее окна телефона: блоки остатка и периода иначе не собираются, и проверка
// видела бы не экран, а его верх (тот же приём, что в `MedicationFormScreenTest`).
// Язык — часть условий: сценарии приёмки записаны по-русски, и даты со склонениями
// на английской локали стенда читаются не тем, что записано в документе.
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsAcceptanceTest {

    @Test
    fun theScreenScenariosOfMedsRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-09"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
