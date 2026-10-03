package ru.stohastika.sklyanka.acceptance.meds

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.steps.task.MedicationFormSteps
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки повторного открытия формы нового лекарства (SKL-57, `@screen`).
 *
 * Стенд собирает те же экраны, что маршрут раздела: список лекарств и форму — из
 * доменного черновика ([MedsFormStand], [MedsListStand]). Состояние «форма открыта»
 * живёт в шагах: строка Gherkin не может оставить композицию живой между шагами, и
 * каждый шаг собирает тот экран, который продукт показал бы сейчас. Повторное открытие
 * формы всегда начинается с нового черновика — это и проверяет сценарий; маршрут
 * `MedicationFormRoute` с настоящей моделью проверяет `MedsRouteTest` (TDD §7).
 *
 * Строка «человек сохраняет лекарство» объявлена в общем артефакте
 * (`MedicationFormSteps`) — второго объявления словарь шагов не допускает, — и нажатие
 * на собранной форме приходит сюда крючком `submitOnScreen`: «Когда» остаётся «Когда»,
 * а сохранение идёт настоящим `world.save`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsReopenScreenSteps(
    private val world: AcceptanceWorld,
    private val formSteps: MedicationFormSteps,
    private val cardSteps: MedCardScreenSteps,
) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Открыта ли форма сейчас: состояние маршрута, которое переживает шаг. */
    private var formOpen = false

    /** Чем кончилось последнее сохранение: «форма закрывается» отвечает по нему. */
    private var lastSave: SaveResult? = null

    /** Снимок числа лекарств до отмены: «форма закрылась без сохранения». */
    private var countBeforeCancel: Int = 0

    init {
        // Экранный прогон подставляет нажатие на собранной форме: доменный шаг
        // «человек сохраняет лекарство» остаётся один на все прогоны.
        formSteps.submitOnScreen = { pressSave() }
    }

    // --- Предыстория ---------------------------------------------------------------

    @Допустим("^человек находится на списке лекарств$")
    fun thePersonIsOnTheMedicationList() {
        formOpen = false
        MedsListStand.ask(world) { test -> test.onNodeWithTag(TAG_ADD_MEDICATION).assertIsDisplayed() }
    }

    @Допустим("^открыта форма нового лекарства$")
    fun theNewMedicationFormIsOpen() {
        openNewForm()
    }

    // --- Действия ------------------------------------------------------------------

    @Когда("^человек нажимает «Добавить лекарство»$")
    fun thePersonPressesAdd() {
        pressAdd()
    }

    @Когда("^человек снова нажимает «Добавить лекарство»$")
    fun thePersonPressesAddAgain() {
        pressAdd()
    }

    @Когда("^в поле названия введено «([^»]+)»$")
    fun theNameIsTyped(name: String) {
        val intents = MedsFormStand.act(world) { test -> test.onNameField().performTextInput(name) }
        assertTrue(
            "typing must change the name: $intents",
            intents.any { it is MedFormIntent.NameChanged && it.value == name },
        )
        assertEquals("the typed name must stay in the draft", name, MedsFormStand.open(world).draft.name)
        fillMinimalCourse()
    }

    @Когда("^человек нажимает «Отмена»$")
    fun thePersonPressesCancel() {
        countBeforeCancel = world.medications.names().size
        MedsFormStand.cancel(world, context.getString(UiR.string.action_cancel))
        assertTrue("«Отмена» must close the route", MedsFormStand.cancelled)
        formOpen = false
    }

    @Когда("^нажимает «Изменить»$")
    fun thePersonPressesEdit() {
        val medication = requireNotNull(cardSteps.openedMedication.takeIf { it.isNotEmpty() }) {
            "the scenario must open a card before pressing «Изменить»"
        }
        val intents = MedCardStand.act(world, medication) { test -> test.onNodeWithTag(TAG_EDIT).performClick() }
        assertTrue(
            "«Изменить» must ask to open the form: $intents",
            intents.any { it is MedCardIntent.Edit },
        )
        val card = world.medications.get(world.medicationNamed(medication))
        MedsFormStand.showEdit(world, card)
        formOpen = true
    }

    @Когда("^человек только что сохранил новое лекарство «([^»]+)» и вернулся в список$")
    fun thePersonHasJustSavedANewMedication(name: String) {
        openNewForm()
        MedsFormStand.act(world) { test -> test.onNameField().performTextInput(name) }
        fillMinimalCourse()
        pressSave()
        assertTrue("the save must close the form", !formOpen)
    }

    // --- Проверки ------------------------------------------------------------------

    @Тогда("^открывается форма нового лекарства$")
    fun theNewMedicationFormOpens() {
        assertFormOpen()
        assertNameValue("")
    }

    @Тогда("^форма остаётся открытой$")
    fun theFormStaysOpen() {
        assertTrue("the form must stay open after the frame settles", formOpen)
        assertFormOpen()
    }

    @Тогда("^поле названия пусто$")
    fun theNameFieldIsEmpty() {
        assertNameValue("")
    }

    @Тогда("^форма закрывается$")
    fun theFormCloses() {
        assertTrue("the save must succeed before the form closes: $lastSave", lastSave is SaveResult.Saved)
        assertTrue("the form must be closed by the route", !formOpen)
    }

    @Тогда("^человек снова видит список лекарств$")
    fun thePersonSeesTheListAgain() {
        assertTrue("the list must be shown, not the form", !formOpen)
        MedsListStand.ask(world) { test -> test.onNodeWithTag(TAG_ADD_MEDICATION).assertIsDisplayed() }
    }

    @Тогда("^в списке два лекарства$")
    fun theListHoldsTwoMedications() {
        assertEquals("the saved course must reach the list", 2, MedsListStand.state(world).items.size)
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_MEDICATION_CARD, useUnmergedTree = true).assertCountEquals(2)
        }
    }

    @Тогда("^форма закрывается без сохранения$")
    fun theFormClosesWithoutSaving() {
        assertTrue("«Отмена» must close the form", !formOpen)
        assertEquals("cancelling must not save anything", countBeforeCancel, world.medications.names().size)
    }

    @Тогда("^открывается форма лекарства «([^»]+)»$")
    fun theEditFormOpens(medication: String) {
        assertFormOpen()
        val name = world.medicationNamed(medication)
        val mode = MedsFormStand.open(world).mode
        assertEquals(
            "the edit form must open the chosen course",
            world.medications.get(name).name,
            (mode as? FormMode.Edit)?.title,
        )
    }

    @Тогда("^в поле названия — «([^»]+)»$")
    fun theNameFieldReads(name: String) {
        // Сценарий зовёт лекарство «Л», модель хранит обезличенное имя: сверяется то,
        // что показывает экран, — имя модели (НФТ-19).
        assertNameValue(MedsListStand.displayName(world, name))
    }

    // --- Обвязка -------------------------------------------------------------------

    private fun pressAdd() {
        val intents = MedsListStand.act(world) { test -> test.onNodeWithTag(TAG_ADD_MEDICATION).performClick() }
        assertTrue("«+» must ask to open the form: $intents", intents.any { it is MedsIntent.AddMedication })
        openNewForm()
    }

    /**
     * Сохранение с нажатием на собранной форме.
     *
     * Черновик берётся у стенда: то, что человек видел на форме, и есть то, что
     * сохраняется. Отказ не закрывает форму — сценарий скажет об этом сам.
     */
    private fun pressSave() {
        val current = MedsFormStand.open(world)
        val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_SAVE_BUTTON).performClick() }
        assertTrue(
            "the form must ask to save: $intents (name=«${current.draft.name}», enabled=${current.saveEnabled})",
            MedFormIntent.Save in intents,
        )
        lastSave = world.save(MedsFormStand.open(world).draft)
        formOpen = lastSave !is SaveResult.Saved
    }

    private fun openNewForm() {
        val profileId = world.profiles.get(world.profiles.active).id
        val draft = MedicationDraft.new(profileId, world.clock.today())
        MedsFormStand.showDraft(world, draft.normalized())
        formOpen = true
        lastSave = null
    }

    private fun assertFormOpen() {
        assertTrue("the form must be open", formOpen)
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_NAME_FIELD).assertExists() }
    }

    private fun assertNameValue(expected: String) {
        assertTrue("the form must be open", formOpen)
        val actual = MedsFormStand.ask(world) { test ->
            test.onNameField().fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        }
        assertEquals("the name field value", expected, actual)
    }

    /**
     * Поле ввода названия.
     *
     * Тег стоит на контейнере поля, а действие ввода — на внутреннем узле: адресуемся
     * по действию, а не копируем устройство компонента дизайн-системы.
     */
    private fun ComposeUiTest.onNameField() =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)), useUnmergedTree = true)

    /**
     * Минимальный курс рядом с названием.
     *
     * Сценарий называет только название, а форма без дозы и времени не сохраняется
     * (`ValidateMedicationDraft`): «человек заполнил форму» — предпосылка строки,
     * а не отдельная проверка, и стенд дополняет её тем же путём, каким это сделал бы
     * человек. Наблюдаемый предмет сценария — открытие формы, а не правила SKL-09.
     */
    private fun fillMinimalCourse() {
        MedsFormStand.intent(world, MedFormIntent.DoseAmountChanged(MINIMAL_DOSE))
        if (MedsFormStand.open(world).draft.times.isEmpty()) MedsFormStand.intent(world, MedFormIntent.TimeAdded)
    }

    private companion object {
        const val MINIMAL_DOSE = "2"
    }
}
