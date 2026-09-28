package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.feature.today.TAG_LANE_HEADER
import ru.heuri.sklyanka.feature.today.TodayIntent
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.heuri.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.heuri.sklyanka.ui.component.TAG_MISSED_STATUS
import ru.heuri.sklyanka.ui.component.TAG_TAKEN_STATUS
import java.time.LocalTime
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки отметки приёма (SKL-08, группа `@screen`; заведены SKL-26).
 *
 * Нажатия идут по собранной композиции, а намерения экрана применяет к миру тот же
 * домен, что в продукте ([TodayScreenProbe]). Сценарий говорит о человеке у экрана,
 * и проверяется именно путь «человек нажал — приём отметился», а не вызов домена
 * в обход экрана.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class TodayMarkingSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Действия ---------------------------------------------------------------

    @Когда("^человек нажимает «(Принято|Пропустить)» в карточке «([^»]+)»$")
    fun thePersonPressesTheButton(button: String, medication: String) = press(button, medication)

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «(Принято|Пропустить)» в карточке «([^»]+)»$")
    fun thePersonPressesTheButtonAt(date: String, time: String, button: String, medication: String) {
        world.clock.moveTo(world.localMoment(date, time))
        press(button, medication)
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «(Принято|Пропустить)» в карточке «([^»]+)»(?: на телефоне)?$")
    fun thePersonPressesTheButtonTodayAt(time: String, button: String, medication: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        press(button, medication)
    }

    @Когда("^человек нажимает круглую кнопку-галочку в карточке «([^»]+)»$")
    fun thePersonPressesTheRoundCheck(medication: String) {
        val intake = pendingToday(world.medicationNamed(medication))
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasTestTag(TAG_MARK_BUTTON) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the round check must mark the intake of $medication: $intents",
            TodayIntent.MarkTaken(intake.id) in intents,
        )
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает на ту же карточку ещё раз$")
    fun thePersonTapsTheSameCardAgain(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        val medication = scenarioNameOf(world.intakes.current().medicationId)
        val model = TodayScreenProbe.displayName(world, medication)
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "a repeated tap must not mark the intake again: $intents",
            intents.none { it is TodayIntent.MarkTaken || it is TodayIntent.MarkSkipped },
        )
    }

    @Когда("^человек смотрит на карточку приёма «([^»]+)» на (\\d{2}:\\d{2})$")
    fun thePersonLooksAtThatCard(medication: String, time: String) {
        world.intakes.pending(world.medicationNamed(medication), world.clock.today().atTime(LocalTime.parse(time)))
    }

    @Когда("^человек включает телефон и открывает экран «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonTurnsThePhoneOnAndOpensToday(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Продукт проходит по просроченным приёмам при открытии экрана (НАП-34):
        // без этого прохода «открытие» в сценарии не делало бы ничего.
        world.sweep(SweepReason.APP_START)
        TodayScreenProbe.ask(world) { }
    }

    // --- Проверки ---------------------------------------------------------------

    @Тогда("^в карточке вместо кнопок показано фактическое время отметки «(\\d{2}:\\d{2})»$")
    fun theCardShowsTheMarkedMoment(time: String) {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test ->
            // Тег состояния — внутри карточки, а карточка помечена как узел слияния:
            // в мёрженом дереве такого узла нет, и читается он по немёрженому.
            val status = test.onNode(
                hasTestTag(TAG_TAKEN_STATUS) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            )
            assertTrue(
                "the card must show the moment of the mark $time: ${statusText(status)}",
                time in TodayScreenProbe.textsOf(status.fetchSemanticsNode()),
            )
            assertNoMarkingAffordances(test, medication)
        }
    }

    @Тогда("^вместо пары кнопок «Принято» и «Пропустить» показана круглая кнопка-галочка$")
    fun theRoundCheckReplacesTheActions() {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test ->
            test.onNode(
                hasTestTag(TAG_MARK_BUTTON) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            assertNoActionLabels(test, medication)
        }
    }

    @Тогда("^у этой кнопки есть подпись для чтения с экрана «([^»]+)»$")
    fun theRoundCheckIsNamedForTheScreenReader(description: String) {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNode(
                hasTestTag(TAG_MARK_BUTTON) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
            // Сценарий называет лекарство в падеже фразы («Отметить приём Лозартана»),
            // а подпись собирается из имени модели: сверяется формулировка, а имя
            // подставляется тем, которым экран его называет (НФТ-19).
            val expected = description.replace(
                Regex("""\S*${Regex.escape(medication)}\S*"""),
                TodayScreenProbe.displayName(world, medication),
            )
            assertEquals("the round check must be named for the screen reader", expected, spoken)
        }
    }

    @Тогда("^карточка «([^»]+)» остаётся в группе «(\\d{2}:\\d{2})»$")
    fun theCardStaysInThatLane(medication: String, time: String) {
        TodayScreenProbe.ask(world) { test ->
            val card = test.onNode(TodayScreenProbe.card(world, medication), useUnmergedTree = true)
                .fetchSemanticsNode()
            val lanes = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
            val header = lanes.first { time in TodayScreenProbe.textsOf(it) }
            assertTrue(
                "the card of $medication must sit under its lane $time",
                card.boundsInRoot.top >= header.boundsInRoot.top,
            )
            val nextLane = lanes.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
            if (nextLane != null) {
                assertTrue(
                    "the card of $medication must sit above the next lane",
                    card.boundsInRoot.top < nextLane,
                )
            }
        }
    }

    @Тогда("^карточка показана приглушённой, без кнопок отметки$")
    fun theCardIsDimmedWithoutMarkingButtons() {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test -> assertNoMarkingAffordances(test, medication) }
    }

    @Тогда("^статус приёма подписан текстом, а не только цветом$")
    fun theStatusIsSpelledOut() {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test ->
            val status = test.onNode(
                hasTestTag(TAG_MISSED_STATUS) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            )
            assertTrue("the status of $medication must be spelled out", statusText(status).isNotBlank())
        }
    }

    @Тогда("^отметить этот приём с экрана нельзя$")
    fun theIntakeCannotBeMarkedFromTheScreen() {
        val medication = currentMedication()
        TodayScreenProbe.ask(world) { test -> assertNoMarkingAffordances(test, medication) }
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun press(button: String, medication: String) {
        val intake = pendingToday(world.medicationNamed(medication))
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasText(button) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).performClick()
        }
        val expected = if (button == context.getString(UiR.string.action_taken)) {
            TodayIntent.MarkTaken(intake.id)
        } else {
            TodayIntent.MarkSkipped(intake.id)
        }
        assertTrue("the card of $medication must offer «$button»: $intents", expected in intents)
    }

    /** Приём, который сценарий сейчас видит на экране: ещё не отмеченный и сегодняшний. */
    private fun pendingToday(medication: String): Intake = requireNotNull(
        world.intakes.pendingOf(medication).firstOrNull { it.scheduledAt.toLocalDate() == world.clock.today() },
    ) { "no pending intake of $medication on ${world.clock.today()}" }

    /** Лекарство последнего упомянутого приёма — в том виде, в каком его называет сценарий. */
    private fun currentMedication(): String = scenarioNameOf(world.intakes.current().medicationId)

    private fun scenarioNameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    /**
     * Без кнопок отметки карточка не оставляет способа отметить приём.
     *
     * Проверка идёт по немёрженому дереву: кнопка отметки у приёма с наступившим
     * временем живёт внутри карточки, и в мёрженом дереве её узла просто нет.
     */
    private fun assertNoMarkingAffordances(test: ComposeUiTest, medication: String) {
        test.onAllNodes(
            hasTestTag(TAG_MARK_BUTTON) and TodayScreenProbe.insideCard(world, medication),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        assertNoActionLabels(test, medication)
    }

    /** Пары кнопок «Принято» и «Пропустить» в карточке нет. */
    private fun assertNoActionLabels(test: ComposeUiTest, medication: String) {
        listOf(UiR.string.action_taken, UiR.string.action_skip).map(context::getString).forEach { label ->
            test.onAllNodes(
                hasText(label) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        }
    }

    private fun statusText(node: SemanticsNodeInteraction): String =
        TodayScreenProbe.textsOf(node.fetchSemanticsNode()).joinToString(" ").trim()
}
