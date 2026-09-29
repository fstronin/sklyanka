package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_PROGRESS_LINK
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Экранные шаги приёмки переходов на «Сегодня» (SKL-08 и SKL-31, группа `@navigation`;
 * заведены SKL-26).
 *
 * Экран отдаёт намерение (`OpenMedication` или `OpenHistory`), а переход собирает
 * маршрут `app` — на этом уровне проверяется контракт экрана: нажатие мимо кнопки
 * отметки ведёт к лекарству, а строка прогресса дня — к истории. Нажатие карточки идёт
 * действием самого узла, а не попаданием в координату: строка и говорит «не попадая
 * по кнопке отметки».
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

    // --- Строка прогресса дня — ссылка на историю (ОВ-84, SKL-31) ---------------------------

    @Тогда("^справа в (?:той же строке|строке прогресса) показано действие «([^»]+)»$")
    fun theActionIsShownInTheProgressRow(action: String) {
        // «›» — нарисованный значок и в семантику не попадает (ОВ-84): сверяется слово.
        val word = action.removeSuffix(" ›").trim()
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.texts(test, TAG_PROGRESS_LINK)
            assertTrue("the progress row must offer «$action»: $texts", word in texts)
        }
    }

    @Тогда("^вся строка прогресса — одна цель нажатия$")
    fun theWholeProgressRowIsOneTarget() {
        TodayScreenProbe.ask(world) { test ->
            val link = test.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).fetchSemanticsNode()
            val bar = test.onNodeWithTag(TAG_DAY_PROGRESS, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("the progress row must be clickable", link.config.contains(SemanticsActions.OnClick))
            assertEquals(
                "the link must span the progress row",
                bar.boundsInRoot.width,
                link.boundsInRoot.width,
                1f,
            )
            val height = with(link.layoutInfo.density) { link.size.height.toDp() }
            assertTrue(
                "the link target is $height, below ${SklyankaSizes.MinTouchTarget}",
                height >= SklyankaSizes.MinTouchTarget - SklyankaSizes.BorderThin,
            )
        }
    }

    @Когда("^человек нажимает на строку прогресса дня$")
    fun thePersonPressesTheDayProgressRow() {
        intents = TodayScreenProbe.act(world) { test ->
            test.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).performClick()
        }
    }

    @Тогда("^открывается история приёмов$")
    fun theHistoryOpens() {
        assertTrue(
            "the progress row must open the history: $intents",
            TodayIntent.OpenHistory in intents,
        )
    }

    @Тогда("^ссылки «([^»]+)» на экране нет$")
    fun theLinkIsAbsent(link: String) {
        val word = link.removeSuffix(" ›").trim()
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithText(word, useUnmergedTree = true).assertCountEquals(0)
        }
    }
}
