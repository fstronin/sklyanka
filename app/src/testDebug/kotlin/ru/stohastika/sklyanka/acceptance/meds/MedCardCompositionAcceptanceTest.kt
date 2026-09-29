package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки архивации карточки (SKL-10, `@composition`).
 *
 * Модуль выбран не по удобству: сценарию «Архивированное лекарство не планируется
 * и не напоминает» нужен экран «Сегодня» — он живёт в `feature-today`, и единственное
 * место, где он существует рядом с миром карточки, `app` (`SKL-26/TDD.md §4.3`).
 *
 * Остальные `@screen`-сценарии SKL-10 исполняет прогон `feature-meds`
 * (`@screen and not @composition`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedCardCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTheCardRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
    }

    private companion object {
        const val TASK = "SKL-10"
        const val COMPOSITION_TAG = "@composition"
    }
}
