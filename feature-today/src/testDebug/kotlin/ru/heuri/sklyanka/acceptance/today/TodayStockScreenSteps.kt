package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.heuri.sklyanka.feature.today.TAG_STOCK_BANNER
import ru.heuri.sklyanka.ui.component.TAG_SCREEN_HEADER

/**
 * Экранные шаги приёмки предупреждения об остатке (SKL-08, группа `@screen`; заведены
 * SKL-26).
 *
 * Числа и то, о каком лекарстве предупреждение, считает домен (SKL-12); экран собирает
 * из них строку склонениями ресурсов. Шаг сверяет с сценарием собранную строку целиком:
 * и «осталось 9 таблеток», и «хватит на 4 дня» — согласование числа со словом, которое
 * проверяется только на настоящей строке.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayStockScreenSteps(private val world: AcceptanceWorld) {

    @Тогда("^показана одна плашка с текстом «([^»]+)»$")
    fun theSinglePlateCarriesThatText(text: String) {
        TodayScreenProbe.ask(world) { test ->
            val plates = test.onAllNodesWithTag(TAG_STOCK_BANNER, useUnmergedTree = true)
            plates.assertCountEquals(1)
            val texts = TodayScreenProbe.textsOf(plates.fetchSemanticsNodes().single())
            // Имя лекарства в сценарии — ключ, на экране — позиция `med-N` (НФТ-19):
            // сценарий называет имя, шаг подставляет то, которым экран его называет.
            val (medication, rest) = text.split(NAME_SEPARATOR, limit = 2)
            val expected = "${TodayScreenProbe.displayName(world, medication)} — $rest"
            assertTrue("the plate must read «$expected»: $texts", expected in texts)
        }
    }

    @Тогда("^рядом с ней стоит кнопка «([^»]+)»$")
    fun thePlateCarriesTheButton(button: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onNode(hasText(button) and hasAnyAncestor(hasTestTag(TAG_STOCK_BANNER))).assertIsDisplayed()
        }
    }

    @Тогда("^второй плашки на экране нет$")
    fun thereIsNoSecondPlate() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_STOCK_BANNER).assertCountEquals(1)
        }
    }

    @Тогда("^плашки предупреждения об остатке на экране нет$")
    fun thereIsNoStockPlate() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_STOCK_BANNER).assertCountEquals(0)
        }
    }

    @Тогда("^полоса прогресса дня стоит сразу под шапкой$")
    fun theDayProgressStandsRightUnderTheHeader() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_STOCK_BANNER).assertCountEquals(0)
            val header = test.onNodeWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNode()
            val progress = test.onNodeWithTag(TAG_DAY_PROGRESS).fetchSemanticsNode()
            assertTrue(
                "the day progress must stand right under the header",
                progress.boundsInRoot.top >= header.boundsInRoot.bottom,
            )
        }
    }

    private companion object {
        const val NAME_SEPARATOR = " — "
    }
}
