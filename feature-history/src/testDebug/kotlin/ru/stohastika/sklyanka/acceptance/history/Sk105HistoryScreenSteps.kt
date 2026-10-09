package ru.stohastika.sklyanka.acceptance.history

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.TAG_SUMMARY
import ru.stohastika.sklyanka.feature.history.TAG_TILE_DAYS_WITHOUT_MISSES
import ru.stohastika.sklyanka.feature.history.TAG_TILE_MISSES
import ru.stohastika.sklyanka.feature.history.TAG_TILE_ON_TIME
import kotlin.math.abs

/**
 * Экранные шаги приёмки плиток сводки истории (SKL-105, группа `@screen`).
 *
 * Шаги живут рядом с остальными шагами истории (`HistoryScreenSteps`), потому что говорят
 * о том же экране и о том же стенде: состояние собирает настоящий домен
 * ([HistoryFromWorld]), вопросы задаются собранной композиции ([HistoryScreenProbe]).
 * Отдельный файл, а не строки в `HistoryScreenSteps`, потому что проверки принадлежат
 * задаче SKL-105: у плитки «принято вовремя» больше нет прочерка (V-2, Р-33), а доля
 * меньше процента называется словами (Р-27).
 *
 * Фраза «плитки „принято вовремя“ на экране нет» объявлена в `HistoryScreenSteps`:
 * одно и то же её слово несут два файла приёмки (SKL-18 и SKL-105), и вторая копия
 * внутри одного прогона была бы отказом сборки. Здесь живёт то, чего в той фразе нет:
 * ряд двух оставшихся плиток и слова о доле.
 *
 * **Проверяется виденное, а не только тег.** Число и подпись плитки живут в семантике
 * потомков, а описание плитки их стирает (`SummaryTile`); поэтому текст ищется в дереве
 * без слияния и привязывается к плитке её прямоугольником — «число принадлежит плитке»
 * доказывается геометрией, а не догадкой о том, чьим ребёнком узел оказался. Там, где
 * текст обязан исчезнуть (одно сообщение читалке), вопрос задаётся слитому дереву: оно
 * и есть то, что слышит человек.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: слова
 * плиток берутся из ресурсов (`R.string`, `R.plurals`) и нигде не переписаны.
 */
