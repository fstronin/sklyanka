package ru.heuri.sklyanka.feature.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.stock.StockWarning
import ru.heuri.sklyanka.domain.today.DayProgress
import ru.heuri.sklyanka.domain.today.DaySection
import ru.heuri.sklyanka.domain.today.IntakeItem
import ru.heuri.sklyanka.domain.today.IntakePresentation
import ru.heuri.sklyanka.domain.today.PartOfDay
import ru.heuri.sklyanka.domain.today.PrnEntry
import ru.heuri.sklyanka.domain.today.SectionState
import ru.heuri.sklyanka.domain.today.SectionSummary
import ru.heuri.sklyanka.domain.today.StockNotice
import ru.heuri.sklyanka.domain.today.TimeGroup
import ru.heuri.sklyanka.domain.today.TodayLayout
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.heuri.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.heuri.sklyanka.ui.component.TAG_MISSED_STATUS
import ru.heuri.sklyanka.ui.component.TAG_TAKEN_STATUS
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalTime
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экран «Сегодня»: четыре вида карточки приёма, групповая карточка, свёртка плотного
 * дня, блок «по потребности», плашка остатка и пустое состояние.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHeaderCarriesTheDateAndTheTitle() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithText(context.getString(R.string.today_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DAY_PROGRESS).assertIsDisplayed()
    }

    @Test
    fun theLanesAreLabelledByPartOfDayAndByNow() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithText(context.getString(R.string.part_of_day_morning)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.part_of_day_evening)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.today_now)).assertIsDisplayed()
    }

    @Test
    fun aTakenIntakeShowsTheMarkTimeAndAMissedOneShowsItsStatus() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithTag(TAG_TAKEN_STATUS, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MISSED_STATUS, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anIntakeStillAheadCarriesTheRoundCheckButton() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithTag(TAG_MARK_BUTTON, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theRoundCheckButtonIsNamedForTheScreenReader() {
        show(TodayFixtures.plainDay())

        composeRule
            .onNodeWithContentDescription(context.getString(UiR.string.cd_mark_intake, TodayFixtures.LOSARTAN))
            .assertIsDisplayed()
    }

    @Test
    fun anIntakeWhoseTimeHasComeOffersBothButtons() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithText(context.getString(UiR.string.action_taken)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_skip)).assertIsDisplayed()
    }

    @Test
    fun theStockPlateShowsOneWarningWithABuyButton() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithTag(TAG_STOCK_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.today_stock_action)).assertIsDisplayed()
    }

    @Test
    fun withoutATriggeredThresholdThereIsNoStockPlate() {
        show(TodayFixtures.plainDay().copy(stockWarning = null))

        composeRule.onAllNodesWithTag(TAG_STOCK_BANNER).assertCountEquals(0)
    }

    @Test
    fun theOnDemandBlockCarriesItsDividerAndCard() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithTag(TAG_PRN_DIVIDER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PRN_CARD).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_take)).assertIsDisplayed()
    }

    @Test
    fun withoutOnDemandMedicationsTheBlockIsGoneCompletely() {
        show(TodayFixtures.plainDay().copy(prn = emptyList()))

        composeRule.onAllNodesWithTag(TAG_PRN_DIVIDER).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_PRN_CARD).assertCountEquals(0)
    }

    @Test
    fun anExhaustedDailyLimitClosesTheTakeButtonAndNamesTheReason() {
        show(TodayFixtures.plainDay().copy(prn = listOf(TodayFixtures.exhaustedPrn())))

        composeRule.onNodeWithText(context.getString(UiR.string.action_take)).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_PRN_LIMIT_REASON).assertIsDisplayed()
    }

    @Test
    fun simultaneousIntakesAreShownAsOneCardWithTwoActions() {
        show(TodayFixtures.groupDay())

        composeRule.onNodeWithTag(TAG_GROUP_CARD).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.group_mark_all_three)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.group_mark_one_by_one)).assertIsDisplayed()
    }

    @Test
    fun oneByOneAsksToExpandTheGroupWithoutMarkingAnything() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.groupDay(), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_GROUP_ONE_BY_ONE).performClick()

        assertEquals(listOf(TodayIntent.ExpandGroup(LocalTime.of(14, 0))), intents)
    }

    @Test
    fun anExpandedGroupFallsApartIntoSeparateRows() {
        show(TodayFixtures.groupDay().copy(expandedGroups = setOf(LocalTime.of(14, 0))))

        composeRule.onAllNodesWithTag(TAG_GROUP_CARD).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_INTAKE_CARD).assertCountEquals(TodayFixtures.GROUP_SIZE)
    }

    @Test
    fun theDenseDayCollapsesThePastAndTheFuture() {
        show(TodayFixtures.denseDay())

        composeRule.onAllNodesWithTag(TAG_SECTION_COLLAPSED).assertCountEquals(2)
        composeRule.onNodeWithTag(TAG_SECTION_EXPANDED).assertIsDisplayed()
    }

    @Test
    fun aCollapsedSectionAsksToExpandOnATap() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.denseDay(), onIntent = intents::add)

        composeRule.onAllNodesWithTag(TAG_SECTION_COLLAPSED)[0].performClick()

        assertEquals(listOf(TodayIntent.ToggleSection(PartOfDay.MORNING)), intents)
    }

    @Test
    fun theDenseProgressCaptionNamesTheMedicationCount() {
        show(TodayFixtures.denseDay())

        val intakes = context.resources.getQuantityString(UiR.plurals.intakes_taken_of_total, 3, 1, 3)
        val medications = context.resources.getQuantityString(R.plurals.today_medications, 3, 3)
        composeRule
            .onNodeWithText(context.getString(R.string.today_progress_dense, intakes, medications))
            .assertIsDisplayed()
    }

    @Test
    fun aProfileWithoutMedicationsSeesTheCallToAddOne() {
        show(TodayUiState.Empty(TodayFixtures.DATE))

        composeRule.onNodeWithText(context.getString(R.string.today_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.today_empty_text)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.today_empty_action)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_DAY_PROGRESS).assertCountEquals(0)
    }

    @Test
    fun theEmptyStateButtonAsksForTheNewMedicationForm() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayUiState.Empty(TodayFixtures.DATE), onIntent = intents::add)

        composeRule.onNodeWithText(context.getString(R.string.today_empty_action)).performClick()

        assertEquals(listOf(TodayIntent.AddMedication), intents)
    }

    @Test
    fun medicationsWithoutIntakesTodayGetAQuietLineInsteadOfTheEmptyState() {
        show(
            TodayFixtures.plainDay().copy(
                layout = TodayLayout.Plain(emptyList()),
                prn = emptyList(),
                stockWarning = null,
                progress = DayProgress(taken = 0, planned = 0, medicationCount = 1),
                nothingToday = true,
            ),
        )

        composeRule.onNodeWithTag(TAG_NOTHING_TODAY).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_TODAY_EMPTY).assertCountEquals(0)
    }

    @Test
    fun theBottomBarCarriesThreeTabs() {
        show(TodayFixtures.plainDay())

        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_meds)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_settings)).assertIsDisplayed()
    }

    private fun show(state: TodayUiState, onIntent: (TodayIntent) -> Unit = {}) {
        composeRule.setContent {
            SklyankaTheme { TodayScreen(state = state, onIntent = onIntent) }
        }
    }
}

