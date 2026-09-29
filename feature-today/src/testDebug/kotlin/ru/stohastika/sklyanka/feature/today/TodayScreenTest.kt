package ru.stohastika.sklyanka.feature.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.stock.StockWarning
import ru.stohastika.sklyanka.domain.today.DayProgress
import ru.stohastika.sklyanka.domain.today.DaySection
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.PrnEntry
import ru.stohastika.sklyanka.domain.today.SectionState
import ru.stohastika.sklyanka.domain.today.SectionSummary
import ru.stohastika.sklyanka.domain.today.StockNotice
import ru.stohastika.sklyanka.domain.today.TimeGroup
import ru.stohastika.sklyanka.domain.today.TodayLayout
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_MISSED_STATUS
import ru.stohastika.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_PROGRESS_LINK
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.component.TAG_SECONDARY_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.component.TAG_TAKEN_STATUS
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.math.BigDecimal
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экран «Сегодня»: четыре вида карточки приёма, свёртка плотного дня, блок
 * «по потребности», плашка остатка и пустое состояние.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: снимок стенда режет его по нижней кромке, и проверка
// состава видела бы не экран, а его верх. Высота окна поднята для прогона (SKL-24).
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class TodayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHeaderCarriesTheDateAndTheTitle() {
        show(TodayFixtures.plainDay())

        // Слово «Сегодня» стоит и в шапке, и во вкладке нижней панели: проверка
        // адресует шапку, а не первое совпадение.
        composeRule
            .onNode(hasText(context.getString(R.string.today_title)) and hasAnyAncestor(hasTestTag(TAG_SCREEN_HEADER)))
            .assertIsDisplayed()
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

    // --- Групповая отметка и ссылка на историю (SKL-31) -------------------------

    @Test
    fun simultaneousIntakesOfTheCurrentTimeAreRowsUnderAGroupCaption() {
        show(TodayFixtures.groupDay())

        composeRule.onAllNodesWithTag(TAG_INTAKE_CARD).assertCountEquals(TodayFixtures.GROUP_SIZE)
        composeRule.onAllNodesWithText(TIME_14).assertCountEquals(1)
        composeRule.onNodeWithText(groupCaption(3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.group_mark_all_three)).assertIsDisplayed()
    }

    @Test
    fun theGroupCaptionAndButtonCountOnlyTheAwaitingIntakes() {
        show(TodayFixtures.groupDay(awaiting = 2))

        composeRule.onNodeWithText(groupCaption(2)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.group_mark_both)).assertIsDisplayed()
    }

    @Test
    fun withASingleAwaitingIntakeTheGroupCaptionAndButtonAreGone() {
        show(TodayFixtures.groupDay(awaiting = 1))

        composeRule.onAllNodesWithTag(TAG_GROUP_CAPTION, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_GROUP_MARK_ALL).assertCountEquals(0)
    }

    @Test
    fun aheadSimultaneousIntakesCarryNoGroupCaptionOrBatch() {
        show(TodayFixtures.groupDay(isNow = false))

        composeRule.onAllNodesWithTag(TAG_GROUP_CAPTION, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_GROUP_MARK_ALL).assertCountEquals(0)
    }

    @Test
    fun theFirstPressOnlyArmsTheButtonAndMarksNothing() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.groupDay(), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_GROUP_MARK_ALL).performClick()

        assertEquals(listOf(TodayIntent.ArmGroupMark(TodayFixtures.GROUP_TIME)), intents)
    }

    @Test
    fun theSecondPressAsksToMarkEveryAwaitingIntake() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_GROUP_MARK_ALL).performClick()

        assertEquals(listOf(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c"))), intents)
    }

    @Test
    fun theConfirmationIsAQuestionOnThePrimaryButtonAnnouncedPolitely() {
        show(TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME))

        composeRule.onNodeWithText(context.getString(R.string.group_confirm_all_three)).assertIsDisplayed()
        val node = composeRule.onNode(batchButton(TAG_PRIMARY_BUTTON), useUnmergedTree = true).fetchSemanticsNode()
        // Вопрос объявляется чтением с экрана: live region стоит только в подтверждении.
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theOrdinaryBatchButtonIsSecondaryAndNotALiveRegion() {
        show(TodayFixtures.groupDay())

        val node = composeRule.onNode(batchButton(TAG_SECONDARY_BUTTON), useUnmergedTree = true).fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theBatchKeepsThePrimaryHeightWhenOrdinary() {
        show(TodayFixtures.groupDay())

        assertBatchHeight()
    }

    @Test
    fun theBatchKeepsThePrimaryHeightWhenConfirming() {
        show(TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME))

        assertBatchHeight()
    }

    @Test
    fun tappingOutsideTheButtonDismissesTheConfirmation() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME), onIntent = intents::add)

        // Пустое место шапки: не кнопка и не карточка — значит, «вне кнопки» (ОВ-32).
        composeRule.onNodeWithTag(TAG_TODAY_SCREEN).performTouchInput { click(Offset(2f, 2f)) }

        assertEquals(listOf(TodayIntent.DismissGroupConfirm), intents)
    }

    @Test
    fun tappingTheButtonItselfDoesNotDismissTheConfirmation() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_GROUP_MARK_ALL).performClick()

        assertTrue(
            "the button tap must not dismiss the confirmation: $intents",
            intents.none { it == TodayIntent.DismissGroupConfirm },
        )
    }

    @Test
    fun theDayProgressRowAsksForHistory() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.plainDay(), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).performClick()

        assertEquals(listOf(TodayIntent.OpenHistory), intents)
    }

    @Test
    fun withoutADayPlanThereIsNoHistoryLink() {
        show(
            TodayFixtures.plainDay().copy(
                progress = DayProgress(taken = 0, planned = 0, medicationCount = 1),
                nothingToday = true,
            ),
        )

        composeRule.onAllNodesWithTag(TAG_DAY_PROGRESS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * Батч-кнопка группы в её нынешнем виде — по тегу вида под обёрткой батча.
     *
     * На экране есть и другие основные и второстепенные кнопки (пара карточки), поэтому
     * вид ищется среди потомков тега батча, а не сам по себе.
     */
    private fun batchButton(tag: String): SemanticsMatcher =
        hasTestTag(tag) and hasAnyAncestor(hasTestTag(TAG_GROUP_MARK_ALL))

    private fun assertBatchHeight() {
        val node = composeRule.onNodeWithTag(TAG_GROUP_MARK_ALL).fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.size.height.toDp() }
        assertTrue(
            "the batch button is $height, below ${SklyankaSizes.PrimaryButtonHeight}",
            height >= SklyankaSizes.PrimaryButtonHeight - SklyankaSizes.BorderThin,
        )
    }

    private fun groupCaption(count: Int): String =
        context.resources.getQuantityString(R.plurals.group_caption_by_one, count, count)

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

    // --- Предупреждение о ненадёжности (SKL-29) --------------------------------

    @Test
    fun revokedNotificationsAreNamedOnTheBanner() {
        assertTheWarningIsShown(ReminderWarning.NOTIFICATIONS_OFF, R.string.today_reliability_notifications_off)
    }

    @Test
    fun deniedExactAlarmsAreNamedOnTheBanner() {
        assertTheWarningIsShown(ReminderWarning.EXACT_ALARMS_OFF, R.string.today_reliability_exact_alarms_off)
    }

    @Test
    fun bothRevokedPermissionsAreNamedOnOneBanner() {
        assertTheWarningIsShown(ReminderWarning.BOTH_OFF, R.string.today_reliability_both_off)
    }

    private fun assertTheWarningIsShown(warning: ReminderWarning, text: Int) {
        show(TodayFixtures.plainDay().copy(reliability = warning))

        composeRule.onNodeWithTag(TAG_RELIABILITY_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(text)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.today_reliability_action)).assertIsDisplayed()
    }

    @Test
    fun theWarningStandsFirstInTheHeader() {
        show(TodayFixtures.plainDay().copy(reliability = ReminderWarning.NOTIFICATIONS_OFF))

        val banner = composeRule.onNodeWithTag(TAG_RELIABILITY_BANNER).fetchSemanticsNode()
        val progress = composeRule.onNodeWithTag(TAG_DAY_PROGRESS).fetchSemanticsNode()
        val stock = composeRule.onNodeWithTag(TAG_STOCK_BANNER).fetchSemanticsNode()
        // Первым в шапке: выше полосы прогресса и плашки остатка (ОВ-82).
        assertTrue("the banner must stand above the day progress", banner.boundsInRoot.top < progress.boundsInRoot.top)
        assertTrue("the banner must stand above the stock plate", banner.boundsInRoot.top < stock.boundsInRoot.top)
    }

    @Test
    fun withoutAReasonThereIsNoReliabilityBanner() {
        show(TodayFixtures.plainDay().copy(reliability = null))

        composeRule.onAllNodesWithTag(TAG_RELIABILITY_BANNER).assertCountEquals(0)
    }

    @Test
    fun theWarningActionAsksForPermissions() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.plainDay().copy(reliability = ReminderWarning.NOTIFICATIONS_OFF), onIntent = intents::add)

        composeRule.onNodeWithText(context.getString(R.string.today_reliability_action)).performClick()

        assertEquals(listOf(TodayIntent.OpenPermissions), intents)
    }

    @Test
    fun manualMarkingStaysAvailableNextToTheWarning() {
        val intents = mutableListOf<TodayIntent>()
        show(TodayFixtures.plainDay().copy(reliability = ReminderWarning.BOTH_OFF), onIntent = intents::add)

        // Предупреждение не запирает экран: карточка приёма и её кнопки остаются (НАП-18).
        composeRule.onNodeWithTag(TAG_RELIABILITY_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_taken)).performClick()

        assertEquals(listOf(TodayIntent.MarkTaken("i-due")), intents)
    }

    // --- Снэкбар отмены (SKL-30) -----------------------------------------------

    @Test
    fun theUndoSnackbarNamesTheMedicationAndOffersOneAction() {
        show(TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice()))

        composeRule.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.today_undo_taken_single, "Магний B6"), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.today_undo_action), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theSnackbarStandsAboveTheBottomBarWithoutCoveringIt() {
        show(TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice()))

        val snackbar = composeRule.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
        val bar = composeRule.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode()
        // Панель остаётся видимой и нажимаемой: снэкбар выровнен по низу области
        // контента, а не поверх панели (03-screens.md, SKL-30).
        assertTrue(
            "the snackbar must stand above the bar: ${snackbar.boundsInRoot.bottom} > ${bar.boundsInRoot.top}",
            snackbar.boundsInRoot.bottom <= bar.boundsInRoot.top,
        )
    }

    @Test
    fun withoutAnUndoNoticeThereIsNoSnackbar() {
        show(TodayFixtures.plainDay().copy(undo = null))

        composeRule.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theEmptyStateCarriesNoSnackbar() {
        show(TodayUiState.Empty(TodayFixtures.DATE))

        composeRule.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theUndoActionNamesExactlyTheIntakesOfTheAction() {
        val intents = mutableListOf<TodayIntent>()
        show(
            TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice(intakeIds = listOf("i-a", "i-b"))),
            onIntent = intents::add,
        )

        composeRule.onNodeWithText(context.getString(R.string.today_undo_action), useUnmergedTree = true).performClick()

        assertEquals(listOf(TodayIntent.UndoMark(listOf("i-a", "i-b"))), intents)
    }

    private fun show(state: TodayUiState, onIntent: (TodayIntent) -> Unit = {}) {
        composeRule.setContent {
            SklyankaTheme { TodayScreen(state = state, onIntent = onIntent) }
        }
    }

    private companion object {
        const val TIME_14 = "14:00"
    }
}

