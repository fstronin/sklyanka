package ru.heuri.sklyanka.feature.meds

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import ru.heuri.sklyanka.domain.medication.CourseProgress
import ru.heuri.sklyanka.domain.medication.DayCell
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.MedicationSubtitle
import ru.heuri.sklyanka.domain.medication.WeekAdherenceStrip
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.feature.meds.card.MedCardUiState
import ru.heuri.sklyanka.feature.meds.card.MedicationCardScreen
import ru.heuri.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.heuri.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.heuri.sklyanka.feature.meds.card.TAG_COURSE_BAR
import ru.heuri.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.heuri.sklyanka.feature.meds.card.TAG_LIMIT_BLOCK
import ru.heuri.sklyanka.feature.meds.card.TAG_RESTORE
import ru.heuri.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.heuri.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.heuri.sklyanka.feature.meds.card.TAG_SUBTITLE
import ru.heuri.sklyanka.feature.meds.card.TAG_TIME_CHIP
import ru.heuri.sklyanka.feature.meds.card.TAG_WEEK_BLOCK
import ru.heuri.sklyanka.feature.meds.card.WatchMirrorUi
import ru.heuri.sklyanka.ui.component.TAG_ADHERENCE_DAY
import ru.heuri.sklyanka.ui.component.TAG_ADHERENCE_LEGEND
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Карточка лекарства: блоки, бессрочный курс, скрытый остаток, архивный вид, часы.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class MedicationCardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theTitleCarriesTheNameAndTheSubtitleOfFourFacts() {
        show(state())

        composeRule.onNodeWithText(MEDICATION_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SUBTITLE).assertIsDisplayed()
    }

    @Test
    fun theScheduleBlockShowsTheFrequencyTheDoseAndTheTimeChips() {
        show(state())

        composeRule.onNodeWithTag(TAG_SCHEDULE_BLOCK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_TIME_CHIP, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText(MORNING_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(EVENING_TEXT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theCourseBlockShowsThePeriodAndTheDaysLeft() {
        show(state())

        composeRule.onNodeWithTag(TAG_COURSE_BLOCK).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_COURSE_BAR, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.card_course_days_left, DAYS_LEFT, DAYS_LEFT),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun anOpenCourseShowsNeitherABarNorTheDaysLeft() {
        show(state(course = CourseProgress.Open(START_DATE)))

        composeRule.onNodeWithTag(TAG_COURSE_BLOCK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_COURSE_BAR, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theStockBlockDisappearsWhenTheStockIsNotTracked() {
        show(state(stock = StockPanel.Hidden))

        composeRule.onAllNodesWithTag(TAG_STOCK_BLOCK).assertCountEquals(0)
    }

    @Test
    fun theStockWarningIsCarriedByTextAndNotByColourAlone() {
        show(state(stock = StockPanel.Visible(unitsLeft = BigDecimal("9"), daysLeft = 4, warning = true)))

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.card_stock_days_left, STOCK_DAYS, STOCK_DAYS),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun theWeekShowsSevenDaysALegendAndTheCounter() {
        show(state())

        composeRule.onNodeWithTag(TAG_WEEK_BLOCK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_ADHERENCE_DAY, useUnmergedTree = true).assertCountEquals(7)
        composeRule.onNodeWithTag(TAG_ADHERENCE_LEGEND, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(UiR.string.adherence_legend_today), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** ЭКР-6: сегодняшний день подписан «сегодня», а не числом принятых приёмов. */
    @Test
    fun todayIsDescribedAsTodayAndNotAsATakenDay() {
        show(state())

        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.cd_adherence_today, todayWeekday()),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun aMedicationTakenAsNeededShowsALimitInsteadOfASchedule() {
        show(
            state(
                schedule = ScheduleBlockUi.AsNeeded(maxPerDay = 3),
                course = null,
            ),
        )

        composeRule.onNodeWithTag(TAG_LIMIT_BLOCK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_SCHEDULE_BLOCK).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_COURSE_BLOCK).assertCountEquals(0)
    }

    /**
     * Строки «Напоминать на часах» на карточке нет — отдельной проверкой.
     *
     * Это решение `ADR-0009` (интеграция с Wear Engine отложена до пост-MVP), а не
     * упущение: без теста строка однажды вернётся сама, вместе с состоянием `watchMirror`.
     */
    @Test
    fun thereIsNoWatchRowOnTheCard() {
        show(state())

        composeRule.onAllNodesWithTag(TAG_WATCH_ROW).assertCountEquals(0)
    }

    @Test
    fun theBackArrowIsLabelledForAScreenReader() {
        show(state())

        composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_back)).assertIsDisplayed()
    }

    @Test
    fun anArchivedCardOffersRestoreAndCarriesNoWatchSwitch() {
        show(state(isArchived = true))

        composeRule.onNodeWithTag(TAG_RESTORE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_ARCHIVE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_WATCH_ROW).assertCountEquals(0)
    }

    /**
     * Строки выбора звука на карточке нет — отдельной проверкой.
     *
     * Это осознанное решение (НАП-27, вторая версия), а не упущение: без теста оно
     * выглядит как забытая строка и однажды будет «дополнено».
     */
    @Test
    fun thereIsNoSoundRowOnTheCard() {
        show(state())

        composeRule.onAllNodesWithTag(TAG_SOUND_ROW).assertCountEquals(0)
    }

    /**
     * Название дня недели тем же шаблоном, каким его собирает экран.
     *
     * Русского текста в коде нет ни в тестах (НФТ-42): день берётся из ресурса-шаблона
     * и локали устройства, а не пишется буквами.
     */
    private fun todayWeekday(): String = TODAY.format(
        DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern_weekday_full),
            context.resources.configuration.locales[0],
        ),
    )

    private fun show(state: MedCardUiState) {
        composeRule.setContent {
            SklyankaTheme { MedicationCardScreen(state = state, onIntent = {}) }
        }
    }

    @Suppress("LongParameterList")
    private fun state(
        schedule: ScheduleBlockUi = ScheduleBlockUi.Scheduled(
            timesPerDay = 2,
            doseAmount = BigDecimal("2"),
            doseUnit = DoseUnit.TABLET,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        ),
        course: CourseProgress? = CourseProgress.Bounded(
            start = START_DATE,
            end = END_DATE,
            daysLeft = DAYS_LEFT,
            fraction = COURSE_FRACTION,
        ),
        stock: StockPanel = StockPanel.Visible(unitsLeft = BigDecimal("9"), daysLeft = 4, warning = true),
        isArchived: Boolean = false,
    ) = MedCardUiState(
        medicationId = "medication-1",
        title = MEDICATION_NAME,
        form = MedicationForm.TABLET,
        subtitle = MedicationSubtitle(
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND_SCORED,
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
        ),
        schedule = schedule,
        course = course,
        stock = stock,
        stockUnit = DoseUnit.TABLET,
        week = week(),
        // Состояние часов намеренно непустое: строки на экране нет именно потому,
        // что её вывел из состава MVP `ADR-0009`, а не потому, что состояние пусто.
        watchMirror = WatchMirrorUi(checked = true, enabled = true),
        isArchived = isArchived,
        courseFinishedOffer = false,
        refill = null,
        notice = null,
    )

    /** Шесть завершившихся дней по два принятых приёма и сегодняшний день полым кружком. */
    private fun week(): WeekAdherenceStrip {
        val days = (1..6).map { back ->
            val date = TODAY.minusDays(back.toLong())
            DayCell.Completed(date = date, taken = 2, planned = 2)
        }.reversed() + DayCell.Today(TODAY)
        return WeekAdherenceStrip(days = days, taken = 12, planned = 14)
    }

    private companion object {
        const val MEDICATION_NAME = "med-a"
        const val MORNING_TEXT = "08:00"
        const val EVENING_TEXT = "20:00"
        const val DAYS_LEFT = 17
        const val STOCK_DAYS = 4
        const val COURSE_FRACTION = 0.43f

        /** Строки выбора звука на карточке нет: тега тоже нет ни в одном компоненте. */
        const val TAG_SOUND_ROW = "med_card_sound_row"

        /** Строки часов на карточке нет: тега тоже нет ни в одном компоненте (`ADR-0009`). */
        const val TAG_WATCH_ROW = "med_card_watch_row"

        val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 12)
        val END_DATE: LocalDate = LocalDate.of(2026, 10, 12)
    }
}
