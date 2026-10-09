package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.task.MedicationFormSteps
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.TAG_FORM_TILE_GLYPH
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_PREVIEW
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_RELEASE_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_TABLET_SHAPE_ROW

/**
 * Экранные шаги приёмки SKL-66 — превью выбора и силуэты формы (группа `@preview`, `@form`).
 *
 * Состояние формы собирает [MedsFormStand] из доменного черновика, нажатия уходят
 * намерениями. Выбор формы и вида повторяет `MedicationFormViewModel.reduce` через стенд,
 * а доменный черновик держится в согласии: часть сценариев выбирает форму шагом общего
 * артефакта (`MedicationFormSteps`), и тогда стенд подтягивается к тому же черновику.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsPreviewScreenSteps(private val world: AcceptanceWorld, private val form: MedicationFormSteps) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Предыстория превью --------------------------------------------------------

    @Допустим("^человек на форме нового лекарства$")
    fun thePersonIsOnTheNewMedicationForm() {
        MedsFormStand.open(world)
    }

    @Допустим("^человек на форме нового лекарства выбрал форму выпуска «([^»]+)», вида ещё нет$")
    fun thePersonChoseTheFormWithoutShape(label: String) {
        MedsFormStand.open(world)
        chooseForm(label)
    }

    @Допустим("^человек на форме нового лекарства выбрал форму «([^»]+)»$")
    fun thePersonChoseTheForm(label: String) {
        MedsFormStand.open(world)
        chooseForm(label)
        // Представительная доза: подпись превью называет числа в порядке ЭКР-7,
        // и введённое количество дозы в неё попадает (ОВ-94, §5.3).
        MedsFormStand.intent(world, MedFormIntent.DoseAmountChanged("2"))
    }

    @Допустим("^человек на форме нового лекарства выбрал форму «([^»]+)» и вид «([^»]+)»$")
    fun thePersonChoseTheFormAndShape(formLabel: String, shapeLabel: String) {
        MedsFormStand.open(world)
        chooseForm(formLabel)
        chooseShape(shapeLabel)
    }

    @Когда("^он выбирает вид «([^»]+)»$")
    fun thePersonChoosesTheShape(label: String) {
        chooseShape(label)
    }

    @Когда("^он выбирает форму «([^»]+)»$")
    fun thePersonChoosesTheForm(label: String) {
        chooseForm(label)
    }

    @Когда("^он возвращает форму «([^»]+)»$")
    fun thePersonReturnsTheForm(label: String) {
        chooseForm(label)
    }

    // --- Превью ---------------------------------------------------------------------

    @Тогда("^сразу под блоком «Название» и выше «Формы выпуска» показан блок-превью$")
    fun thePreviewStandsBetweenTheNameAndTheReleaseForm() {
        val state = previewState()
        MedsFormStand.showDraft(world, state.draft)
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_MEDICATION_PREVIEW).assertIsDisplayed()
            val nameY = test.onNodeWithTag(TAG_NAME_FIELD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val previewY = test.onNodeWithTag(
                TAG_MEDICATION_PREVIEW,
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot.top
            // Плиток формы шесть, тег стоит на каждой: верхняя задаёт начало блока.
            val formY = test.onAllNodesWithTag(TAG_RELEASE_FORM, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .minOf { it.boundsInRoot.top }
            assertTrue("the preview must stand under «Название»: $previewY > $nameY", previewY > nameY)
            assertTrue("the preview must stand above «Форма выпуска»: $previewY < $formY", previewY < formY)
        }
    }

    @Тогда("^заголовок превью — «([^»]+)»$")
    fun thePreviewTitleIs(title: String) {
        val state = previewState()
        MedsFormStand.showDraft(world, state.draft)
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the preview title must read «$title»: $texts", title in texts)
        }
    }

    @Тогда("^подпись превью — «([^»]+)»$")
    fun thePreviewCaptionIs(caption: String) {
        val state = previewState()
        MedsFormStand.showDraft(world, state.draft)
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the preview caption must read «$caption»: $texts", caption in texts)
        }
    }

    @Тогда("^он не видит блока «Вид таблетки»$")
    fun thePersonDoesNotSeeTheTabletShapeBlock() {
        assertFalse("the shape block must be inapplicable (ОВ-88)", previewState().applicability.shapeVisible)
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_TABLET_SHAPE_ROW, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^на плитке показан силуэт овальной таблетки$")
    fun thePreviewTileShowsTheOvalTablet() {
        val state = previewState()
        assertEquals(TabletShape.OVAL, state.draft.shape)
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_MEDICATION_PREVIEW).assertIsDisplayed()
        }
    }

    @Когда("^он смотрит на плитку превью$")
    fun thePersonLooksAtThePreviewTile() {
        previewState()
    }

    @Когда("^он смотрит на подпись превью$")
    fun thePersonLooksAtThePreviewCaption() {
        previewState()
    }

    @И("^в подписи названо введённое количество дозы$")
    fun theCaptionNamesTheEnteredDose() {
        val state = previewState()
        val amount = requireNotNull(state.draft.doseAmount) { "the scenario must have entered a dose" }
        val dose = amount.stripTrailingZeros().toPlainString()
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the caption must name the dose $dose: $texts", texts.any { dose in it })
        }
    }

    // --- Плитки формы ---------------------------------------------------------------

    @Тогда("^в блоке «Форма выпуска» шесть плиток: «(.+)»$")
    fun theReleaseFormOffersSixTiles(values: String) {
        val expected = values.split(VALUES_SEPARATOR).map { it.trim().trim('«', '»') }
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_RELEASE_FORM, useUnmergedTree = true).assertCountEquals(expected.size)
            expected.forEach { label ->
                test.onNode(
                    hasText(label) and hasAnyAncestor(hasTestTag(TAG_RELEASE_FORM)),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            }
        }
    }

    @Тогда("^на каждой плитке показан силуэт формы$")
    fun everyFormTileShowsAShape() {
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_FORM_TILE_GLYPH, useUnmergedTree = true)
                .assertCountEquals(MedicationForm.entries.size)
        }
    }

    @Тогда("^подпись плитки остаётся словом — иконка не заменяет текст$")
    fun everyTileKeepsItsWord() {
        MedsFormStand.ask(world) { test ->
            MedicationForm.entries.forEach { form ->
                test.onNode(
                    hasText(context.getString(labelOf(form))) and hasAnyAncestor(hasTestTag(TAG_RELEASE_FORM)),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            }
        }
    }

    @Тогда("^плитка «([^»]+)» выделена как выбранная$")
    fun thatTileIsSelected(label: String) {
        assertEquals(formOf(label), MedsFormStand.open(world).draft.form)
    }

    @Но("^остальные пять плиток выбранными не показаны$")
    fun theOtherFiveTilesAreNotSelected() {
        val chosen = MedsFormStand.open(world).draft.form
        val selected = MedicationForm.entries.filter { it == chosen }
        assertEquals("exactly one form must be chosen", 1, selected.size)
    }

    // --- Вид таблетки: сброс --------------------------------------------------------

    @Тогда("^вид таблетки не выбран$")
    fun theTabletShapeIsNotChosen() {
        assertEquals(null, MedsFormStand.open(world).draft.shape)
    }

    // --- Превью не является действием -----------------------------------------------

    @Когда("^он нажимает на блок-превью$")
    fun thePersonPressesThePreview() {
        previewState()
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_MEDICATION_PREVIEW, useUnmergedTree = true)
            val actions = node.fetchSemanticsNode().config.toList().map { it.key.name }
            assertFalse("the preview must not be clickable: $actions", actions.any { it == CLICK_ACTION })
            node.performTouchInput { click() }
        }
    }

    @Тогда("^ничего не происходит: форма остаётся на месте, блок не открывает экранов$")
    fun nothingHappens() {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_MEDICATION_PREVIEW).assertIsDisplayed()
            test.onNodeWithTag(TAG_NAME_FIELD).assertIsDisplayed()
        }
    }

    // --- Доступность ----------------------------------------------------------------

    @Когда("^программа чтения с экрана зачитывает блок-превью$")
    fun theScreenReaderReadsThePreview() {
        previewState()
    }

    @Тогда("^она произносит заголовок «([^»]+)» и подпись превью$")
    fun itSpeaksTheTitleAndCaption(title: String) {
        val state = previewState()
        MedsFormStand.showDraft(world, state.draft)
        val caption = expectedCaption(state)
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the reader must speak the title «$title»: $texts", texts.contains(title))
            assertTrue("the reader must speak the caption «$caption»: $texts", texts.contains(caption))
        }
    }

    /**
     * Подпись превью по тому же правилу, что и `medicationPreviewNumbers` (§5.3, ЭКР-7).
     *
     * Числа в порядке ЭКР-7: сначала доза на один приём, затем дозировка препарата, через
     * « · ». Пустая часть в строку не попадает; пусто и то и другое — подпись пуста, а форму
     * и вид называет заголовок, который остаётся всегда.
     */
    private fun expectedCaption(state: MedFormUiState): String = listOfNotNull(
        state.draft.doseAmount?.let { amount ->
            "${amount.stripTrailingZeros().toPlainString()} ${doseUnitLabel(state.applicability.doseUnit)}"
        },
        state.draft.strength?.let { amount ->
            "${amount.stripTrailingZeros().toPlainString()} ${context.getString(R.string.strength_unit_milligram)}"
        },
    ).filter { it.isNotBlank() }.joinToString(PREVIEW_NUMBER_SEPARATOR)

    @Но("^не произносит отдельной подписи силуэта$")
    fun itDoesNotSpeakAShapeLabel() {
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_MEDICATION_PREVIEW, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the silhouette must not carry its own label",
                descriptions(node).isEmpty(),
            )
        }
    }

    // --- Обвязка --------------------------------------------------------------------

    /**
     * Состояние превью с учётом выбора, сделанного шагом общего артефакта.
     *
     * Экранные шаги ведут стенд и доменный черновик вместе; общий шаг `MedicationFormSteps`
     * меняет только черновик. Если форма или вид разошлись, побеждает черновик — и стенд
     * собирается из него.
     */
    private fun previewState(): MedFormUiState {
        val current = MedsFormStand.open(world)
        val domain = form.currentDraft()
        return if (domain.form != current.draft.form || domain.shape != current.draft.shape) {
            MedsFormStand.showDraft(world, domain)
        } else {
            current
        }
    }

    private fun chooseForm(label: String) {
        val chosen = formOf(label)
        MedsFormStand.intent(world, MedFormIntent.ReleaseFormChanged(chosen))
        form.chooseForm(chosen)
    }

    private fun chooseShape(label: String) {
        val chosen = shapeOf(label)
        MedsFormStand.intent(world, MedFormIntent.ShapeChanged(chosen))
        form.chooseShape(chosen)
    }

    private fun doseText(state: MedFormUiState): String {
        val amount = requireNotNull(state.draft.doseAmount) { "the scenario must have entered a dose" }
        return "${amount.stripTrailingZeros().toPlainString()} ${doseUnitLabel(state.applicability.doseUnit)}"
    }

    /** Суффикс единицы дозы: та же четвёрка, что у `MedsText.doseSuffix`. */
    private fun doseUnitLabel(unit: ru.stohastika.sklyanka.domain.medication.DoseUnit): String = context.getString(
        when (unit) {
            ru.stohastika.sklyanka.domain.medication.DoseUnit.TABLET,
            ru.stohastika.sklyanka.domain.medication.DoseUnit.CAPSULE,
            -> R.string.dose_suffix_piece

            ru.stohastika.sklyanka.domain.medication.DoseUnit.MILLILITRE -> R.string.dose_suffix_millilitre
            ru.stohastika.sklyanka.domain.medication.DoseUnit.DOSE -> R.string.dose_suffix_dose
        },
    )

    private fun descriptions(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap { descriptions(it) }

    private fun formOf(label: String): MedicationForm =
        MedicationForm.entries.first { context.getString(labelOf(it)) == label }

    private fun labelOf(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> R.string.med_form_tablet
        MedicationForm.CAPSULE -> R.string.med_form_capsule
        MedicationForm.SPRAY -> R.string.med_form_spray
        MedicationForm.SYRUP -> R.string.med_form_syrup
        MedicationForm.DROPS -> R.string.med_form_drops
        MedicationForm.INJECTION -> R.string.med_form_injection
    }

    private fun shapeOf(label: String): TabletShape =
        TabletShape.entries.first { context.getString(shapeLabelOf(it)) == label }

    private fun shapeLabelOf(shape: TabletShape): Int = when (shape) {
        TabletShape.ROUND -> R.string.tablet_shape_round
        TabletShape.ROUND_SCORED -> R.string.tablet_shape_round_scored
        TabletShape.OVAL -> R.string.tablet_shape_oval
        TabletShape.OBLONG -> R.string.tablet_shape_oblong
        TabletShape.TRIANGULAR -> R.string.tablet_shape_triangular
    }

    private companion object {
        const val VALUES_SEPARATOR = ","
        const val CLICK_ACTION = "OnClick"

        /** Разделитель чисел превью — точка-пауза из макета (`medicationPreviewNumbers`). */
        const val PREVIEW_NUMBER_SEPARATOR = " · "
    }
}
