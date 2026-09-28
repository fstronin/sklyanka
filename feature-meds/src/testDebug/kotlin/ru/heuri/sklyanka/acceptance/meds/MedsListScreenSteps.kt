package ru.heuri.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.RussianNames
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.feature.meds.R
import ru.heuri.sklyanka.feature.meds.TAG_SEGMENTED_CONTROL
import ru.heuri.sklyanka.feature.meds.list.MedsIntent
import ru.heuri.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.heuri.sklyanka.feature.meds.list.TAG_LAST_INTAKE
import ru.heuri.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.heuri.sklyanka.feature.meds.list.TAG_TRAILING_LINE
import ru.heuri.sklyanka.ui.component.TAG_SCREEN_HEADER
import java.time.LocalTime
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки списка лекарств (SKL-09, группа `@list`; заведены SKL-26).
 *
 * Состояние списка собирает [MedsListStand] тем же путём, что `MedicationListViewModel`,
 * а нажатия уходят намерениями и применяются к миру доменом. Переходы к форме и карточке
 * проверяются контрактом намерений: маршрут живёт в `app`, и выдумывать его здесь значило
 * бы проверять копию (`SKL-26/TDD.md §4.3`). Заголовок формы открывшегося экрана —
 * в модуле формы, и он проверяется там же.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedsListScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var intents: List<MedsIntent> = emptyList()

    /** Карточка последнего утверждения: «в число «Активные · 5» она не входит». */
    private var focusedCard: String = ""

    // --- Открытие экрана ---------------------------------------------------------

    @Допустим("^человек на экране «Лекарства»$")
    fun thePersonIsOnTheMedicationList() {
        MedsListStand.ask(world) { }
    }

    @Когда("^человек открывает экран «Лекарства»$")
    fun thePersonOpensTheMedicationList() {
        MedsListStand.ask(world) { }
    }

    // --- Состав списка -----------------------------------------------------------

    @Тогда("^показан заголовок «(.+)»$")
    fun theHeaderShows(title: String) {
        assertTrue(
            "the header must read «$title»: ${headerTexts()}",
            title in headerTexts(),
        )
    }

    @Тогда("^показаны карточки всех пяти лекарств$")
    fun allFiveCardsAreShown() {
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_MEDICATION_CARD, useUnmergedTree = true).assertCountEquals(FIVE)
        }
    }

    @Тогда("^у карточки «([^»]+)» чипы времени «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theCardCarriesThoseTimeChips(medication: String, first: String, second: String) {
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(medication), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("the card of «$medication» must show the chip $first: $texts", first in texts)
            assertTrue("the card of «$medication» must show the chip $second: $texts", second in texts)
        }
    }

    @Тогда("^сегментный переключатель показывает «Активные · (\\d+)» и «Архив · (\\d+)»$")
    fun theSegmentsShowThoseCounters(active: Int, archived: Int) {
        val expectedActive = context.getString(R.string.meds_tab_active, active)
        val expectedArchive = context.getString(R.string.meds_tab_archive, archived)
        MedsListStand.ask(world) { test ->
            val labels = MedsProbe.texts(test, TAG_SEGMENTED_CONTROL)
            assertTrue("the active segment must read «$expectedActive»: $labels", expectedActive in labels)
            assertTrue("the archive segment must read «$expectedArchive»: $labels", expectedArchive in labels)
        }
    }

    @Тогда("^карточка «([^»]+)» в списке показана$")
    fun thatCardIsShown(medication: String) {
        focusedCard = medication
        MedsListStand.ask(world) { test ->
            test.onNode(card(medication), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^в число «Активные · (\\d+)» она не входит$")
    fun thatCardIsNotCounted(active: Int) {
        val state = MedsListStand.state(world)
        val id = world.medications.get(world.medicationNamed(focusedCard)).id
        val card = state.items.firstOrNull { it.medicationId == id }
        assertNotNull("«$focusedCard» must be in the list", card)
        assertNotNull("«$focusedCard» must be an on-demand card (ЭКР-5)", card!!.asNeeded)
        assertEquals(
            "the active counter must not count on-demand medications (ЭКР-3)",
            active,
            state.activeCount,
        )
        val label = context.getString(R.string.meds_tab_active, active)
        MedsListStand.ask(world) { test ->
            test.onNodeWithText(label).assertIsDisplayed()
        }
    }

    // --- Карточка «по потребности» -----------------------------------------------

    @Тогда("^рядом с названием «([^»]+)» стоит чип «([^»]+)»$")
    fun theChipStandsNextToTheName(medication: String, chip: String) {
        focusedCard = medication
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(medication), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("the card of «$medication» must carry the chip «$chip»: $texts", chip in texts)
        }
    }

    @Тогда("^строка описания оканчивается на «не чаще (\\d+) раз в сутки»$")
    fun theDescriptionEndsWithThatLimit(limit: Int) {
        val expected = context.resources.getQuantityString(R.plurals.max_per_day, limit, limit)
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(focusedCard), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue(
                "the description of «$focusedCard» must end with «$expected»: $texts",
                texts.any { it.endsWith(expected) },
            )
        }
    }

    @Тогда("^третья строка карточки — «(.+)»$")
    fun theThirdLineIsTheLastIntake(line: String) {
        assertEquals(line, lastIntakeLine(focusedCard))
    }

    @Тогда("^вместо шеврона перехода стоит кнопка «([^»]+)»$")
    fun theTakeButtonStandsInsteadOfTheChevron(button: String) {
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(focusedCard), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("the on-demand card must carry «$button»: $texts", button in texts)
        }
        MedsListStand.ask(world) { test ->
            test.onNode(takeButton(focusedCard), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // --- Остаток и период в карточке ----------------------------------------------

    @Тогда("^в карточке «([^»]+)» показано «([^»]+)»$")
    fun theCardShows(medication: String, text: String) {
        MedsListStand.ask(world) { test ->
            val node = test
                .onNode(hasTestTag(TAG_TRAILING_LINE) and hasAnyAncestor(card(medication)), useUnmergedTree = true)
                .fetchSemanticsNode()
            val shown = MedsProbe.textsOf(node).single()
            assertEquals("the card of «$medication» must show «$text»", text, shown)
        }
    }

    // --- «Принять» в списке --------------------------------------------------------

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «Принять» в карточке «([^»]+)»$")
    fun thePersonPressesTakeInThatCard(time: String, medication: String) {
        focusedCard = medication
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        val id = world.medications.get(world.medicationNamed(medication)).id
        intents = MedsListStand.act(world) { test ->
            test.onNode(takeButton(medication), useUnmergedTree = true).performClick()
        }
        assertTrue(
            "the card of «$medication» must ask to create the intake: $intents",
            MedsIntent.TakeNow(id) in intents,
        )
    }

    // «Появляется принятый приём …» объявлено в общем артефакте (`TodayPrnSteps`): тем же
    // следствием «Принять» пользуется экран «Сегодня», и второе объявление погасило бы
    // этот прогон целиком.

    @Тогда("^строка карточки меняется на «(.+)»$")
    fun theCardLineChangesTo(line: String) {
        assertEquals(line, lastIntakeLine(world.medicationNamed(focusedCard)))
    }

    @Когда("^человек смотрит на карточку «([^»]+)» в (\\d{2}:\\d{2})$")
    fun thePersonLooksAtThatCard(medication: String, time: String) {
        focusedCard = medication
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        MedsListStand.ask(world) { }
    }

    @Тогда("^кнопка «([^»]+)» недоступна$")
    fun thatButtonIsDisabled(button: String) {
        assertEquals(button, context.getString(UiR.string.action_take))
        val id = world.medications.get(world.medicationNamed(focusedCard)).id
        val card = MedsListStand.state(world).items.first { it.medicationId == id }
        assertFalse("the taken count must reach the daily limit (ПР-7)", requireNotNull(card.asNeeded).takeEnabled)
        // Признак недоступности живёт у слитого узла кнопки: у подписи нажатий нет вовсе.
        MedsListStand.ask(world) { test ->
            test.onNodeWithText(button).assertIsNotEnabled()
        }
    }

    // --- Архив ---------------------------------------------------------------------

    @Тогда("^в списке активных лекарств его нет$")
    fun theMedicationIsGoneFromTheActiveList() {
        val name = world.medications.last
        val id = world.medications.get(name).id
        val state = MedsListStand.state(world)
        assertTrue(
            "the archived medication must leave the active list (ПР-11)",
            state.items.none { it.medicationId == id },
        )
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_MEDICATION_CARD, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^счётчик «Активные» уменьшается на единицу$")
    fun theActiveCounterDropsByOne() {
        val before = requireNotNull(world.medicationCountsBefore) { "the archive action was never performed" }
        val after = MedsListStand.state(world).activeCount
        assertEquals("the active counter must drop by one", before.active - 1, after)
        val label = context.getString(R.string.meds_tab_active, after)
        MedsListStand.ask(world) { test -> test.onNodeWithText(label).assertIsDisplayed() }
    }

    @Тогда("^счётчик «Архив» увеличивается на единицу$")
    fun theArchiveCounterGrowsByOne() {
        val before = requireNotNull(world.medicationCountsBefore) { "the archive action was never performed" }
        val after = MedsListStand.state(world).archiveCount
        assertEquals("the archive counter must grow by one", before.archived + 1, after)
        val label = context.getString(R.string.meds_tab_archive, after)
        MedsListStand.ask(world) { test -> test.onNodeWithText(label).assertIsDisplayed() }
    }

    // --- Переходы --------------------------------------------------------------------

    @Когда("^он нажимает круглую кнопку добавления$")
    fun thePersonPressesTheAddButton() {
        intents = MedsListStand.act(world) { test ->
            test.onNodeWithTag(TAG_ADD_MEDICATION).performClick()
        }
    }

    @Тогда("^открывается форма с заголовком «([^»]+)»$")
    fun theFormOpensWithThatTitle(title: String) {
        // Нажатие отдаёт намерение, а маршрут собирает `app`: здесь проверяется контракт
        // экрана. Заголовок же принадлежит форме этого модуля, и он проверяется у неё.
        assertTrue(
            "the add button must open the form: $intents",
            MedsIntent.AddMedication in intents,
        )
        MedsFormStand.ask(world) { test ->
            val header = MedsProbe.texts(test, TAG_SCREEN_HEADER)
            assertTrue("the fresh form must be titled «$title»: $header", title in header)
        }
    }

    @Когда("^он нажимает на карточку «([^»]+)»$")
    fun thePersonPressesThatCard(medication: String) {
        // Сценарий называет лекарство, не заводя его Предысторией: список должен его
        // показывать, поэтому мир доводится до названного состояния тем же расписанием,
        // что даёт лекарству таблица первого сценария задачи.
        if (RussianNames.resolve(medication, world.medications.names()) == null) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(LocalTime.of(8, 0)),
            )
        }
        intents = MedsListStand.act(world) { test ->
            test.onNode(card(medication), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    @Тогда("^открывается карточка лекарства «([^»]+)»$")
    fun theMedicationCardOpens(medication: String) {
        // Нажатие отдаёт намерение перехода — его разбирает маршрут `app`; сама карточка
        // лекарства — экран SKL-10, и её состав в объём SKL-09 не входит (`TDD.md §2`).
        val id = world.medications.get(world.medicationNamed(medication)).id
        assertTrue(
            "the tap on the card must open the medication «$medication»: $intents",
            MedsIntent.OpenMedication(id) in intents,
        )
    }

    // --- Обвязка -----------------------------------------------------------------------

    private fun headerTexts(): List<String> = MedsListStand.ask(world) { test ->
        MedsProbe.texts(test, TAG_SCREEN_HEADER)
    }

    private fun card(medication: String) = MedsListStand.card(world, medication)

    private fun takeButton(medication: String) = hasText(context.getString(UiR.string.action_take)) and
        hasAnyAncestor(card(medication))

    private fun lastIntakeLine(medication: String): String = MedsListStand.ask(world) { test ->
        val node = test
            .onNode(hasTestTag(TAG_LAST_INTAKE) and hasAnyAncestor(card(medication)), useUnmergedTree = true)
            .fetchSemanticsNode()
        MedsProbe.textsOf(node).single()
    }

    private companion object {
        const val FIVE = 5
    }
}
