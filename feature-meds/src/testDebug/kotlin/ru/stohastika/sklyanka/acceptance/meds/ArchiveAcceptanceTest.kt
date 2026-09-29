package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки архива курсов (SKL-26, группа `@screen` файла SKL-11).
 *
 * Модуль свой: архив живёт в `feature-meds`, и его шаги обязаны видеть собранный экран
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Сценарии, где архив соседствует
 * с «Сегодня» (`@composition`), исполняет прогон `app`.
 */
@RunWith(AndroidJUnit4::class)
// Архив длиннее окна телефона: двенадцать карточек и плашка истории иначе не собираются,
// и проверка видела бы не экран, а его верх (тот же приём, что в модульных тестах).
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class ArchiveAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheArchiveRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-11"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