/** Расклады экрана для проверок: те же числа, что в `acceptance.feature` и макетах. */
internal object TodayFixtures {

    val DATE: java.time.LocalDate = java.time.LocalDate.of(2026, 9, 25)
    const val LOSARTAN = "Лозартан"
    const val GROUP_SIZE = 3

    /** Обычный день: принятый приём, просроченный, наступивший и ещё впереди. */
    fun plainDay(): TodayUiState.Content = content(
        layout = TodayLayout.Plain(
            listOf(
                group(
                    time = LocalTime.of(8, 0),
                    isNow = false,
                    items = listOf(
                        item("i-taken", "Эутирокс", LocalTime.of(8, 0), IntakePresentation.TAKEN),
                        item("i-missed", "Витамин D3", LocalTime.of(8, 0), IntakePresentation.MISSED),
                    ),
                ),
                group(
                    time = LocalTime.of(14, 0),
                    isNow = true,
                    items = listOf(item("i-due", "Магний B6", LocalTime.of(14, 0), IntakePresentation.DUE_NOW)),
                ),
                group(
                    time = LocalTime.of(21, 0),
                    isNow = false,
                    items = listOf(item("i-ahead", LOSARTAN, LocalTime.of(21, 0), IntakePresentation.UPCOMING)),
                ),
            ),
        ),
        prn = listOf(prn(canTake = true, takenToday = 0)),
        stockWarning = stockNotice(),
        progress = DayProgress(taken = 1, planned = 4, medicationCount = 4),
    )

    /** Три приёма на одно наступившее время — групповая карточка. */
    fun groupDay(): TodayUiState.Content = content(
        layout = TodayLayout.Plain(
            listOf(
                group(
                    time = LocalTime.of(14, 0),
                    isNow = true,
                    items = listOf(
                        item("i-a", "Магний B6", LocalTime.of(14, 0), IntakePresentation.DUE_NOW),
                        item("i-b", "Панангин", LocalTime.of(14, 0), IntakePresentation.DUE_NOW),
                        item("i-c", "Омез", LocalTime.of(14, 0), IntakePresentation.DUE_NOW),
                    ),
                ),
            ),
        ),
    )

