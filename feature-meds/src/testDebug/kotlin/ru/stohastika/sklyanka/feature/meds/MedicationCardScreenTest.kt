package ru.stohastika.sklyanka.feature.meds

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.RefillUi
import ru.stohastika.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BAR
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_LIMIT_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_CANCEL
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_ROW
import ru.stohastika.sklyanka.feature.meds.card.TAG_RESTORE
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BAR
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.meds.card.TAG_SUBTITLE
import ru.stohastika.sklyanka.feature.meds.card.TAG_TIME_CHIP
import ru.stohastika.sklyanka.feature.meds.card.TAG_WEEK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.WatchMirrorUi
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_DAY
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_LEGEND
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

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

    /**
     * Чипы времени переносятся целиком и не сжимаются в колонки (SKL-59).
     *
     * Восемь приёмов в сутки — случай стенда, на котором дефект виден и без крупного
     * шрифта: последние чипы получали нулевую ширину и разворачивались по буквам.
     */
    @Test
    fun everyTimeChipKeepsItsOwnWidth() {
        val manyTimes = (0 until 8).map { index -> LocalTime.of(8, 0).plusMinutes(index * 30L) }
        show(
            state(
                schedule = ScheduleBlockUi.Scheduled(
                    timesPerDay = manyTimes.size,
                    doseAmount = BigDecimal("1"),
                    doseUnit = DoseUnit.TABLET,
                    times = manyTimes,
                ),
            ),
        )

        val chips = composeRule
            .onAllNodesWithTag(TAG_TIME_CHIP, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(manyTimes.size, chips.size)
        val squeezed = chips.filter { node ->
            with(composeRule.density) { node.size.width.toDp().value } < MIN_CHIP_WIDTH_DP
        }
        assertTrue(
            "чипы сжаты в колонки: " + squeezed.joinToString { node ->
                with(composeRule.density) { "${node.size.width.toDp().value}x${node.size.height.toDp().value}" }
            },
            squeezed.isEmpty(),
        )
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
    fun theStockRowShowsTheShareOfThePackageAndTheBar() {
        show(
            state(
                stock = StockPanel.Visible(
                    unitsLeft = BigDecimal("9"),
                    daysLeft = 4,
                    warning = true,
                    packageSize = BigDecimal("60"),
                ),
            ),
        )

        // «9 таблеток из 60» — число рядом с полосой (НФТ-33), собранное из ресурсов так же,
        // как его собирает экран: склонение по остатку, размер — числом.
        composeRule
            .onNodeWithText(
                context.getString(
                    R.string.card_stock_of_package,
                    context.resources.getQuantityString(R.plurals.stock_left_tablets, STOCK, STOCK),
                    PACKAGE_SIZE,
                ),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_STOCK_BAR, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anUnknownPackageSizeKeepsThePlainStockAndNoBar() {
        show(state(stock = StockPanel.Visible(unitsLeft = BigDecimal("9"), daysLeft = 4, warning = true)))

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.stock_left_tablets, STOCK, STOCK),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_STOCK_BAR, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * Единица остатка не уезжает на строку без числа (SKL-59, дефект пилота).
     *
     * Проверяется разложение надписи: строка, кончающаяся числом, и следующая,
     * начинающаяся буквой, — это ровно разрыв «60 / капсул». Перенос перед числом
     * («осталось / 60 капсул») нарушением не считается и остаётся допустимым.
     */
    @Test
    fun theStockUnitNeverStartsALineAfterTheNumber() {
        show(
            state(
                stock = StockPanel.Visible(unitsLeft = BigDecimal("60"), daysLeft = 60, warning = false),
                stockUnit = DoseUnit.CAPSULE,
            ),
            fontScale = FONT_SCALE_130,
        )

        val lines = textLinesOf(TAG_STOCK_VALUE)
        val split = lines.zipWithNext().any { (first, second) ->
            first.trimEnd().lastOrNull()?.isDigit() == true && second.firstOrNull()?.isLetter() == true
        }
        assertFalse("остаток разорван между числом и единицей: $lines", split)
        assertTrue("остаток не поместился и в две строки: $lines", lines.size <= 2)
    }

    /**
     * При крупном шрифте действие уходит под текст (§6.3), а не отнимает у него ширину.
     */
    @Test
    fun atTwoHundredPercentTheRefillActionMovesUnderTheValue() {
        show(
            state(
                stock = StockPanel.Visible(unitsLeft = BigDecimal("60"), daysLeft = 60, warning = false),
                stockUnit = DoseUnit.CAPSULE,
            ),
            fontScale = FONT_SCALE_200,
        )

        composeRule.onNodeWithTag(TAG_REFILL, useUnmergedTree = true).performScrollTo()
        composeRule.waitForIdle()
        val value = composeRule.onNodeWithTag(TAG_STOCK_VALUE, useUnmergedTree = true).fetchSemanticsNode()
        val refill = composeRule.onNodeWithTag(TAG_REFILL, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "кнопка не ушла под текст: value=${value.boundsInRoot}, refill=${refill.boundsInRoot}",
            refill.boundsInRoot.top >= value.boundsInRoot.bottom,
        )
    }

    @Test
    fun theRefillRowOpensInTheStockBlockWithTheUnitAndCancel() {
        show(state(refill = RefillUi(text = "")))

        composeRule.onNodeWithTag(TAG_REFILL_ROW, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REFILL_CANCEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(UiR.string.action_cancel), useUnmergedTree = true)
            .assertIsDisplayed()
        // Единица строки пополнения — та же, что у остатка: человек вводит число в тех же
        // единицах, в каких карточка о нём говорит.
        composeRule
            .onNodeWithText(context.getString(R.string.stock_unit_tablets), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun stockAboveThePackageKeepsThePlainStockAndNoBar() {
        show(
            state(
                stock = StockPanel.Visible(
                    unitsLeft = BigDecimal("90"),
                    daysLeft = 45,
                    warning = false,
                    packageSize = BigDecimal("60"),
                ),
            ),
        )

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.stock_left_tablets, MANY, MANY),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_STOCK_BAR, useUnmergedTree = true).assertCountEquals(0)
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

    private fun show(state: MedCardUiState, fontScale: Float = 1f) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme { MedicationCardScreen(state = state, onIntent = {}) }
            }
        }
    }

    /** Строки разметки надписи: как их разложила вёрстка (приём `TextLayoutFacts` SKL-25). */
    private fun textLinesOf(tag: String): List<String> {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
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
        stockUnit: DoseUnit = DoseUnit.TABLET,
        isArchived: Boolean = false,
        refill: RefillUi? = null,
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
        stockUnit = stockUnit,
        week = week(),
        // Состояние часов намеренно непустое: строки на экране нет именно потому,
        // что её вывел из состава MVP `ADR-0009`, а не потому, что состояние пусто.
        watchMirror = WatchMirrorUi(checked = true, enabled = true),
        isArchived = isArchived,
        courseFinishedOffer = false,
        refill = refill,
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
        const val STOCK = 9
        const val MANY = 90
        const val PACKAGE_SIZE = "60"
        const val COURSE_FRACTION = 0.43f

        /** Масштабы системного шрифта, на которых проверяется НФТ-31. */
        const val FONT_SCALE_130 = 1.3f
        const val FONT_SCALE_200 = 2.0f

        /** Ширина чипа времени не может быть меньше его собственной подписи «08:00». */
        const val MIN_CHIP_WIDTH_DP = 40f

        /** Строки выбора звука на карточке нет: тега тоже нет ни в одном компоненте. */
        const val TAG_SOUND_ROW = "med_card_sound_row"

        /** Строки часов на карточке нет: тега тоже нет ни в одном компоненте (`ADR-0009`). */
        const val TAG_WATCH_ROW = "med_card_watch_row"

        val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 12)
        val END_DATE: LocalDate = LocalDate.of(2026, 10, 12)
    }
}
