package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки композиции «карточка напоминания → Сегодня» (SKL-30, 5 сценариев).
 *
 * Модуль выбран не по удобству: сценарию нужны оба экрана одновременно, а живут они
 * в разных модулях — единственное место, где оба существуют сразу, `app`
 * (`SKL-26/TDD.md §4.3`). Остальные `@screen`-сценарии SKL-30 исполняют прогоны
 * `feature-today` и `feature-reminder`: каждому — там, где видны его экраны.
 */
@RunWith(AndroidJUnit4::class)
// Высота с запасом: карточка напоминания стоит на весь экран, а «Сегодня» должна
// показать ленты и нижнюю панель со снэкбаром; язык — часть условий: сценарии записаны
// по-русски, и склонения счётных форм читаются не на английской локали стенда.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class UndoCompositionAcceptanceTest {

    @Test
    fun theUndoCompositionScenariosRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
        } finally {
            UndoAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-30"
        const val COMPOSITION_TAG = "@screen and @composition"
    }
}
