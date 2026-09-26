package ru.heuri.sklyanka.feature.meds

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.TrailingLine
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.feature.meds.list.AsNeededCardUi
import ru.heuri.sklyanka.feature.meds.list.MedicationCardUi
import ru.heuri.sklyanka.feature.meds.list.MedicationListScreen
import ru.heuri.sklyanka.feature.meds.list.MedsUiState
import ru.heuri.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.heuri.sklyanka.feature.meds.list.TAG_LAST_INTAKE
import ru.heuri.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.heuri.sklyanka.feature.meds.list.TAG_TRAILING_LINE
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экран «Лекарства»: счётчики, состав карточек, кнопка добавления.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class MedicationListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHeaderCarriesTheScreenTitle() {
        show(MedsUiState(loading = false, items = listOf(scheduledCard())))

        composeRule.onNodeWithText(context.getString(R.string.meds_title)).assertIsDisplayed()
    }

    @Test
    fun theSegmentedControlShowsBothCounters() {
        show(
            MedsUiState(
                activeCount = 5,
                archiveCount = 12,
                items = listOf(scheduledCard()),
                loading = false,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.meds_tab_active, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.meds_tab_archive, 12)).assertIsDisplayed()
    }

    @Test
    fun anAsNeededMedicationIsListedWhileTheActiveCounterIgnoresIt() {
        // ЭКР-3: чип говорит «Активные · 5», а карточек шесть — шестая «по потребности».
        show(
            MedsUiState(
                activeCount = 5,
                items = List(5) { index -> scheduledCard(id = "scheduled-$index") } + asNeededCard(),
                loading = false,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.meds_tab_active, 5)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_MEDICATION_CARD).assertCountEquals(6)
    }

    @Test
    fun anAsNeededCardCarriesTheChipTheLastIntakeAndTheTakeButton() {
        show(MedsUiState(items = listOf(asNeededCard()), loading = false))

        composeRule.onNodeWithText(context.getString(R.string.meds_as_needed_chip)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_LAST_INTAKE).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_take)).assertIsDisplayed()
    }

    @Test
    fun anExhaustedDailyLimitDisablesTheTakeButton() {
        show(MedsUiState(items = listOf(asNeededCard(takeEnabled = false)), loading = false))

        composeRule.onNodeWithText(context.getString(UiR.string.action_take)).assertIsNotEnabled()
    }

    @Test
    fun aTriggeredStockThresholdReplacesThePeriodWithTheStockLeft() {
        show(
            MedsUiState(
                items = listOf(
                    scheduledCard(
                        trailingLine = TrailingLine.Stock(BigDecimal("9"), DoseUnit.TABLET),
                    ),
                ),
                loading = false,
            ),
        )

        composeRule.onNodeWithTag(TAG_TRAILING_LINE).assertIsDisplayed()
    }

    @Test
    fun theAddButtonIsLabelledForScreenReaders() {
        show(MedsUiState(items = listOf(scheduledCard()), loading = false))

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.cd_add_medication))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ADD_MEDICATION).assertIsDisplayed()
    }

    @Test
    fun anEmptyActiveListInvitesToAddAMedication() {
        show(MedsUiState(loading = false))

        composeRule.onNodeWithText(context.getString(R.string.meds_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.meds_empty_action)).assertIsDisplayed()
    }

    private fun show(state: MedsUiState) {
        composeRule.setContent {
            SklyankaTheme {
                MedicationListScreen(state = state, onIntent = {})
            }
        }
    }

    private fun scheduledCard(
        id: String = "scheduled",
        trailingLine: TrailingLine = TrailingLine.Period(LocalDate.of(2026, 3, 12), null),
    ) = MedicationCardUi(
        medicationId = id,
        title = "med-$id",
        form = MedicationForm.TABLET,
        strength = "50",
        strengthUnit = "mg",
        timesPerDay = 1,
        times = listOf(LocalTime.of(8, 0)),
        trailingLine = trailingLine,
        asNeeded = null,
    )

    private fun asNeededCard(takeEnabled: Boolean = true) = MedicationCardUi(
        medicationId = "as-needed",
        title = "med-as-needed",
        form = MedicationForm.CAPSULE,
        strength = "200",
        strengthUnit = "mg",
        timesPerDay = 0,
        times = emptyList(),
        trailingLine = TrailingLine.Period(LocalDate.of(2026, 3, 12), null),
        asNeeded = AsNeededCardUi(
            maxPerDay = 3,
            lastIntakeDate = LocalDate.of(2026, 9, 19),
            takeEnabled = takeEnabled,
        ),
    )
}
