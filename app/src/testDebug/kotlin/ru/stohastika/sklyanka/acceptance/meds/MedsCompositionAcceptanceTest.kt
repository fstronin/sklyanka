package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки формы и «Сегодня» (SKL-09, `@composition`).
 *
 * Модуль выбран не по удобству: сценарию «Выключенное напоминание о покупке отменяет
 * предупреждение об остатке» нужны форма и «Сегодня» одновременно, а живут они
 * в разных модулях — единственное место, где оба экрана существуют сразу, `app`
 * (`SKL-26/TDD.md §4.3`). Тег `@composition` — область прогона: тот же `@screen`,
 * которому нужен не стенд одного экрана, а композиция.
 *
 * Остальные `@screen`-сценарии SKL-09 исполняет прогон `feature-meds`
 * (`@screen and not @composition`): один сценарий исполняется ровно там, где видны
 * его экраны.
 */
@RunWith(AndroidJUnit4::class)
// Форма длиннее окна телефона: переключатель остатка иначе не собрался бы под краем.
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfMedsRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
    }

    private companion object {
        const val TASK = "SKL-09"
        const val COMPOSITION_TAG = "@composition"
    }
}
