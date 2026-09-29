package ru.stohastika.sklyanka.acceptance.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Композиционный прогон приёмки SKL-31: возврат из истории туда, откуда её открыли (ОВ-84).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: примеру «с „Сегодня“ → на „Сегодня“» нужны
 * «Сегодня» и история одновременно, а живут они в разных модулях — единственное место,
 * где оба экрана существуют сразу, `app`. Пример «из настроек → в настройки» идёт через
 * тот же прогон: структура сценария различает примеры только местом входа, и разводить
 * их по прогонам значило бы проверять две разные структуры вместо одной.
 *
 * Остальные `@screen`-сценарии SKL-31 исполняет прогон `feature-today`
 * (`@screen and not @composition`): один сценарий исполняется ровно там, где видны
 * его экраны.
 */
@RunWith(AndroidJUnit4::class)
// Экран истории прокручивается целиком, а строка прогресса «Сегодня» стоит в шапке:
// высоты с запасом хватает, чтобы собирались обе части связки.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TodayHistoryCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTodayHistoryRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-31"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}