/** Расклады экрана для проверок: те же числа, что в `acceptance.feature` и макетах. */
internal object TodayFixtures {

    val DATE: java.time.LocalDate = java.time.LocalDate.of(2026, 9, 25)
    val GROUP_TIME: LocalTime = LocalTime.of(14, 0)
    const val LOSARTAN = "Лозартан"
    const val GROUP_SIZE = 3

    /** Профиль, которому принадлежат лекарства заглушки: в чужом контексте это чужой профиль. */
    const val CARD_PROFILE_ID = "profile-card"

    /** Владелец телефона — он же исполнитель отметки в чужом профиле (ПР-12, ИНВ-18). */
    const val ACTOR_PROFILE_ID = "profile-owner"

    /**
     * Обычный день: принятый приём, просроченный, наступивший и ещё впереди.
     *
     * [markedByYou] ставит у принятого приёма исполнителя отметки: в чужом профиле владелец
     * телефона отметил приём своей рукой, и под фактическим временем стоит «отметили вы»
     * (ПРФ-14). В своём профиле такой отметки не бывает по построению (ИНВ-18).
     */
    fun plainDay(markedByYou: Boolean = false): TodayUiState.Content = content(
        layout = TodayLayout.Plain(
            listOf(
                group(
                    time = LocalTime.of(8, 0),
                    isNow = false,
                    items = listOf(
                        item("i-taken", "Эутирокс", LocalTime.of(8, 0), IntakePresentation.TAKEN, markedByYou),
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

    /**
     * Три приёма на одно время: строки, подпись группы и батч-кнопка (ОВ-32).
     *
     * [awaiting] — сколько приёмов ещё ждут отметки; остальные отмечены принятыми.
     * [isNow] — наступило ли время группы: у будущей батча и подписи нет вовсе.
     */
    fun groupDay(awaiting: Int = GROUP_SIZE, isNow: Boolean = true): TodayUiState.Content = content(
        layout = TodayLayout.Plain(
            listOf(
                group(
                    time = GROUP_TIME,
                    isNow = isNow,
                    items = listOf(
                        groupItem("i-a", "Магний B6", awaiting > 0),
                        groupItem("i-b", "Панангин", awaiting > 1),
                        groupItem("i-c", "Омез", awaiting > 2),
                    ),
                ),
            ),
        ),
    )

    private fun groupItem(id: String, name: String, awaiting: Boolean): IntakeItem =
        item(id, name, GROUP_TIME, if (awaiting) IntakePresentation.DUE_NOW else IntakePresentation.TAKEN)

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

    /** Окно отмены после отметки: текст называет лекарство, действие одно (SKL-30). */
    fun undoNotice(intakeIds: List<String> = listOf("i-due"), names: List<String> = listOf("Магний B6")): UndoNotice =
        UndoNotice(
            intakeIds = intakeIds,
            result = MarkResult.TAKEN,
            medicationNames = names,
        )

    private fun content(
        layout: TodayLayout,
        prn: List<PrnEntry> = emptyList(),
        stockWarning: StockNotice? = null,
        progress: DayProgress = DayProgress(taken = 0, planned = 1, medicationCount = 1),
        reliability: ReminderWarning? = null,
    ) = TodayUiState.Content(
        date = DATE,
        progress = progress,
        stockWarning = stockWarning,
        reliability = reliability,
        layout = layout,
        prn = prn,
        toggledSections = emptySet(),
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

    private fun item(
        id: String,
        name: String,
        time: LocalTime,
        presentation: IntakePresentation,
        markedByYou: Boolean = false,
    ): IntakeItem {
        val medication = medication(id = "m-$id", name = name, times = listOf(time))
        return IntakeItem(
            intake = ru.stohastika.sklyanka.domain.model.Intake(
                id = id,
                medicationId = medication.id,
                scheduledAt = DATE.atTime(time),
                status = presentation.status(),
                actedAt = null,
                source = null,
                // Исполнитель отметки заполнен ровно тогда, когда он отличается от профиля
                // приёма (ИНВ-18) — именно это и означает подпись «отметили вы».
                actorProfileId = ACTOR_PROFILE_ID.takeIf { markedByYou },
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
    ) = ru.stohastika.sklyanka.domain.model.Medication(
        id = id,
        profileId = CARD_PROFILE_ID,
        name = name,
        form = ru.stohastika.sklyanka.domain.model.MedicationForm.TABLET,
        shape = ru.stohastika.sklyanka.domain.model.TabletShape.ROUND,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = null,
        strengthUnit = null,
        foodRelation = ru.stohastika.sklyanka.domain.model.FoodRelation.ANY,
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
