package ru.stohastika.sklyanka.acceptance.meds

import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.feature.meds.FakeProCache
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormRoute
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_CONFIRM
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DIALOG
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DISMISS
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_TITLE
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * Экранные шаги приёмки SKL-154: системная «Назад» на форме лекарства (RT-1, Р-87 п. 1).
 *
 * Стенд собирает **настоящий маршрут** [MedicationFormRoute] с настоящей моделью
 * [MedicationFormViewModel] на мире сценария: перехват «Назад» на грязном черновике,
 * диалог подтверждения и его действия — код продукта, а не пересказ разметки. Системная
 * «Назад» нажимается настоящим диспетчером, чей владелец подставляется композицией
 * (приём `MedsFormStand.systemBack`, Р-54); плейсхолдер обработчика раздела стоит снаружи
 * маршрута и раньше него, поэтому чистый черновик закрывает именно раздел.
 *
 * Композиция живёт внутри одного шага (конвенция `TodayScreenProbe`): маршрут открывает
 * форму заново на каждую сборку, поэтому действие повторяет ввод человека внутри своего
 * блока — «Остаться» проверяется на той же форме, что собирает продукт.
 * Путь «Сбросить» и чистой формы исполняют сценарии `SKL-137.feature` (стенд `app`).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk154FormBackSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Наблюдения, записанные действиями -------------------------------------------------

    /** Название, введённое человеком: действие повторяет его на каждой новой композиции. */
    private var typedName: String = ""
    private var dialogVisible = false
    private var dialogTitle = ""
    private var formUnderDialog = false
    private var nameUnderDialog = ""
    private var dialogGoneAfterStay = false
    private var formAfterStay = false
    private var nameAfterStay = ""
    private var closedWithoutSaving = false
    private var medicationsBefore = 0

    /** Детерминированные, но разные идентификаторы сохранений (приём `AcceptanceWorld.save`). */
    private var savedForms = 0

    // --- Предыстория и действия --------------------------------------------------------------

    @Допустим("^человек открыл форму нового лекарства на маршруте$")
    fun thePersonOpenedTheNewMedicationFormOnTheRoute() {
        medicationsBefore = world.medications.all().size
        onRoute(medicationId = null) { test, _ -> test.settleUntilForm() }
    }

    @Когда("^он вводит название «([^»]+)» и нажимает системную «Назад»$")
    fun heTypesTheNameAndPressesSystemBack(name: String) {
        typedName = name
        onRoute(medicationId = null) { test, owner ->
            test.settleUntilForm()
            test.typeIntoName(name)
            owner.pressBack()
            test.settle()
            dialogVisible = test.dialogNodes().isNotEmpty()
            dialogTitle = test.dialogTitle()
            formUnderDialog = test.formNodes().isNotEmpty()
            nameUnderDialog = test.nameInField()
        }
    }

    @Когда("^он нажимает системную «Назад» и выбирает в диалоге «Остаться»$")
    fun hePressesSystemBackAndChoosesToStay() {
        onRoute(medicationId = null) { test, owner ->
            test.settleUntilForm()
            test.typeIntoName(typedName)
            owner.pressBack()
            test.settle()
            assertTrue("«Назад» на грязной форме обязана поднять диалог", test.dialogNodes().isNotEmpty())
            test.onNodeWithTag(TAG_CONFIRM_DISMISS).performClick()
            test.settle()
            dialogGoneAfterStay = test.dialogNodes().isEmpty()
            formAfterStay = test.formNodes().isNotEmpty()
            nameAfterStay = test.nameInField()
        }
    }

    @Когда("^он выбирает в диалоге подтверждения «Сбросить»$")
    fun heChoosesToDiscardInTheConfirmationDialog() {
        onRoute(medicationId = null) { test, owner ->
            test.settleUntilForm()
            test.typeIntoName(typedName)
            owner.pressBack()
            test.settle()
            assertTrue("«Назад» на грязной форме обязана поднять диалог", test.dialogNodes().isNotEmpty())
            test.onNodeWithTag(TAG_CONFIRM_CONFIRM).performClick()
            test.settle()
            assertTrue("«Сбросить» обязано закрыть диалог", test.dialogNodes().isEmpty())
        }
    }

    @Когда("^человек открывает правку лекарства «([^»]+)» и нажимает системную «Назад»$")
    fun thePersonOpensTheMedicationEditAndPressesSystemBack(medication: String) {
        val medicationId = world.medications.get(world.medicationNamed(medication)).id
        medicationsBefore = world.medications.all().size
        onRoute(medicationId) { test, owner ->
            test.settleUntilForm()
            owner.pressBack()
            test.settle()
            dialogVisible = test.dialogNodes().isNotEmpty()
        }
    }

    // --- Проверки ----------------------------------------------------------------------------

    @Тогда("^над формой открывается диалог подтверждения сброса$")
    fun theDiscardDialogOpensOverTheForm() {
        assertTrue("системная «Назад» на грязной форме обязана открыть диалог подтверждения", dialogVisible)
        assertEquals(
            "диалог обязан быть озаглавлен продуктовым ресурсом",
            context.getString(MedsR.string.form_discard_title),
            dialogTitle,
        )
    }

    @Тогда("^форма с введённым названием остаётся на экране$")
    fun theFormWithTheTypedNameStaysOnScreen() {
        assertTrue("до выбора человека форма обязана оставаться на экране", formUnderDialog)
        assertEquals("ввод обязан остаться в поле", typedName, nameUnderDialog)
    }

    @Тогда("^диалог закрывается, а форма и введённое название сохраняются$")
    fun theDialogClosesAndTheFormKeepsTheInput() {
        assertTrue("«Остаться» обязано закрыть диалог", dialogGoneAfterStay)
        assertTrue("«Остаться» обязано сохранить форму", formAfterStay)
        assertEquals("«Остаться» обязано сохранить ввод", typedName, nameAfterStay)
    }

    @Тогда("^диалог подтверждения сброса не появляется$")
    fun theDiscardDialogDoesNotAppear() {
        assertFalse("черновик без изменений обязан закрываться без вопроса", dialogVisible)
    }

    @Тогда("^форма закрывается без записи$")
    fun theFormClosesWithoutARecord() {
        assertTrue("системная «Назад» обязана закрыть форму", closedWithoutSaving)
        assertEquals("уход с формы не обязан ничего записать", medicationsBefore, world.medications.all().size)
    }

    @Тогда("^лекарство «([^»]+)» не заведено$")
    fun theMedicationIsNotCreated(medication: String) {
        assertTrue(
            "уход с формы не обязан заводить лекарство: ${world.medications.names()}",
            world.medications.all().none { it.name == medication },
        )
    }

    @Тогда("^лекарство «([^»]+)» остаётся в профиле$")
    fun theMedicationStaysInTheProfile(medication: String) {
        val model = world.medications.get(world.medicationNamed(medication))
        assertFalse("правка без изменений не обязана тронуть лекарство", model.isArchived)
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /**
     * Собрать маршрут формы и исполнить действие внутри композиции.
     *
     * Модель создаётся на каждый блок заново: маршрут открывает форму при сборке, и общее
     * состояние между шагами живёт в мире сценария и в наблюдениях этого класса, а не в модели.
     */
    private fun onRoute(medicationId: String?, action: (ComposeUiTest, FormBackOwner) -> Unit) {
        val owner = FormBackOwner()
        closedWithoutSaving = false
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    SklyankaTheme {
                        // Плейсхолдер обработчика раздела: стоит раньше маршрута, поэтому
                        // перехват формы на грязном черновике выигрывает у него (Р-54).
                        BackHandler(enabled = true) { closedWithoutSaving = true }
                        MedicationFormRoute(
                            viewModel = formModel(),
                            medicationId = medicationId,
                            onDone = { closedWithoutSaving = true },
                            onAbandon = { closedWithoutSaving = true },
                        )
                    }
                }
            }
            action(this, owner)
        }
    }

    /** Настоящая модель формы на хранилищах мира сценария (образец — `AcceptanceWorld.save`). */
    private fun formModel(): MedicationFormViewModel = MedicationFormViewModel(
        activeProfile = world.profileSession.activeStore,
        medications = world.repositories.medicationStore,
        saveMedication = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = world.repositories.medicationStore,
            planner = world.planner(),
            transactor = ScenarioTransactor(world.intakes, world.medications),
            log = world.log,
            newId = { "medication-sk154-${++savedForms}" },
        ),
        time = world.clock.time,
        // Продукт бесплатен (Р-86): доступ «Про» не гейтит состав формы в этих сценариях.
        proStatus = ObserveProStatusUseCase(
            FakeProCache(ProSubscription.Active(productId = null, until = LocalDate.MAX)),
            world.clock.time,
        ),
    )

    /** Прогнать очередь главного looper'а и кадры композиции: модель открывается корутиной. */
    private fun ComposeUiTest.settle() {
        shadowOf(Looper.getMainLooper()).idle()
        waitForIdle()
    }

    private fun ComposeUiTest.settleUntilForm() {
        repeat(SETTLE_ROUNDS) { settle() }
        assertTrue(
            "форма обязана открыться маршрутом",
            formNodes().isNotEmpty(),
        )
    }

    private fun FormBackOwner.pressBack() {
        onBackPressedDispatcher.onBackPressed()
    }

    /** Ввод в поле названия: значение лежит у строки ввода внутри поля (приём `MedsFormScreenSteps`). */
    private fun ComposeUiTest.typeIntoName(name: String) {
        onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
            useUnmergedTree = true,
        ).performTextInput(name)
        settle()
    }

    private fun ComposeUiTest.dialogNodes() = onAllNodesWithTag(TAG_CONFIRM_DIALOG).fetchSemanticsNodes()

    private fun ComposeUiTest.formNodes() = onAllNodesWithTag(TAG_MEDICATION_FORM).fetchSemanticsNodes()

    private fun ComposeUiTest.dialogTitle(): String = onAllNodesWithTag(TAG_CONFIRM_TITLE).fetchSemanticsNodes()
        .firstOrNull()
        ?.config
        ?.getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(separator = "") { it.text }

    /** Введённое в поле названия: значение лежит у строки ввода внутри поля, а не у контейнера. */
    private fun ComposeUiTest.nameInField(): String = onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private companion object {
        /** Раундов осаждения достаточно, чтобы корутина открытия формы завершилась. */
        const val SETTLE_ROUNDS = 3
    }
}

/**
 * Владелец диспетчера системной «Назад» без Activity (Р-54, образец — `RecordingBackOwner`
 * `MedsFormStand`). Владельцем жизненного цикла он же и является: обработчик маршрута
 * привязывается к нему и обязан видеть состояние `RESUMED`, иначе он выключен.
 */
private class FormBackOwner :
    OnBackPressedDispatcherOwner,
    LifecycleOwner {

    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
