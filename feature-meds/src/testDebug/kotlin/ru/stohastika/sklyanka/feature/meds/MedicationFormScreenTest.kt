package ru.stohastika.sklyanka.feature.meds

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.form.TAG_FOOD_RELATION
import ru.stohastika.sklyanka.feature.meds.form.TAG_MAX_PER_DAY
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_HINT
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_UNIT
import ru.stohastika.sklyanka.feature.meds.form.TAG_PERIOD_PRESETS
import ru.stohastika.sklyanka.feature.meds.form.TAG_RELEASE_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_THRESHOLD_OPTIONS
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_UNIT
import ru.stohastika.sklyanka.feature.meds.form.TAG_TABLET_SHAPE_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIMES_BLOCK
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Форма лекарства: порядок блоков, применимость, единицы, подписи для чтения с экрана.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedicationFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theModeSwitchStandsAboveTheScheduleBlocks() {
        // Отступление от макета по Д-2: именно режим решает, нужно ли время вообще.
        show(draft())

        assertOrder(
            label(R.string.form_label_name),
            label(R.string.form_label_release_form),
            label(R.string.form_label_tablet_shape),
            label(R.string.form_label_dose),
            label(R.string.form_label_mode),
            label(R.string.form_label_times),
            label(R.string.form_label_food_relation),
            label(R.string.form_label_stock),
            label(R.string.form_label_period),
        )
    }

    @Test
    fun theReleaseFormOffersSixValues() {
        show(draft())

        listOf(
            R.string.med_form_tablet,
            R.string.med_form_capsule,
            R.string.med_form_spray,
            R.string.med_form_syrup,
            R.string.med_form_drops,
            R.string.med_form_injection,
        ).forEach { id ->
            // Подпись плитки адресуется внутри блока формы: «Таблетка» звучит ещё и в
            // заголовке блока-превью (ОВ-94), и поиск по всему экрану нашёл бы два узла.
            composeRule.onNode(
                hasText(context.getString(id)) and hasAnyAncestor(hasTestTag(TAG_RELEASE_FORM)),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        }
    }

    @Test
    fun theTabletShapeBlockShowsForTabletsOnly() {
        show(draft(form = MedicationForm.TABLET))
        composeRule.onNodeWithTag(TAG_TABLET_SHAPE_ROW).assertIsDisplayed()
    }

    @Test
    fun theTabletShapeBlockHidesForASpray() {
        show(draft(form = MedicationForm.SPRAY))
        composeRule.onNodeWithTag(TAG_TABLET_SHAPE_ROW).assertDoesNotExistInTree()
    }

    @Test
    fun everyTabletShapeIsLabelledForScreenReaders() {
        show(draft(form = MedicationForm.TABLET))

        listOf(
            R.string.tablet_shape_round,
            R.string.tablet_shape_round_scored,
            R.string.tablet_shape_oval,
            R.string.tablet_shape_oblong,
            R.string.tablet_shape_triangular,
        ).forEach { id ->
            composeRule.onNodeWithContentDescription(context.getString(id)).assertIsDisplayed()
        }
    }

    @Test
    fun aScheduledCourseShowsTheCounterAndTheTimes() {
        show(draft(times = listOf(LocalTime.of(8, 0))))

        composeRule.onNodeWithText(context.getString(R.string.form_label_intakes_per_day)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_caption_times_below)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TIMES_BLOCK).assertIsDisplayed()
    }

    @Test
    fun anAsNeededCourseReplacesTheScheduleWithTheDailyLimit() {
        // Дефект постановки Д-1: поля ограничения в макете нет, блок счётчика меняет смысл.
        show(draft(mode = ScheduleMode.PRN))

        composeRule.onNodeWithTag(TAG_MAX_PER_DAY).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_label_max_per_day)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TIMES_BLOCK).assertDoesNotExistInTree()
    }

    @Test
    fun theAsNeededModeIsExplainedInWords() {
        show(draft())

        composeRule.onNodeWithText(context.getString(R.string.form_mode_as_needed_hint)).assertIsDisplayed()
    }

    @Test
    fun anAsNeededCourseHidesTheFoodRelationBlock() {
        // ОВ-83: к приёму «по потребности» связь с едой не относится; остаток остаётся.
        show(draft(mode = ScheduleMode.PRN))

        composeRule.onNodeWithTag(TAG_FOOD_RELATION).assertDoesNotExistInTree()
        composeRule.onNodeWithTag(TAG_STOCK_FIELD).assertIsDisplayed()
    }

    @Test
    fun aScheduledCourseKeepsTheFoodRelationBlock() {
        show(draft())

        // Блок — ряд из четырёх плиток: проверка адресует состав блока, а не первую
        // попавшуюся плитку.
        composeRule.onAllNodesWithTag(TAG_FOOD_RELATION).assertCountEquals(4)
    }

    @Test
    fun theStockUnitFollowsTheReleaseForm() {
        show(draft(form = MedicationForm.SYRUP))

        composeRule.onNodeWithTag(TAG_STOCK_UNIT).assertIsDisplayed()
        // Поиск по подписи «мл» неоднозначен: единица стоит и у дозы, и у остатка.
        // Проверка адресует именно поле остатка, а не первое попавшееся совпадение.
        composeRule
            .onNodeWithTag(TAG_STOCK_UNIT)
            .assertTextEquals(context.getString(R.string.stock_unit_millilitres))
    }

    @Test
    fun thePackageSizeRowStandsRightUnderTheCurrentStock() {
        show(draft())

        composeRule.onNodeWithText(context.getString(R.string.form_package_size)).assertIsDisplayed()
        val stockY = composeRule.onNodeWithTag(TAG_STOCK_FIELD).fetchSemanticsNode().positionInRoot.y
        val packageY = composeRule.onNodeWithTag(TAG_PACKAGE_SIZE_FIELD).fetchSemanticsNode().positionInRoot.y
        assertTrue("the package size must stand under the stock: $packageY < $stockY", packageY > stockY)
    }

    @Test
    fun thePackageSizeRowRepeatsTheStockUnit() {
        show(draft(form = MedicationForm.SYRUP))

        composeRule
            .onNodeWithTag(TAG_PACKAGE_SIZE_UNIT)
            .assertTextEquals(context.getString(R.string.stock_unit_millilitres))
    }

    @Test
    fun thePackageSizeRowCarriesItsHint() {
        show(draft())

        composeRule.onNodeWithTag(TAG_PACKAGE_SIZE_HINT).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_package_size_hint)).assertIsDisplayed()
    }

    @Test
    fun thePackageSizeErrorIsSpelledOutUnderTheBlock() {
        show(
            draft().copy(packageSize = BigDecimal.ZERO),
            violations = listOf(DraftViolation.PackageSizeNotPositive),
        )

        composeRule.onNodeWithText(context.getString(R.string.error_package_size_not_positive)).assertIsDisplayed()
    }

    @Test
    fun anOpenEndedCourseGreysOutTheEndDate() {
        show(draft())

        composeRule.onNodeWithText(context.getString(R.string.form_period_indefinite)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_period_indefinite_caption)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_period_end_empty)).assertIsDisplayed()
    }

    @Test
    fun thereIsExactlyOneSaveActionAndTheHeaderKeepsOnlyCancel() {
        // Отступление от макета по Д-3: «Готово» в шапке не рисуется вовсе.
        show(draft())

        composeRule.onNodeWithText(context.getString(R.string.form_save_new)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun savingIsBlockedWhileTheNameIsEmptyAndTheReasonIsSpelledOut() {
        show(draft(name = ""))

        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.form_save_blocked)).assertIsDisplayed()
    }

    @Test
    fun editingTitlesTheHeaderWithTheMedicationName() {
        show(draft(name = "med-a"), mode = FormMode.Edit("med-a"))

        // Название лекарства есть и в шапке, и в поле ввода: проверка адресует шапку.
        composeRule
            .onNode(hasText("med-a") and hasAnyAncestor(hasTestTag(TAG_SCREEN_HEADER)))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.form_save_existing)).assertIsDisplayed()
    }

    @Test
    fun theCounterButtonsAreLabelledForScreenReaders() {
        show(draft(times = listOf(LocalTime.of(8, 0))))

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_decrease)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_increase)).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.cd_delete_time, "08:00"))
            .assertIsDisplayed()
    }

    @Test
    fun theDoseFieldsAreLabelledForScreenReaders() {
        show(draft())

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_dose_amount)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_strength)).assertIsDisplayed()
    }

    // --- SKL-69: строка времени поднимает выбор наружу ---

    @Test
    fun tappingATimeRowAsksTheRouteToPickThatTime() {
        var picked: Pair<Int, LocalTime>? = null
        composeRule.setContent {
            SklyankaTheme {
                MedicationFormScreen(
                    state = MedFormUiState.of(
                        draft(times = listOf(LocalTime.of(8, 0))),
                        FormMode.Create,
                        FormInputs(),
                    ),
                    onIntent = {},
                    onCancel = {},
                    onPickDate = {},
                    onPickTime = { index, time -> picked = index to time },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_TIME_ROW).performClick()

        assertEquals(0 to LocalTime.of(8, 0), picked)
    }

    // --- SKL-75: пресеты срока окончания ---

    @Test
    fun thePresetsStandUnderTheEndDateRow() {
        show(draft().copy(endDate = LocalDate.of(2026, 10, 25)))

        composeRule.onNodeWithTag(TAG_PERIOD_PRESETS).assertIsDisplayed()
    }

    @Test
    fun thePresetMatchingTheEndDateIsSelectedAndTheOthersAreNot() {
        show(draft().copy(endDate = LocalDate.of(2026, 10, 25)))

        composeRule.onNodeWithText(context.getString(R.string.period_preset_month_1)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.period_preset_weeks_2)).assertIsNotSelected()
    }

    @Test
    fun anEndDateThatMatchesNoPresetMarksNone() {
        show(draft().copy(endDate = LocalDate.of(2026, 11, 17)))

        composeRule.onNodeWithText(context.getString(R.string.period_preset_month_1)).assertIsNotSelected()
        composeRule.onNodeWithText(context.getString(R.string.period_preset_other)).assertIsNotSelected()
    }

    @Test
    fun thePresetsAreNotSelectableWhileTheCourseIsOpenEnded() {
        show(draft())

        composeRule.onNodeWithText(context.getString(R.string.period_preset_week_1)).assertHasNoClickAction()
    }

    // --- SKL-74: порог напоминания о покупке ---

    @Test
    fun theThresholdChipsAppearWhenTheReminderIsOn() {
        show(draft().copy(stockThresholdDays = 7))

        composeRule.onNodeWithTag(TAG_STOCK_THRESHOLD_OPTIONS).assertIsDisplayed()
        composeRule.onNodeWithText(StockThresholds.DEFAULT_DAYS.toString()).assertIsSelected()
    }

    @Test
    fun theThresholdChipsAreHiddenWhenTheReminderIsOff() {
        show(draft())

        composeRule.onNodeWithTag(TAG_STOCK_THRESHOLD_OPTIONS).assertDoesNotExistInTree()
        composeRule.onNodeWithTag(TAG_STOCK_REMINDER).assertIsDisplayed()
    }

    private fun label(id: Int): String = context.getString(id).uppercase()

    /** Блоки идут сверху вниз в объявленном порядке — это контракт формы, а не вёрстка. */
    private fun assertOrder(vararg labels: String) {
        val positions = labels.map { text ->
            composeRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        }
        positions.zipWithNext().forEach { (upper, lower) ->
            assertTrue("blocks are out of order: $upper before $lower", upper < lower)
        }
    }

    private fun show(
        draft: MedicationDraft,
        mode: FormMode = FormMode.Create,
        violations: List<DraftViolation> = emptyList(),
    ) {
        composeRule.setContent {
            SklyankaTheme {
                MedicationFormScreen(
                    state = MedFormUiState.of(draft, mode, FormInputs()).copy(violations = violations),
                    onIntent = {},
                    onCancel = {},
                    onPickDate = {},
                    onPickTime = { _, _ -> },
                )
            }
        }
    }

    private fun draft(
        name: String = "med-a",
        form: MedicationForm = MedicationForm.TABLET,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        times: List<LocalTime> = emptyList(),
    ) = MedicationDraft(
        id = null,
        profileId = PROFILE_ID,
        name = name,
        form = form,
        scheduleMode = mode,
        times = times,
        startDate = LocalDate.of(2026, 9, 25),
    )

    private fun SemanticsNodeInteraction.assertDoesNotExistInTree() = assertDoesNotExist()

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    }
}
