package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.stohastika.sklyanka.feature.today.TAG_PRN_CARD
import ru.stohastika.sklyanka.feature.today.TAG_PRN_COLLAPSED
import ru.stohastika.sklyanka.feature.today.TAG_PRN_DIVIDER
import ru.stohastika.sklyanka.feature.today.TAG_PRN_LIMIT_REASON
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки блока «По потребности» (SKL-08, группа `@screen`; заведены SKL-26).
 *
 * Нажатие «Принять» идёт по собранной композиции, а создаёт приём тот же доменный
 * сценарий, что в продукте ([TodayScreenProbe]). Недоступность кнопки проверяется
 * у собранного экрана: причина ограничения — предметное значение, и подписывает его
 * экран, а не шаг.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayPrnScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Действия ---------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Принять» в карточке «([^»]+)»$")
    fun thePersonPressesTakeInTheOnDemandCard(date: String, time: String, medication: String) {
        world.clock.moveTo(world.localMoment(date, time))
        pressTake(medication)
    }

    /** То же нажатие без переноса часов: Предыстория уже поставила момент сценария (SKL-30). */
    @Когда("^человек нажимает «Принять» в карточке «([^»]+)»$")
    fun thePersonPressesTakeInTheOnDemandCardNow(medication: String) {
        pressTake(medication)
    }

    private fun pressTake(medication: String) {
        val medicationId = world.medications.get(world.medicationNamed(medication)).id
        val take = context.getString(UiR.string.action_take)
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasText(take) and hasAnyAncestor(prnCard(world, medication)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the on-demand card of $medication must ask to create the intake: $intents",
            TodayIntent.TakePrn(medicationId) in intents,
        )
    }

    @Когда("^человек смотрит на карточку «([^»]+)» в (\\d{2}:\\d{2})$")
    fun thePersonLooksAtTheOnDemandCard(medication: String, time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        world.medications.focusOn(world.medicationNamed(medication))
        TodayScreenProbe.ask(world) { }
    }

    // --- Состав блока -----------------------------------------------------------

    @Тогда("^ниже лент приёмов показан разделитель «([^»]+)»$")
    fun theDividerStandsBelowTheLanes(divider: String) {
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_PRN_DIVIDER).fetchSemanticsNode()
            assertTrue(
                "the divider must read «$divider»: ${TodayScreenProbe.textsOf(node)}",
                divider in TodayScreenProbe.textsOf(node),
            )
            val cards = test.onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("the scenario must have lanes of its own", cards.isNotEmpty())
            assertTrue(
                "the divider must stand below the lanes of the schedule",
                node.boundsInRoot.top >= cards.maxOf { it.boundsInRoot.bottom },
            )
        }
    }

    @Тогда("^в нём карточка «([^»]+)» с подписью «([^»]+)»$")
    fun theBlockHoldsThatCard(medication: String, subtitle: String) {
        TodayScreenProbe.ask(world) { test ->
            val card = test.onNode(prnCard(world, medication), useUnmergedTree = true)
            val texts = TodayScreenProbe.textsOf(card.fetchSemanticsNode())
            assertTrue(
                "the on-demand card must name ${TodayScreenProbe.displayName(world, medication)}: $texts",
                TodayScreenProbe.displayName(world, medication) in texts,
            )
            assertTrue("the on-demand card must read «$subtitle»: $texts", subtitle in texts)
        }
    }

    /**
     * Кнопка карточки «по потребности» — в мёрженом дереве: подпись и признак
     * доступности лежат у одного узла нажатия, и проверяются они вместе.
     */
    @Тогда("^у карточки есть кнопка «([^»]+)»$")
    fun theCardHasThatButton(button: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onNode(hasText(button) and hasAnyAncestor(hasTestTag(TAG_PRN_CARD))).assertIsDisplayed()
        }
    }

    @Тогда("^кнопка «([^»]+)» недоступна$")
    fun theButtonIsDisabled(button: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onNode(hasText(button) and hasAnyAncestor(hasTestTag(TAG_PRN_CARD))).assertIsNotEnabled()
        }
    }

    @Тогда("^причина недоступности видна человеку текстом$")
    fun theReasonIsWrittenOut() {
        TodayScreenProbe.ask(world) { test ->
            val reason = TodayScreenProbe.textsOf(test.onNodeWithTag(TAG_PRN_LIMIT_REASON).fetchSemanticsNode())
            assertTrue("the reason must be spelled out, not only coloured: $reason", reason.any { it.isNotBlank() })
        }
    }

    @Тогда("^разделителя «([^»]+)» на экране нет$")
    fun theDividerIsGone(divider: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_PRN_DIVIDER).assertCountEquals(0)
            test.onAllNodes(hasText(divider)).assertCountEquals(0)
        }
    }

    @Тогда("^пустого места на его месте не остаётся$")
    fun theBlockLeavesNoGap() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_PRN_CARD).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_PRN_COLLAPSED).assertCountEquals(0)
        }
    }

    // --- Прогресс дня -----------------------------------------------------------

    @Тогда("^подпись прогресса дня остаётся «([^»]+)»$")
    fun theDayProgressCaptionStays(caption: String) {
        TodayScreenProbe.ask(world) { test ->
            val shown = TodayScreenProbe.texts(test, TAG_DAY_PROGRESS)
            assertTrue("the day progress must read «$caption»: $shown", caption in shown)
        }
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Карточка «по потребности»: имя лекарства лежит у потомков, а не у самого узла. */
    private fun prnCard(world: AcceptanceWorld, medication: String): SemanticsMatcher =
        hasTestTag(TAG_PRN_CARD) and hasAnyDescendant(
            hasText(TodayScreenProbe.displayName(world, medication)),
        )
}
