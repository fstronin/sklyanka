package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.today.PartOfDay
import ru.heuri.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.heuri.sklyanka.feature.today.TAG_SECTION_COLLAPSED
import ru.heuri.sklyanka.feature.today.TAG_SECTION_EXPANDED
import ru.heuri.sklyanka.feature.today.TodayIntent
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD

/**
 * Экранные шаги приёмки плотного дня (SKL-08, группа `@dense`; заведены SKL-26).
 *
 * Свёртка и разворот — зрительное состояние экрана: в продукте его ведёт `TodayViewModel`,
 * в приёмке — накопленные переключения [TodayScreenProbe] (`TDD.md §5.2`). Проверки
 * задают вопросы собранной композиции: какие секции свёрнуты, что в них написано и что
 * появляется после нажатия.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayDenseScreenSteps(private val world: AcceptanceWorld) {

    /** Свёрнутая строка, о которой сценарий говорил последней: «человек нажимает на эту». */
    private var lastCollapsed: String? = null

    // --- Свёрнутые и развёрнутые секции --------------------------------------------------

    @Тогда("^(?:утро показано|вечер показан) свёрнутой строкой «([^»]+)»(?: с подписью «([^»]+)»)?$")
    fun thatPartOfDayIsCollapsed(title: String, caption: String?) {
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.textsOf(collapsedRow(test, title))
            caption?.let { assertTrue("the collapsed «$title» must read «$it»: $texts", it in texts) }
        }
        lastCollapsed = title
    }

    @Тогда("^день показан развёрнутым разделителем «([^»]+)» со счётчиком «([^»]+)»$")
    fun theDayIsExpandedWithThatCounter(title: String, counter: String) {
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.textsOf(expandedDivider(test, title))
            assertTrue("the divider «$title» must count «$counter»: $texts", counter in texts)
        }
    }

    @Когда("^человек нажимает на эту строку$")
    fun thePersonTapsThatRow() {
        val title = requireCollapsedTitle()
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasTestTag(TAG_SECTION_COLLAPSED) and hasAnyDescendant(hasText(title)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue("the row «$title» must ask to unfold: $intents", intents.any { it is TodayIntent.ToggleSection })
    }

    @Тогда("^группа разворачивается в список из ([а-я]+) приёмов$")
    fun theGroupUnfoldsIntoThatManyIntakes(count: String) {
        val expected = RussianDates.count(count)
        TodayScreenProbe.ask(world) { test ->
            val cards = cardsUnder(test, requireCollapsedTitle())
            assertEquals("the unfolded group must show $expected intakes", expected, cards.size)
        }
    }

    @Тогда("^каждый приём показан отдельной карточкой$")
    fun eachIntakeIsASeparateCard() {
        TodayScreenProbe.ask(world) { test ->
            val cards = cardsUnder(test, requireCollapsedTitle())
            assertTrue("the scenario unfolded nothing", cards.isNotEmpty())
            val tops = cards.map { it.boundsInRoot.top }
            assertEquals("each intake must sit in a card of its own: $tops", tops.size, tops.toSet().size)
            val names = eveningMedicationNames()
            cards.forEach { card ->
                val texts = TodayScreenProbe.textsOf(card)
                assertEquals("each card must hold exactly one intake: $texts", 1, names.count { it in texts })
            }
        }
    }

    // --- Подпись дня и что в неё не входит ------------------------------------------------

    @Тогда("^показана подпись «([^»]+)»$")
    fun theCaptionIs(caption: String) {
        TodayScreenProbe.ask(world) { test ->
            val shown = TodayScreenProbe.texts(test, TAG_DAY_PROGRESS)
            assertTrue("the day progress must read «$caption»: $shown", caption in shown)
        }
    }

    @Тогда("^лекарства по потребности в число «([^»]+)» не входят$")
    fun theOnDemandMedicationsAreNotCounted(countText: String) {
        val count = requireNotNull(Regex("\\d+").find(countText)) { "no number in «$countText»" }.value.toInt()
        assertEquals(
            "the caption must count scheduled medications only",
            count,
            TodayStand.progress(world).medicationCount,
        )
        assertTrue(
            "the scenario must hold on-demand medications",
            world.medications.all().any { it.scheduleMode == ScheduleMode.PRN && !it.isArchived },
        )
    }

    // --- Обвязка --------------------------------------------------------------------------

    /**
     * Имена лекарств, чьи приёмы сценарий видит в вечерней секции.
     *
     * Без повторов: у лекарства бывает два вечерних приёма, и в карточке оно
     * всё равно названо один раз — считаются имена, а не приёмы.
     */
    private fun eveningMedicationNames(): List<String> = world.intakes
        .ofProfile(world.profiles, world.profiles.active)
        .filter { it.scheduledAt.toLocalDate() == world.clock.today() }
        .filter { PartOfDay.of(it.scheduledAt.toLocalTime()) == PartOfDay.EVENING }
        .mapNotNull { world.medications.byId(it.medicationId)?.name }
        .distinct()

    private fun requireCollapsedTitle(): String =
        requireNotNull(lastCollapsed) { "the scenario never named a collapsed row" }

    private fun collapsedRow(test: ComposeUiTest, title: String): SemanticsNode = test.onNode(
        hasTestTag(TAG_SECTION_COLLAPSED) and hasAnyDescendant(hasText(title)),
        useUnmergedTree = true,
    ).fetchSemanticsNode()

    private fun expandedDivider(test: ComposeUiTest, title: String): SemanticsNode = test.onNode(
        hasTestTag(TAG_SECTION_EXPANDED) and hasAnyDescendant(hasText(title)),
        useUnmergedTree = true,
    ).fetchSemanticsNode()

    /**
     * Карточки приёмов под развёрнутым разделителем.
     *
     * Секция вечера — последняя: ниже её разделителя лежат только её ленты. Блок
     * «по потребности» идёт ещё ниже и свои карточки помечает другим тегом.
     */
    private fun cardsUnder(test: ComposeUiTest, title: String): List<SemanticsNode> {
        val divider = expandedDivider(test, title)
        return test.onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= divider.boundsInRoot.top }
    }
}
