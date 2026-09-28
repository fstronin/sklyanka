package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.feature.today.TodayIntent
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD

/**
 * Экранные шаги приёмки перехода к карточке лекарства (SKL-08, группа `@navigation`;
 * заведены SKL-26).
 *
 * Экран отдаёт намерение `OpenMedication`, а карточку собирает маршрут `app` — на этом
 * уровне проверяется контракт экрана: нажатие мимо кнопки отметки ведёт к лекарству,
 * а не к отметке. Нажатие идёт действием самого узла карточки, а не попаданием
 * в координату: строка и говорит «не попадая по кнопке отметки».
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayNavigationScreenSteps(private val world: AcceptanceWorld) {

    private var focusedMedication: String = ""
    private var intents: List<TodayIntent> = emptyList()

    @Допустим("^показан приём «([^»]+)» на (.+?), (\\d{2}:\\d{2})$")
    fun thatIntakeIsShown(medication: String, date: String, time: String) {
        val name = world.medicationNamed(medication)
        world.intakes.pending(name, world.localMoment(date, time))
        focusedMedication = name
        TodayScreenProbe.ask(world) { test ->
            test.onNode(TodayScreenProbe.card(world, name), useUnmergedTree = true).fetchSemanticsNode()
        }
    }

    @Когда("^человек нажимает на карточку приёма, не попадая по кнопке отметки$")
    fun thePersonPressesTheCardBody() {
        val model = TodayScreenProbe.displayName(world, focusedMedication)
        intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    @Тогда("^открывается карточка лекарства «([^»]+)»$")
    fun theMedicationCardOpens(medication: String) {
        // Нажатие отдаёт намерение перехода — его разбирает маршрут `app`; выдумывать
        // здесь навигацию значило бы проверять копию маршрута, а не экран (SKL-26, TDD §4.3).
        val id = world.medications.get(world.medicationNamed(medication)).id
        assertTrue(
            "the tap on the card must open the medication «$medication»: $intents",
            TodayIntent.OpenMedication(id) in intents,
        )
    }
}
