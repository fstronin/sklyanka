package ru.heuri.sklyanka.acceptance.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки перехода «настройки → история» (SKL-18, `@composition`).
 *
 * Модуль выбран не по удобству: сценарию «Возврат ведёт туда, откуда человек пришёл»
 * нужны настройки и история одновременно, а живут они в разных модулях — единственное
 * место, где оба экрана существуют сразу, `app` (`SKL-26/TDD.md §4.3`). Тег `@composition` —
 * область прогона: тот же `@screen`, которому нужен не стенд одного экрана, а композиция.
 *
 * Остальные `@screen`-сценарии SKL-18 исполняет прогон `feature-history`
 * (`@screen and not @composition`): один сценарий исполняется ровно там, где видны
 * его экраны.
 */
@RunWith(AndroidJUnit4::class)
// Экран истории прокручивается целиком, а шапка с «Назад» стоит первой: высоты с запасом
// хватает, чтобы собирались и она, и строки настроек над ней.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class HistoryCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfHistoryRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
    }

    private companion object {
        const val TASK = "SKL-18"
        const val COMPOSITION_TAG = "@composition"
    }
}
