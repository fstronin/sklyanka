package ru.heuri.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки архива и «Сегодня» (SKL-11, `@composition`).
 *
 * Модуль выбран не по удобству: сценариям «Архивированное лекарство не порождает
 * приёмов» и «Возврат порождает приёмы и перепланирует напоминания» нужен экран
 * «Сегодня» — он живёт в `feature-today`, и единственное место, где он существует
 * рядом с миром архива, `app` (`SKL-26/TDD.md §4.3`).
 *
 * Остальные `@screen`-сценарии SKL-11 исполняет прогон `feature-meds`
 * (`@screen and not @composition`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class ArchiveCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTheArchiveRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
    }

    private companion object {
        const val TASK = "SKL-11"
        const val COMPOSITION_TAG = "@composition"
    }
}
