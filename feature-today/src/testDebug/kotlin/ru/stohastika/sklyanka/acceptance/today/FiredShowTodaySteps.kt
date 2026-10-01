package ru.stohastika.sklyanka.acceptance.today

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.today.TodayLayout
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-88 — «Сегодня» не меняется от снятия показа (ПМ-22, ОВ-109).
 *
 * Приём после подавления остаётся ожидающимся и отмечаемым, и экран показывает его
 * обычной просроченной карточкой: состояние собирает настоящий домен ([TodayStand]),
 * а проверяются собранная композиция и её ресурсы.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class FiredShowTodaySteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Лекарство, о котором говорила проверка карточки: ею же отвечает проверка отсутствия пометки. */
    private var shownMedication: String? = null

    @Тогда("^приём показан обычной просроченной карточкой с опозданием$")
    fun theIntakeIsShownAsAnOrdinaryOverdueCard() {
        val intake = world.intakes.currentOrScheduled()
        val medication = medicationOf(intake.medicationId)
        shownMedication = medication
        assertEquals(
            "an overdue waiting intake is shown by the ordinary due-now card (ОВ-35)",
            IntakePresentation.DUE_NOW,
            itemOf(intake.id)?.presentation,
        )
        assertEquals("the suppressed show must not change the intake", IntakeStatus.PENDING, intake.status)
        TodayScreenProbe.ask(world) { test ->
            test.onNode(TodayScreenProbe.card(world, medication), useUnmergedTree = true).assertIsDisplayed()
            test.onNode(
                hasText(string(UiR.string.action_taken)) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        }
    }

    @И("^отдельной пометки о том, что уведомление не показывали, на карточке нет$")
    fun theCardHasNoDeliveryNote() {
        val medication = requireNotNull(shownMedication) { "the card check must run first" }
        val name = medicationLabel(medication)
        val texts = TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { node -> TodayScreenProbe.textsOf(node).any { it.contains(name) } }
                .flatMap(TodayScreenProbe::textsOf)
        }
        assertTrue("the card must be on the screen", texts.isNotEmpty())
        assertTrue(
            "the card must carry no note about the suppressed delivery: $texts",
            texts.none { text -> text.contains("не показано") || text.contains("уведомлен") },
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /** Название лекарства в модели экрана: сценарий зовёт «Магний B6», карточка — `med-N`. */
    private fun medicationOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private fun medicationLabel(medication: String): String = TodayScreenProbe.displayName(world, medication)

    private fun itemOf(intakeId: String): IntakeItem? = allItems().firstOrNull { it.id == intakeId }

    private fun allItems(): List<IntakeItem> = when (val layout = TodayStand.agenda(world).layout) {
        is TodayLayout.Plain -> layout.groups
        is TodayLayout.Dense -> layout.sections.flatMap { it.groups }
    }.flatMap { it.items }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)
}
