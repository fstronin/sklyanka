package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.feature.today.R
import ru.heuri.sklyanka.feature.today.TAG_GROUP_CARD
import ru.heuri.sklyanka.feature.today.TAG_GROUP_MARK_ALL
import ru.heuri.sklyanka.feature.today.TAG_GROUP_ONE_BY_ONE
import ru.heuri.sklyanka.feature.today.TAG_LANE_HEADER
import ru.heuri.sklyanka.feature.today.TodayIntent
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки групповой отметки одновременных приёмов (SKL-08, группа
 * `@group-marking`; заведены SKL-26).
 *
 * Нажатие уходит намерением, а отмечает группу тот же доменный сценарий, что в продукте
 * ([TodayScreenProbe]): правило «вся группа или никак» проверяется, а не пересказывается.
 *
 * Клик ищется по тегу кнопки, а не по подписи: при уже отмеченном приёме домен честно
 * исключает его из операции, и подпись становится «Отметить оба», тогда как сценарий
 * зовёт кнопку «Отметить все три» (`SKL-26/TODO.md`, пункт 32).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class TodayGroupMarkingSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Время групповой карточки, о которой сценарий говорил последней. */
    private var lastGroupTime: String? = null

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    // --- Состав групповой карточки --------------------------------------------------------

    @Когда("^человек смотрит на группу «([^»]+)»$")
    fun thePersonLooksAtTheGroup(time: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { }
    }

    @Когда("^человек смотрит на групповую карточку времени «([^»]+)»$")
    fun thePersonLooksAtTheGroupCard(time: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { }
    }

    @Тогда("^показана одна карточка с временем «([^»]+)» и меткой «([^»]+)»$")
    fun theGroupCardIsSingle(time: String, label: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_GROUP_CARD, useUnmergedTree = true).assertCountEquals(1)
            val texts = TodayScreenProbe.texts(test, TAG_GROUP_CARD)
            assertTrue("the group card must show $time: $texts", time in texts)
            assertTrue("the group card must be marked «$label»: $texts", label in texts)
        }
    }

    @Тогда("^лекарства перечислены одной строкой «([^»]+)»$")
    fun theMedicationsAreListedInOneLine(line: String) {
        val separator = context.getString(R.string.group_medications_separator)
        val expected = line.split(separator)
            .joinToString(separator) { name -> TodayScreenProbe.displayName(world, name) }
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.texts(test, TAG_GROUP_CARD)
            assertTrue("the group card must list «$line» as «$expected»: $texts", expected in texts)
        }
    }

    @Тогда("^у карточки две кнопки: «([^»]+)» и «([^»]+)»$")
    fun theCardHasTwoButtons(markAll: String, oneByOne: String) {
        TodayScreenProbe.ask(world) { test ->
            val marks = TodayScreenProbe.texts(test, TAG_GROUP_MARK_ALL)
            assertTrue("the group card must offer «$markAll»: $marks", markAll in marks)
            val singles = TodayScreenProbe.texts(test, TAG_GROUP_ONE_BY_ONE)
            assertTrue("the group card must offer «$oneByOne»: $singles", oneByOne in singles)
        }
    }

    @Тогда("^подпись кнопки групповой отметки — «([^»]+)»$")
    fun theGroupButtonReads(label: String) {
        TodayScreenProbe.ask(world) { test ->
            val marks = TodayScreenProbe.texts(test, TAG_GROUP_MARK_ALL)
            assertTrue("the group button must read «$label»: $marks", label in marks)
        }
    }

    // --- Действия -------------------------------------------------------------------------

    @Когда("^человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButton(button: String) = pressMarkAll(button)

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButtonAt(date: String, time: String, button: String) {
        world.clock.moveTo(world.localMoment(date, time))
        pressMarkAll(button)
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButtonTodayAt(time: String, button: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        pressMarkAll(button)
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «По одному»$")
    fun thePersonPressesOneByOne(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNodeWithTag(TAG_GROUP_ONE_BY_ONE, useUnmergedTree = true).performClick()
        }
        // Время группы называет само намерение экрана: сценарий о нём не говорит,
        // а «эта группа» — та, которую человек только что развернул.
        val unfold = requireNotNull(intents.filterIsInstance<TodayIntent.ExpandGroup>().firstOrNull()) {
            "«По одному» must unfold the group: $intents"
        }
        lastGroupTime = unfold.time.format(timeFormat)
    }

    // --- Что стало с группой ---------------------------------------------------------------

    @Тогда("^групповая карточка разворачивается в три отдельные строки$")
    fun theGroupCardUnfoldsIntoThreeRows() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_GROUP_CARD, useUnmergedTree = true).assertCountEquals(0)
            assertEquals("the unfolded group must show three rows", 3, cardsOfLane(test, groupTime()).size)
        }
    }

    @Тогда("^у каждой строки своя кнопка отметки$")
    fun eachRowHasItsOwnMarkButton() {
        TodayScreenProbe.ask(world) { test ->
            val taken = context.getString(UiR.string.action_taken)
            val cards = cardsOfLane(test, groupTime())
            assertTrue("the scenario unfolded no rows", cards.isNotEmpty())
            cards.forEach { card ->
                val texts = TodayScreenProbe.textsOf(card)
                assertTrue("every row must offer «$taken»: $texts", taken in texts)
            }
        }
    }

    @Тогда("^ни один приём не отмечен$")
    fun noneOfTheIntakesIsMarked() {
        val time = LocalTime.parse(groupTime())
        val intakes = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.scheduledAt.toLocalDate() == world.clock.today() && it.scheduledAt.toLocalTime() == time }
        assertTrue("no intakes at $time", intakes.isNotEmpty())
        intakes.forEach { intake ->
            assertTrue("the intake at ${intake.scheduledAt} must stay pending", intake.isPending)
        }
    }

    // --- Обвязка ---------------------------------------------------------------------------

    private fun pressMarkAll(button: String) {
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNodeWithTag(TAG_GROUP_MARK_ALL, useUnmergedTree = true).performClick()
        }
        assertTrue(
            "«$button» must ask to mark the whole group: $intents",
            intents.any { it is TodayIntent.MarkGroupTaken },
        )
    }

    private fun groupTime(): String = requireNotNull(lastGroupTime) { "the scenario never named a group" }

    /** Карточки ленты указанного времени: между её заголовком и следующим. */
    private fun cardsOfLane(test: ComposeUiTest, time: String): List<SemanticsNode> {
        val headers = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val header = requireNotNull(headers.firstOrNull { time in TodayScreenProbe.textsOf(it) }) { "no lane $time" }
        val next = headers.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        return test.onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= header.boundsInRoot.top }
            .filter { next == null || it.boundsInRoot.top < next }
    }
}