    /** Плотный день: утро свёрнуто и отмечено, день развёрнут, вечер впереди. */
    fun denseDay(): TodayUiState.Content = content(
        layout = TodayLayout.Dense(
            listOf(
                section(
                    partOfDay = PartOfDay.MORNING,
                    state = SectionState.Collapsed(SectionSummary.ALL_MARKED),
                    items = listOf(item("i-m", "Эутирокс", LocalTime.of(8, 0), IntakePresentation.TAKEN)),
                ),
                section(
                    partOfDay = PartOfDay.DAY,
                    state = SectionState.Expanded,
                    items = listOf(item("i-d", "Креон", LocalTime.of(15, 30), IntakePresentation.DUE_NOW)),
                ),
                section(
                    partOfDay = PartOfDay.EVENING,
                    state = SectionState.Collapsed(SectionSummary.AHEAD),
                    items = listOf(item("i-e", LOSARTAN, LocalTime.of(21, 0), IntakePresentation.UPCOMING)),
                ),
            ),
        ),
        progress = DayProgress(taken = 1, planned = 3, medicationCount = 3),
        prn = emptyList(),
    )

    fun exhaustedPrn(): PrnEntry = prn(canTake = false, takenToday = 3)

    private fun content(
        layout: TodayLayout,
        prn: List<PrnEntry> = emptyList(),
        stockWarning: StockNotice? = null,
        progress: DayProgress = DayProgress(taken = 0, planned = 1, medicationCount = 1),
    ) = TodayUiState.Content(
        date = DATE,
        isForeignProfile = false,
        profileAccent = ProfileAccent.Owner,
        progress = progress,
        stockWarning = stockWarning,
        layout = layout,
        prn = prn,
        toggledSections = emptySet(),
        expandedGroups = emptySet(),
        prnCollapsed = false,
        nothingToday = false,
    )

    private fun group(time: LocalTime, isNow: Boolean, items: List<IntakeItem>) = TimeGroup(
        time = time,
        partOfDay = PartOfDay.of(time),
        isNow = isNow,
        items = items,
    )

    private fun section(partOfDay: PartOfDay, state: SectionState, items: List<IntakeItem>) = DaySection(
        partOfDay = partOfDay,
        state = state,
        groups = listOf(group(items.first().intake.scheduledAt.toLocalTime(), isNow = false, items = items)),
    )

    private fun item(id: String, name: String, time: LocalTime, presentation: IntakePresentation): IntakeItem {
        val medication = medication(id = "m-$id", name = name, times = listOf(time))
        return IntakeItem(
            intake = ru.heuri.sklyanka.domain.model.Intake(
                id = id,
                medicationId = medication.id,
                scheduledAt = DATE.atTime(time),
                status = presentation.status(),
                actedAt = null,
                source = null,
                actorProfileId = null,
            ),
            medication = medication,
            presentation = presentation,
            markedAt = if (presentation == IntakePresentation.TAKEN) time.plusMinutes(5) else null,
        )
    }

    private fun IntakePresentation.status(): IntakeStatus = when (this) {
        IntakePresentation.TAKEN -> IntakeStatus.TAKEN
        IntakePresentation.MISSED -> IntakeStatus.MISSED
        else -> IntakeStatus.PENDING
    }

    private fun prn(canTake: Boolean, takenToday: Int) = PrnEntry(
        medication = medication(
            id = "m-prn",
            name = "Нурофен",
            times = emptyList(),
            mode = ScheduleMode.PRN,
            maxPerDay = 3,
        ),
        maxPerDay = 3,
        takenToday = takenToday,
        canTake = canTake,
    )

    private fun stockNotice() = StockNotice(
        warning = StockWarning(
            medicationId = "m-magnesium",
            medicationName = "Магний B6",
            unitsLeft = BigDecimal("9"),
            daysLeft = 4,
            thresholdDays = 5,
        ),
        doseUnit = DoseUnit.TABLET,
    )

    @Suppress("LongParameterList")
    private fun medication(
        id: String,
        name: String,
        times: List<LocalTime>,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ) = ru.heuri.sklyanka.domain.model.Medication(
        id = id,
        profileId = "profile-self",
        name = name,
        form = ru.heuri.sklyanka.domain.model.MedicationForm.TABLET,
        shape = ru.heuri.sklyanka.domain.model.TabletShape.ROUND,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = null,
        strengthUnit = null,
        foodRelation = ru.heuri.sklyanka.domain.model.FoodRelation.ANY,
        scheduleMode = mode,
        maxPerDay = maxPerDay,
        times = times,
        startDate = DATE.minusMonths(1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )
}
