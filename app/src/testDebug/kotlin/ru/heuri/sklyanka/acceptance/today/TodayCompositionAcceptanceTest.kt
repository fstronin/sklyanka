package ru.heuri.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки перехода «Купить» → карточка лекарства (SKL-08, `@composition`).
 *
 * Модуль выбран не по удобству: сценарию нужны «Сегодня» и карточка лекарства
 * одновременно, а живут они в разных модулях — единственное место, где оба экрана
 * существуют сразу, `app` (`SKL-26/TDD.md §4.3`). Тег `@composition` — область прогона:
 * тот же `@screen`, которому нужен не стенд одного экрана, а композиция.
 *
 * Остальные `@screen`-сценарии SKL-08 исполняет прогон `feature-today`
 * (`@screen and not @composition`): один сценарий исполняется ровно там, где видны
 * его экраны.
 */
@RunWith(AndroidJUnit4::class)
// Карточка лекарства прокручивается целиком: высота с запасом на её блоки, чтобы
// блок остатка собирался, а не оставался за краем окна.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TodayCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTodayRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
    }

    private companion object {
        const val TASK = "SKL-08"
        const val COMPOSITION_TAG = "@composition"
    }
}