@OptIn(ExperimentalTestApi::class)
class Sk105HistoryScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * §5.4: без третьей плитки две оставшиеся делят ряд пополам.
     *
     * «Пополам» — это ширина и края ряда, а не число колонок в разметке: пустая колонка
     * отдала бы половину ряда пустоте, и проверка по одному лишь тегу этого не увидела бы.
     */
    @Then("^плитки «дней без пропусков» и «пропусков за месяц» делят ряд пополам$")
    fun theTwoRemainingTilesSplitTheRow() {
        HistoryScreenProbe.ask(world) { test ->
            val row = bounds(test, TAG_SUMMARY)
            val days = bounds(test, TAG_TILE_DAYS_WITHOUT_MISSES)
            val misses = bounds(test, TAG_TILE_MISSES)
            val tolerance = with(test.density) { TOLERANCE.toPx() }
            assertTrue(
                "the two remaining tiles must be one width: ${days.width} vs ${misses.width}",
                abs(days.width - misses.width) <= tolerance,
            )
            assertTrue(
                "the days tile must open the row: ${days.left} vs ${row.left}",
                abs(days.left - row.left) <= tolerance,
            )
            assertTrue(
                "the misses tile must close the row: ${misses.right} vs ${row.right}",
                abs(misses.right - row.right) <= tolerance,
            )
        }
    }

    /**
     * Р-27: доля меньше процента названа словами.
     *
     * Слова берутся из ресурса: числа на такой плитке не бывает вовсе, и подстановка
     * чужого текста обязана падать. Домен при этом обязан действительно округлять долю
     * до нуля — иначе сценарий проверял бы не то состояние.
     */
    @Then("^плитка «принято вовремя» говорит, что доля меньше одного процента$")
    fun theTileNamesAShareBelowOnePercent() {
        val share = requireNotNull(period().onTimeShare as? OnTimeShare.Value) {
            "the scenario must leave a counted share, or the words have nothing to describe"
        }
        assertTrue("the share must round to zero percent for the words to be owed: $share", share.lessThanOnePercent)
        val words = context.getString(R.string.history_percent_less_than_one)
        assertEquals(
            "the tile must read «$words» (Р-27); zero here would erase a share that happened",
            1,
            nodesWithTextInsideTheOnTimeTile(words),
        )
    }

    /** Р-27: «0 %» на плитке — ложь, из-за которой метрике перестают верить. */
    @Then("^нуля на плитке не показано$")
    fun theTileShowsNoZero() {
        val zero = context.getString(R.string.history_percent, 0)
        assertEquals(
            "the tile must not show «$zero» (Р-27)",
            0,
            nodesWithTextInsideTheOnTimeTile(zero),
        )
    }

    /**
     * НФТ-30/§5.4: плитка читается одним сообщением и называет знаменатель.
     *
     * Ожидаемая фраза собирается из ресурса-множества теми же числами, что названы
     * в сценарии: переписанная русская строка разошлась бы с макетом на первом же
     * изменении склонений. Числа сверяются с посчитанной долей — иначе «12 из 14»
     * на экране могло бы быть чужим числом.
     *
     * «Одно сообщение» проверяется слитым деревом: узлу доступности запрещено нести
     * и число, и подпись по отдельности — читалка обязана услышать одно предложение.
     */
    @Then("^плитка «принято вовремя» читается одним сообщением «([^»]+)»$")
    fun theTileReadsAsOneMessage(message: String) {
        val numbers = Regex("\\d+").findAll(message).map { it.value.toInt() }.toList()
        assertEquals("the message must name the numerator and the denominator: «$message»", 2, numbers.size)
        val (onTime, due) = numbers
        val share = requireNotNull(period().onTimeShare as? OnTimeShare.Value) {
            "the scenario must leave a counted share, or the tile has no denominator to name"
        }
        assertEquals(
            "the screen must name the share the world counted: $share",
            onTime to due,
            share.onTime to share.due,
        )
        val expected = context.resources.getQuantityString(R.plurals.cd_history_on_time, due, onTime, due)
        val read = HistoryScreenProbe.ask(world) { test ->
            val tiles = test.onAllNodesWithTag(TAG_TILE_ON_TIME).fetchSemanticsNodes()
            assertEquals("the tile must be one node of the accessibility tree", 1, tiles.size)
            val tile = tiles.single()
            tile.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() to
                tile.config.getOrNull(SemanticsProperties.Text)
        }
        assertEquals("the tile must read «$expected»", expected, read.first)
        assertNull(
            "the tile must speak with one message, not with the number and the caption apart (НФТ-30)",
            read.second,
        )
    }

    /**
     * Пустой месяц: у оставшихся плиток прочерк.
     *
     * Прочерк ищется внутри прямоугольника плитки: «0 %» и прочерк стоят рядом и в
     * других блоках экрана, и проверка «прочерк где-то есть» ничего не говорила бы.
     */
    @Then("^плитки «дней без пропусков» и «пропусков за месяц» показывают прочерк$")
    fun theTwoRemainingTilesShowDashes() {
        val dash = context.getString(R.string.history_value_dash)
        val shown = HistoryScreenProbe.ask(world) { test ->
            listOf(TAG_TILE_DAYS_WITHOUT_MISSES, TAG_TILE_MISSES).map { tag ->
                val tile = bounds(test, tag)
                test.onAllNodesWithText(dash, useUnmergedTree = true).fetchSemanticsNodes()
                    .count { tile.contains(it.boundsInRoot.center) }
            }
        }
        assertEquals("each remaining tile must show the dash exactly once: $shown", listOf(1, 1), shown)
    }

    private fun period(): AdherencePeriod = HistoryFromWorld.period(world, HistoryScreenProbe.selected(world))

    private fun bounds(test: ComposeUiTest, tag: String): Rect =
        test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Сколько узлов с этим текстом стоит внутри плитки «принято вовремя». */
    private fun nodesWithTextInsideTheOnTimeTile(text: String): Int = HistoryScreenProbe.ask(world) { test ->
        val tile = bounds(test, TAG_TILE_ON_TIME)
        test.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            .count { tile.contains(it.boundsInRoot.center) }
    }

    private companion object {
        /** Допуск на округление пикселей при измерении краёв ряда. */
        val TOLERANCE = 1.dp
    }
}
