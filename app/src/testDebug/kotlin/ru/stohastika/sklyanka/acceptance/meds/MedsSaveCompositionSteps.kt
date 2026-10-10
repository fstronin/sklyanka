package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.MedicationUnits
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.today.TAG_LANE_HEADER
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Шаги сценарной приёмки «сохранённый курс сразу даёт приёмы на сегодня» (SKL-09,
 * группа `@save`, `@composition`; заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужны форма и «Сегодня» одновременно: форма
 * живёт в `feature-meds`, «Сегодня» — в `feature-today`, и только здесь оба экрана
 * существуют сразу (`SKL-26/TDD.md §4.3`). Сохранение — настоящий `world.save`
 * (`SaveMedicationUseCase` с тем же портом планирования, что в продукте), проверка
 * «Сегодня» — `TodayFromWorld` тем же доменом.
 *
 * «Форма закрывается» — маршрут `app`: стенд собирает экраны, а не навигацию. Проверяется
 * условие маршрута: форма отдала `Save`, и сохранение состоялось.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedsSaveCompositionSteps(private val world: AcceptanceWorld) {

    private var state: MedFormUiState? = null
    private var saved: SaveResult? = null
    private var savedMedication: String = ""

    // --- Предыстория: заполненная форма -----------------------------------------

    @Допустим(
        "^человек заполнил форму: название «(.+?)», форма выпуска «(.+?)», вид «(.+?)», " +
            "доза (\\d+) \\S+, дозировка (\\d+) (\\S+), режим «(.+?)», " +
            "времена «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})», " +
            "связь с едой «(.+?)», начало (.+?), «Принимать бессрочно» включено$",
    )
    @Suppress("LongParameterList")
    fun thePersonFilledTheForm(
        name: String,
        form: String,
        shape: String,
        dose: Int,
        strength: Int,
        strengthUnit: String,
        mode: String,
        first: String,
        second: String,
        food: String,
        start: String,
    ) {
        val draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            name = name,
            form = releaseForm(form),
            shape = tabletShape(shape),
            doseAmount = BigDecimal(dose),
            strength = BigDecimal(strength),
            strengthUnit = MedicationUnits.strengthUnit(strengthUnit),
            foodRelation = foodRelation(food),
            scheduleMode = scheduleMode(mode),
            times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
            startDate = world.dateOf(start),
        )
        savedMedication = name
        state = MedFormUiState.of(
            draft.normalized(),
            FormMode.Create,
            FormInputs(doseAmount = dose.toString(), strength = strength.toString()),
        )
        form { test ->
            // Значение поля лежит у строки ввода внутри поля, а не у самого поля.
            test.onNode(
                hasTestTag(TAG_NAME_FIELD) and hasAnyDescendant(hasText(name)),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    // --- Действие и следствие ----------------------------------------------------

    @Когда("^он нажимает «Сохранить лекарство» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonPressesSaveTheMedication(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val intents = form { test -> test.onNodeWithTag(TAG_SAVE_BUTTON).performClick() }
        assertTrue("the form must ask to save the medication: $intents", MedFormIntent.Save in intents)
        saved = world.save(requireNotNull(state) { "the form was not filled" }.draft)
    }

    @Тогда("^лекарство сохраняется и форма закрывается$")
    fun theMedicationIsSavedAndTheFormCloses() {
        val result = requireNotNull(saved) { "the scenario never pressed «Сохранить лекарство»" }
        assertTrue("the medication must be saved, not refused: $result", result is SaveResult.Saved)
        assertTrue(
            "the saved medication must be in the world: ${world.medications.names()}",
            world.medications.all().any { it.name == savedMedication },
        )
    }

    @Тогда("^на экране «Сегодня» появляется приём «([^»]+)» на (.+?), (\\d{2}:\\d{2})$")
    fun theIntakeAppearsOnToday(medication: String, date: String, time: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(date, time)
        val intake = world.intakes.of(name).firstOrNull { it.scheduledAt == moment }
        assertNotNull("no intake of «$medication» at $date, $time", intake)
        assertEquals("the intake must await its hour", IntakeStatus.PENDING, intake!!.status)
        onToday { test -> assertCardInLane(name, time, test) }
    }

    @Тогда("^приём на (\\d{2}:\\d{2}) этого дня уже прошёл и в списке ожидающих не значится$")
    fun thePastIntakeIsNotPending(time: String) {
        val name = requireNotNull(savedMedication.takeIf { it.isNotEmpty() }) { "no medication was saved" }
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        assertTrue(
            "the intake at $time must not await anything: ${world.intakes.of(name)}",
            world.intakes.of(name).none { it.scheduledAt == moment && it.isPending },
        )
        // И на собранном экране ожидающим он не показан: у лекарства ровно одна карточка —
        // на назначенное время.
        onToday { test ->
            val model = world.medications.get(name).name
            test.onAllNodes(hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)), useUnmergedTree = true)
                .assertCountEquals(1)
        }
    }

    // --- Обвязка ------------------------------------------------------------------

    /** Собранная форма: нажатие отдаёт намерения; заполнение уже задано Предысторией. */
    private fun form(action: (ComposeUiTest) -> Unit): List<MedFormIntent> {
        val intents = mutableListOf<MedFormIntent>()
        val shown = requireNotNull(state) { "the form was not opened" }
        runComposeUiTest {
            setContent {
                Pinned {
                    MedicationFormScreen(
                        state = shown,
                        onIntent = intents::add,
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
                }
            }
            action(this)
            waitForIdle()
        }
        return intents
    }

    private fun onToday(assertion: (ComposeUiTest) -> Unit) {
        runComposeUiTest {
            setContent { Pinned { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            assertion(this)
        }
    }

    /**
     * Карточка лекарства стоит под разделителем указанного времени — тем же способом,
     * каким это проверяет стенд «Сегодня»: разделитель — сосед карточки, и принадлежность
     * видна только расположением.
     */
    private fun assertCardInLane(medication: String, time: String, test: ComposeUiTest) {
        val model = world.medications.get(medication).name
        val card = test
            .onNode(hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)), useUnmergedTree = true)
            .fetchSemanticsNode()
        val lanes = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val header = lanes.first { time in textsOf(it) }
        assertTrue(
            "the card of «$medication» must stand under its lane $time",
            card.boundsInRoot.top >= header.boundsInRoot.top,
        )
        val nextLane = lanes.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        if (nextLane != null) {
            assertTrue("the card of «$medication» must stand above the next lane", card.boundsInRoot.top < nextLane)
        }
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun releaseForm(label: String): MedicationForm = when (label) {
        "Таблетка" -> MedicationForm.TABLET
        "Капсула" -> MedicationForm.CAPSULE
        "Спрей" -> MedicationForm.SPRAY
        "Сироп" -> MedicationForm.SYRUP
        "Капли" -> MedicationForm.DROPS
        else -> MedicationForm.INJECTION
    }

    private fun tabletShape(label: String): TabletShape = when (label) {
        "Круглая" -> TabletShape.ROUND
        "Круглая с риской" -> TabletShape.ROUND_SCORED
        "Овальная" -> TabletShape.OVAL
        "Продолговатая" -> TabletShape.OBLONG
        else -> TabletShape.TRIANGULAR
    }

    private fun foodRelation(label: String): FoodRelation = when (label) {
        "До еды" -> FoodRelation.BEFORE_MEAL
        "Во время" -> FoodRelation.WITH_MEAL
        "После" -> FoodRelation.AFTER_MEAL
        else -> FoodRelation.ANY
    }

    private fun scheduleMode(label: String): ScheduleMode =
        if (label == AS_NEEDED) ScheduleMode.PRN else ScheduleMode.SCHEDULED

    /** Язык интерфейса закреплён: даты и склонения читаются по-русски при любом телефоне (НФТ-43). */
    @Composable
    private fun Pinned(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val AS_NEEDED = "По потребности"
    }
}
