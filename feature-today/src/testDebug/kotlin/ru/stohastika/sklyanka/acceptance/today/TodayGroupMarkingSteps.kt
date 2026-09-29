package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_GROUP_CAPTION
import ru.stohastika.sklyanka.feature.today.TAG_GROUP_MARK_ALL
import ru.stohastika.sklyanka.feature.today.TAG_LANE_HEADER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки групповой отметки одновременных приёмов (SKL-31, группа
 * `@group-marking`; переписаны из SKL-26 под решение ОВ-32).
 *
 * Приёмы одного времени показаны строками по одной, а батч-кнопка выдаётся только
 * наступившей группе с двумя и более ожидающими. Нажатие «Отметить …» лишь заряжает
 * кнопку (`ArmGroupMark`); отмечает второе нажатие (`MarkGroupTaken`) — тем же доменным
 * путём, что в продукте ([TodayScreenProbe]), и правило «вся группа или никак»
 * проверяется, а не пересказывается.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class TodayGroupMarkingSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Время группы, о которой сценарий говорил последней. */
    private var lastGroupTime: String? = null

    /** Где на экране была батч-кнопка: сценарий возвращается на это место после отметки. */
    private var buttonBounds: Rect? = null

    // --- Состав группы --------------------------------------------------------------------

    @Тогда("^время «([^»]+)» стоит в разделителе один раз с меткой «([^»]+)»$")
    fun theTimeStandsOnceInTheDivider(time: String, label: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            assertEquals(
                "the time $time must stand in the divider alone",
                1,
                test.onAllNodesWithText(time, useUnmergedTree = true).fetchSemanticsNodes().size,
            )
            val header = TodayScreenProbe.lanes(test).firstOrNull { it.first() == time }
            assertEquals("the divider of $time must be labelled «$label»", label, header?.last())
        }
    }

    @Тогда("^под разделителем показана подпись группы «([^»]+)»$")
    fun theCaptionUnderTheDivider(caption: String) {
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.texts(test, TAG_GROUP_CAPTION)
            assertTrue("the group must be captioned «$caption»: $texts", caption in texts)
        }
    }

    @Тогда("^под разделителем «([^»]+)» подпись группы — «([^»]+)»$")
    fun theCaptionOfThatDividerIs(time: String, caption: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_GROUP_CAPTION, useUnmergedTree = true).fetchSemanticsNode()
            val header = laneHeader(test, time)
            assertTrue(
                "the caption of $time must stand under its divider",
                node.boundsInRoot.top >= header.boundsInRoot.top,
            )
            assertTrue(
                "the caption of $time must read «$caption»: ${TodayScreenProbe.textsOf(node)}",
                caption in TodayScreenProbe.textsOf(node),
            )
        }
    }

    @Тогда("^подпись группы (?:снова )?читается «([^»]+)»$")
    fun theGroupCaptionReads(caption: String) {
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.texts(test, TAG_GROUP_CAPTION)
            assertTrue("the group caption must read «$caption»: $texts", caption in texts)
        }
    }

    @Тогда("^каждый приём показан отдельной строкой$")
    fun everyIntakeIsARowOfItsOwn() {
        TodayScreenProbe.ask(world) { test ->
            val rows = nodesInLane(test, groupTime(), TAG_INTAKE_CARD)
            assertEquals("every intake of the group must be a row of its own", intakesAt(groupTime()).size, rows.size)
        }
    }

    @Тогда("^время в строках приёмов не повторяется$")
    fun theRowsDoNotRepeatTheTime() {
        TodayScreenProbe.ask(world) { test ->
            val time = groupTime()
            nodesInLane(test, time, TAG_INTAKE_CARD).forEach { row ->
                val texts = TodayScreenProbe.textsOf(row)
                assertTrue("a row must not repeat the time $time: $texts", time !in texts)
            }
        }
    }

    @Тогда("^у каждой строки своя кнопка отметки$")
    fun eachRowHasItsOwnMarkButton() {
        TodayScreenProbe.ask(world) { test ->
            val taken = context.getString(UiR.string.action_taken)
            val rows = nodesInLane(test, groupTime(), TAG_INTAKE_CARD)
            assertTrue("the group must show its rows", rows.isNotEmpty())
            rows.forEach { row ->
                val texts = TodayScreenProbe.textsOf(row)
                assertTrue("every row must offer «$taken»: $texts", taken in texts)
            }
        }
    }

    @Тогда("^время «([^»]+)» стоит в каждой строке группы$")
    fun theTimeStandsInEveryRow(time: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            val rows = nodesInLane(test, time, TAG_INTAKE_CARD)
            assertTrue("the group of $time must show its rows", rows.isNotEmpty())
            // Время стоит колонкой самого ряда, а не карточкой внутри него: ищется
            // текстовый узел времени, лежащий на высоте строки (MainDense.dc.html).
            val times = test.onAllNodesWithText(time, useUnmergedTree = true).fetchSemanticsNodes()
            rows.forEach { row ->
                val marked = times.any { node ->
                    node.boundsInRoot.top >= row.boundsInRoot.top && node.boundsInRoot.bottom <= row.boundsInRoot.bottom
                }
                assertTrue(
                    "every row must carry the time $time: ${TodayScreenProbe.textsOf(row)}",
                    marked,
                )
            }
        }
    }

    @Тогда("^у каждого приёма показана своя круглая кнопка-галочка$")
    fun everyIntakeHasItsOwnRoundCheck() {
        TodayScreenProbe.ask(world) { test ->
            val rows = nodesInLane(test, groupTime(), TAG_INTAKE_CARD)
            assertTrue("the group must show its rows", rows.isNotEmpty())
            rows.forEach { row ->
                assertTrue("every row must carry the round check", row.containsTag(TAG_MARK_BUTTON))
            }
        }
    }

    @Тогда("^подпись кнопки групповой отметки у группы «([^»]+)» — «([^»]+)»$")
    fun theGroupButtonOfThatGroupReads(time: String, label: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.textsOf(batchButtonInLane(test, time).fetchSemanticsNode())
            assertTrue("the group button of $time must read «$label»: $texts", label in texts)
        }
    }

    @Тогда("^подпись кнопки у группы «([^»]+)» — «([^»]+)»$")
    fun theButtonOfThatGroupReads(time: String, label: String) = theGroupButtonOfThatGroupReads(time, label)

    @Тогда("^кнопка групповой отметки (?:снова )?читается «([^»]+)»$")
    fun theGroupButtonReads(label: String) {
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.texts(test, TAG_GROUP_MARK_ALL)
            assertTrue("the group button must read «$label»: $texts", label in texts)
        }
    }

    @Тогда("^кнопка групповой отметки у группы «([^»]+)» читается «([^»]+)»$")
    fun theGroupButtonOfThatLaneReads(time: String, label: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.textsOf(batchButtonInLane(test, time).fetchSemanticsNode())
            assertTrue("the group button of $time must read «$label»: $texts", label in texts)
        }
    }

    // --- Отсутствие подписи и батча --------------------------------------------------------

    @Тогда("^у группы «([^»]+)» нет подписи группы$")
    fun theGroupHasNoCaption(time: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            assertTrue("the group of $time must carry no caption", nodesInLane(test, time, TAG_GROUP_CAPTION).isEmpty())
        }
    }

    @Тогда("^у группы «([^»]+)» нет кнопки групповой отметки$")
    fun theGroupHasNoBatchButton(time: String) {
        lastGroupTime = time
        TodayScreenProbe.ask(world) { test ->
            assertTrue(
                "the group of $time must carry no batch button",
                nodesInLane(test, time, TAG_GROUP_MARK_ALL).isEmpty(),
            )
        }
    }

    @Тогда("^подписи группы и кнопки групповой отметки на экране больше нет$")
    fun theCaptionAndTheBatchAreGone() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_GROUP_CAPTION, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_GROUP_MARK_ALL, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Действия -------------------------------------------------------------------------

    @Когда("^человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButton(button: String) = armBatch(button)

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButtonAt(date: String, time: String, button: String) {
        world.clock.moveTo(world.localMoment(date, time))
        armBatch(button)
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «(Отметить[^»]*)»$")
    fun thePersonPressesTheGroupMarkButtonTodayAt(time: String, button: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        armBatch(button)
    }

    @Когда("^(?:человек )?нажимает «(Отметить[^»]*)» у группы «([^»]+)»$")
    fun thePersonPressesTheGroupMarkButtonOfThatGroup(button: String, time: String) {
        lastGroupTime = time
        val intents = TodayScreenProbe.act(world) { test ->
            batchButtonInLane(test, time).performClick()
        }
        assertArmed(intents, time)
    }

    @Когда("^(?:человек )?нажимает «(Подтвердить отметку[^»]*)»$")
    fun thePersonConfirmsTheGroupMark(button: String) {
        var bounds: Rect? = null
        val intents = TodayScreenProbe.act(world) { test ->
            val batch = test.onNodeWithTag(TAG_GROUP_MARK_ALL, useUnmergedTree = true)
            bounds = batch.fetchSemanticsNode().boundsInRoot
            batch.performClick()
        }
        buttonBounds = bounds
        assertTrue(
            "«$button» must mark the whole group: $intents",
            intents.any { it is TodayIntent.MarkGroupTaken },
        )
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}(?::\\d{2})?) человек нажимает на то место, где была кнопка$")
    fun thePersonTapsWhereTheButtonWas(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val spot = requireNotNull(buttonBounds) { "the scenario never pressed the batch button" }.center
        val intents = TodayScreenProbe.act(world) { test ->
            test.onRoot().performTouchInput { click(spot) }
        }
        assertTrue(
            "a tap on the old button spot must not mark anything: $intents",
            intents.none {
                it is TodayIntent.MarkGroupTaken || it is TodayIntent.MarkTaken || it is TodayIntent.MarkSkipped
            },
        )
    }

    @Когда("^(?:человек )?нажимает на экране вне кнопки подтверждения$")
    fun thePersonTapsOutsideTheConfirmation() {
        val intents = TodayScreenProbe.act(world) { test ->
            // Пустое место шапки: не кнопка и не карточка — ровно «вне кнопки» (ОВ-32).
            test.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        }
        assertTrue("a tap outside must dismiss the confirmation: $intents", TodayIntent.DismissGroupConfirm in intents)
    }

    @Когда("^человек прокручивает список приёмов, не трогая кнопку$")
    fun thePersonScrollsTheList() {
        TodayScreenProbe.act(world) { test ->
            test.onRoot().performTouchInput {
                // Короткое перетаскивание, а не бросок на всю высоту: кнопка группы
                // обязана остаться собранной (TDD §16.6).
                swipe(
                    start = center + Offset(0f, SCROLL_BY),
                    end = center - Offset(0f, SCROLL_BY),
                    durationMillis = 200,
                )
            }
            test.waitForIdle()
        }
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек уходит на вкладку «Лекарства»$")
    fun thePersonLeavesToTheMedsTab(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Уход с «Сегодня» — конец подтверждения: тот же `onScreenLeft`, что закрывает
        // окно отмены (ОВ-32, TDD §5).
        TodayScreenProbe.leaveScreen()
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) возвращается на «Сегодня»$")
    fun thePersonComesBackToToday(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
    }

    // --- Что значит состояние кнопки -------------------------------------------------------

    @Тогда("^она оформлена акцентно, а не контурно$")
    fun theButtonIsAccentFilled() {
        TodayScreenProbe.ask(world) { test ->
            assertTrue("the confirmation must be the primary button", primaryBatch(test).isNotEmpty())
        }
    }

    @Тогда("^она не спрашивает подтверждение$")
    fun theButtonDoesNotAskForConfirmation() {
        TodayScreenProbe.ask(world) { test ->
            assertTrue("the batch button must be ordinary, not a question", primaryBatch(test).isEmpty())
        }
    }

    @Тогда("^кнопка подтверждения оформлена акцентом профиля «([^»]+)»$")
    fun theConfirmationWearsTheAccentOfThatProfile(profile: String) {
        // Экран собирается в теме чужого профиля, и акцент берётся из неё же: кнопка
        // о профиле не знает (ПРФ-11). Механизм заливки доказан снимками core-ui.
        val accent = ProfileAccent.Foreign(ProfilePalette.of(world.profiles.get(profile).color))
        var primary = false
        TodayScreenProbe.askInProfile(world, accent) { test ->
            primary = primaryBatch(test).isNotEmpty()
        }
        assertEquals(
            "the confirmation must wear the accent of «$profile»",
            accent.color,
            TodayScreenProbe.accentIn(accent),
        )
        assertTrue("the confirmation must be the primary button", primary)
        assertTrue("the accent must differ from the owner's", accent.color != ProfileAccent.Owner.color)
    }

    // --- Обвязка ---------------------------------------------------------------------------

    /** Нажатие «Отметить …»: экран обязан только зарядить кнопку, ничего не отмечая (ОВ-32). */
    private fun armBatch(button: String) {
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNodeWithTag(TAG_GROUP_MARK_ALL, useUnmergedTree = true).performClick()
        }
        val arm = requireNotNull(intents.filterIsInstance<TodayIntent.ArmGroupMark>().singleOrNull()) {
            "«$button» must ask to arm the group mark, not mark it: $intents"
        }
        lastGroupTime = TIME_FORMAT.format(arm.time)
    }

    private fun assertArmed(intents: List<TodayIntent>, time: String) {
        val arm = intents.filterIsInstance<TodayIntent.ArmGroupMark>().singleOrNull()
        requireNotNull(arm) { "the batch button must only arm the group mark: $intents" }
        assertEquals("the armed group must be «$time»", LocalTime.parse(time), arm.time)
        lastGroupTime = time
    }

    /** Основная кнопка внутри батча: подтверждение — она, обычное состояние — нет. */
    private fun primaryBatch(test: ComposeUiTest): List<SemanticsNode> = test.onAllNodes(
        hasTestTag(TAG_PRIMARY_BUTTON) and hasAnyAncestor(hasTestTag(TAG_GROUP_MARK_ALL)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()

    private fun groupTime(): String = requireNotNull(lastGroupTime) { "the scenario never named a group" }

    private fun intakesAt(time: String): List<String> {
        val moment = LocalTime.parse(time)
        return world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.scheduledAt.toLocalDate() == world.clock.today() && it.scheduledAt.toLocalTime() == moment }
            .map { it.id }
    }

    private fun laneHeader(test: ComposeUiTest, time: String): SemanticsNode {
        val headers = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        return requireNotNull(headers.firstOrNull { time in TodayScreenProbe.textsOf(it) }) { "no lane $time" }
    }

    /** Узлы с тегом внутри ленты указанного времени: между её разделителем и следующим. */
    private fun nodesInLane(test: ComposeUiTest, time: String, tag: String): List<SemanticsNode> {
        val header = laneHeader(test, time)
        val headers = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val next = headers.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        return test.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= header.boundsInRoot.top }
            .filter { next == null || it.boundsInRoot.top < next }
    }

    private fun batchButtonInLane(test: ComposeUiTest, time: String): SemanticsNodeInteraction {
        val header = laneHeader(test, time)
        val headers = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val next = headers.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        val buttons = test.onAllNodesWithTag(TAG_GROUP_MARK_ALL, useUnmergedTree = true)
        val index = buttons.fetchSemanticsNodes().indexOfFirst {
            it.boundsInRoot.top >= header.boundsInRoot.top && (next == null || it.boundsInRoot.top < next)
        }
        require(index >= 0) { "no batch button in lane $time" }
        return buttons[index]
    }

    /** Тег есть в узле или любом его потомке. */
    private fun SemanticsNode.containsTag(tag: String): Boolean =
        config.getOrNull(SemanticsProperties.TestTag) == tag || children.any { it.containsTag(tag) }

    private companion object {
        /** Шаг короткого перетаскивания списка: кнопка группы обязана остаться на экране. */
        const val SCROLL_BY = 120f

        /** Время группы в терминах сценария: «14:00». */
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
