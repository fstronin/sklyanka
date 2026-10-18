package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экран истории: состав, тексты из макета, состояния и индикация профиля.
 *
 * Идёт на JVM под Robolectric (SKL-24): настоящая композиция, настоящие ресурсы модуля,
 * настоящая семантика. Чего стенд не доказывает — вид на настоящем экране EMUI и вёрстку
 * глазами (`docs/archive/tasks/SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Шапка несёт возврат и название; действие выгрузки живёт в конце содержимого (Р-88). */
    @Test
    fun theHeaderCarriesTheTitleAndTheReturn() {
        show(HistoryStates.september())

        composeRule.onNodeWithText(context.getString(R.string.history_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_back)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_action))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** «Отчёт врачу» — последний блок содержимого, ниже разбивки и во всю ширину (Р-88). */
    @Test
    fun theReportActionStandsAtTheEndOfTheContent() {
        show(HistoryStates.september())

        val header = composeRule.onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val summary = composeRule.onNodeWithTag(TAG_SUMMARY).fetchSemanticsNode()
        val breakdown = composeRule.onNodeWithTag(TAG_BREAKDOWN).fetchSemanticsNode()
        val action = composeRule.onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        assertTrue(
            "the action must stand below the header, but it is ${action.boundsInRoot} and the header ends at ${header.boundsInRoot.bottom}",
            action.boundsInRoot.top >= header.boundsInRoot.bottom,
        )
        assertTrue(
            "the action must follow the breakdown, but it is ${action.boundsInRoot} and the breakdown ends at ${breakdown.boundsInRoot.bottom}",
            action.boundsInRoot.top >= breakdown.boundsInRoot.bottom,
        )
        // Ширина содержимого — по ряду плиток: у карточки разбивки тег стоит внутри её
        // поля, и её габарит уже на два поля.
        assertEquals(
            "the action must span the content width: ${action.size.width} vs ${summary.size.width}",
            summary.size.width,
            action.size.width,
        )
    }

    /**
     * Развёрнутая шапка начинается своим верхним полем: ряд места не держит (§7).
     *
     * Закреплённый ряд — слой над прокруткой, и до порога его не видно: 56 dp в потоке
     * не резервируются, иначе над рядом возврата и крупным заголовком встала бы пустая
     * полоса. Отсчёт идёт от верхней грани шапки до её первой строки — ряда возврата.
     */
    @Test
    fun theHeaderStartsAtItsOwnTopPaddingWhileTheRowIsHidden() {
        show(HistoryStates.september())

        val header = composeRule.onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val back = composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_back))
            .fetchSemanticsNode()
        val gap = with(composeRule.density) { (back.boundsInRoot.top - header.boundsInRoot.top).toDp() }
        val padding = with(composeRule.density) { SklyankaSizes.HeaderTopPadding }
        assertTrue(
            "the return row stands $gap from the header top, and only the top padding ($padding) is allowed",
            gap <= padding + 1.dp,
        )
    }

    /** Возврат экран не исполняет: куда он ведёт, решает тот, кто собрал маршрут. */
    @Test
    fun theBackActionLeavesTheScreenToItsCaller() {
        val intents = mutableListOf<HistoryIntent>()
        show(HistoryStates.september()) { intents += it }

        composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_back)).performClick()

        assertEquals(listOf(HistoryIntent.Back), intents)
    }

    /** Три плитки с подписями дословно из макета. */
    @Test
    fun theSummaryShowsThreeCaptionsWithNumbersAboveThem() {
        val state = HistoryStates.september()
        show(state)

        composeRule.onNodeWithTag(TAG_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_tile_on_time), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.history_tile_days_without_misses),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_tile_misses), useUnmergedTree = true)
            .assertIsDisplayed()
        // Число ищется по подписи плитки, а не по тексту «9»: тот же «9» стоит в клетке
        // календаря, и проверка по тексту нашла бы два узла и ничего не утверждала.
        composeRule
            .onNodeWithContentDescription(
                context.resources.getQuantityString(
                    R.plurals.cd_history_days_without_misses,
                    state.period.daysWithoutMisses,
                    state.period.daysWithoutMisses,
                ),
            )
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                context.resources.getQuantityString(
                    R.plurals.cd_history_misses,
                    state.period.missesInPeriod,
                    state.period.missesInPeriod,
                ),
            )
            .assertIsDisplayed()
    }

    /**
     * Р-21/Р-27: плитка «принято вовремя» показывает посчитанную долю, а не прочерк.
     *
     * Проверка стоит здесь, а не только в домене, потому что это видимое обещание экрана:
     * подмена доли прочерком или чужим числом обязана падать.
     */
    @Test
    fun theOnTimeTileShowsTheCountedShare() {
        val state = HistoryStates.september()
        show(state)

        val share = requireNotNull(state.period.onTimeShare as? OnTimeShare.Value) {
            "the fixture must carry a counted share for this check to mean anything"
        }
        composeRule.onNodeWithTag(TAG_TILE_ON_TIME, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_percent, share.percent), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Р-33: когда мерить нечего, плитки «принято вовремя» нет вовсе, а не прочерк.
     *
     * Прочерк читался как поломка (V-2): доля либо посчитана, либо плитки нет, и две
     * оставшиеся плитки делят ряд пополам.
     */
    @Test
    fun theOnTimeTileIsAbsentWhenThereIsNothingToMeasure() {
        val state = HistoryStates.september().let {
            it.copy(period = it.period.copy(onTimeShare = OnTimeShare.Absent))
        }
        show(state)

        composeRule.onAllNodesWithTag(TAG_TILE_ON_TIME, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theMonthSwitchShowsTheMonthAndArrowsAreLabelledForAScreenReader() {
        val intents = mutableListOf<HistoryIntent>()
        show(HistoryStates.september()) { intents += it }

        composeRule.onNodeWithTag(TAG_MONTH_LABEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(SEPTEMBER_LABEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_previous_month)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_next_month)).performClick()

        assertEquals(listOf(HistoryIntent.PreviousMonth, HistoryIntent.NextMonth), intents)
    }

    @Test
    fun theWeekHeaderAndTheLegendComeFromTheMockup() {
        show(HistoryStates.september())

        listOf(
            R.string.weekday_monday,
            R.string.weekday_tuesday,
            R.string.weekday_wednesday,
            R.string.weekday_thursday,
            R.string.weekday_friday,
            R.string.weekday_saturday,
            R.string.weekday_sunday,
        ).forEach { weekday ->
            composeRule.onNodeWithText(context.getString(weekday), useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(TAG_LEGEND, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_legend_all_taken), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_legend_partial), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_legend_missed), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theGridHoldsOneCellPerDayOfTheMonth() {
        show(HistoryStates.september())

        composeRule.onAllNodesWithTag(TAG_DAY, useUnmergedTree = true).assertCountEquals(SEPTEMBER_DAYS)
    }

    /**
     * НФТ-33: состояние дня названо словами, а не только цветом.
     *
     * Легенда из трёх цветов сама по себе недоступна, и клетка без подписи для человека,
     * не различающего эти три цвета, не значит ничего.
     */
    @Test
    fun everyDayStateIsCarriedByWordsAndNotByColourAlone() {
        show(HistoryStates.september())

        listOf(
            LocalDate.of(2026, 9, 1) to R.string.history_legend_all_taken,
            LocalDate.of(2026, 9, HistoryStates.PARTIAL_DAY) to R.string.history_legend_partial,
            LocalDate.of(2026, 9, HistoryStates.MISSED_DAY) to R.string.history_legend_missed,
            LocalDate.of(2026, 9, HistoryStates.NO_INTAKES_DAY) to R.string.history_state_no_intakes,
        ).forEach { (date, state) ->
            composeRule
                .onNodeWithContentDescription(
                    HistoryStates.dayDescription(context, date, state),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }
    }

    @Test
    fun todayIsMarkedApartFromTheOtherDays() {
        show(HistoryStates.september())

        composeRule
            .onNodeWithContentDescription(
                HistoryStates.todayDescription(context, HistoryStates.TODAY, R.string.history_state_ahead),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    /** Дни до начала ведения истории подписаны как таковые, а не как пропуски. */
    @Test
    fun theDaysBeforeTheHistoryBeganAreNotCalledMisses() {
        show(HistoryStates.shortHistory())

        composeRule
            .onNodeWithContentDescription(
                HistoryStates.dayDescription(context, LocalDate.of(2026, 9, 3), R.string.history_state_before_history),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun theBreakdownShowsEveryMedicationWithItsShare() {
        show(HistoryStates.september())

        composeRule.onNodeWithTag(TAG_BREAKDOWN).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_BREAKDOWN_ROW, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("med-a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_percent, 97), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_percent, 72), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** ПР-11: архивированный курс остаётся в истории и помечен словом, а не цветом. */
    @Test
    fun anArchivedCourseStaysInTheBreakdownAndIsMarkedByAWord() {
        show(HistoryStates.withArchivedCourse())

        composeRule.onNodeWithText("med-c", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ARCHIVED_MARK, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_archived_course), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theInsightLineNamesTheMostMissedTimeOfDay() {
        show(HistoryStates.september())

        composeRule.onNodeWithTag(TAG_INSIGHT, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(INSIGHT_TEXT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun withoutAClearLeaderTheInsightLineIsAbsent() {
        show(HistoryStates.september().copy(insightTime = null))

        composeRule.onAllNodesWithTag(TAG_INSIGHT, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * Пустой месяц: прочерки у двух плиток, пустой календарь, скрытый блок «По лекарствам».
     *
     * Плитки «принято вовремя» нет вовсе — мерить нечего (Р-33), а не прочерк. И ни одного
     * сообщения об ошибке: месяц без записей — это нормальное состояние, а не сбой
     * (`docs/norm/04-behavior.md`, состояние «Данных за месяц нет»).
     */
    @Test
    fun anEmptyMonthShowsDashesAndHidesTheBreakdown() {
        show(HistoryStates.emptyMonth())

        composeRule.onAllNodesWithTag(TAG_BREAKDOWN).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_INSIGHT, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_TILE_ON_TIME, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(TAG_CALENDAR).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.history_value_dash), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    /**
     * ПРФ-11, ПРФ-12: в контексте чужого профиля экран несёт индикацию.
     *
     * Ни одного признака экран при этом не рисует сам: полосу даёт обёртка оформления
     * профиля, надпись и баннер — общая шапка. Проверка утверждает, что цепочка не
     * разорвана на этом экране, — а именно она и есть требование ПРФ-12.
     */
    @Test
    fun aForeignProfileIsVisibleAsForeign() {
        composeRule.setContent {
            ProfileChrome(ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)) {
                HistoryScreen(state = HistoryStates.september(), onIntent = {})
            }
        }

        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(UiR.string.profile_banner, PROFILE_NAME), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** ПРФ-14: отметка владельца телефона в чужом профиле подписана «отметили вы». */
    @Test
    fun aMarkMadeByThePhoneOwnerIsCaptioned() {
        composeRule.setContent {
            ProfileChrome(ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)) {
                HistoryScreen(state = HistoryStates.foreignProfile(), onIntent = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MARKED_BY_YOU, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(UiR.string.status_marked_by_you), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** ПРФ-14: в истории своего профиля такой подписи нет. */
    @Test
    fun yourOwnHistoryCarriesNoSuchCaption() {
        show(HistoryStates.september())

        composeRule.onAllNodesWithTag(TAG_MARKED_BY_YOU, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * SKL-44: шторка отчёта предъявляется и в реальной обёртке экрана.
     *
     * Проба повторяет корень приложения — `ProfileChrome { HistoryScreen(...) }`.
     * На старом коде шторка стояла соседом в столбце и получала нулевую высоту: на
     * устройстве «Отчёт врачу» строил документ молча. Прямой рендер
     * `DoctorReportSheetTest` этого не ловил — там шторка была корневым ребёнком, —
     * и проверка обязана краснеть, пока слои не разведены.
     */
    @Test
    fun theReportSheetIsPresentedOverTheHistoryInTheRealWrapper() {
        var state by mutableStateOf(HistoryStates.september())
        composeRule.setContent {
            ProfileChrome(ProfileChromeState.Owner) {
                HistoryScreen(
                    state = state,
                    onIntent = { intent ->
                        if (intent == HistoryIntent.ReportRequested) state = HistoryStates.withReport()
                    },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()

        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_DATA_NOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_SHARE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_SAVE).assertIsDisplayed()
    }

    /** SKL-44: «Отмена» возвращает экран истории, и шторку можно открыть снова. */
    @Test
    fun theReportCanBeClosedAndOpenedAgainInTheRealWrapper() {
        var state by mutableStateOf(HistoryStates.september())
        composeRule.setContent {
            ProfileChrome(ProfileChromeState.Owner) {
                HistoryScreen(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            HistoryIntent.ReportRequested -> state = HistoryStates.withReport()
                            HistoryIntent.ReportDismissed -> state = state.copy(report = null)
                            else -> Unit
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(UiR.string.action_cancel)).performClick()

        composeRule.onAllNodesWithTag(TAG_REPORT_SHEET).assertCountEquals(0)

        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()

        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
    }

    /**
     * НФТ-29: у каждой нажимаемой цели экрана не меньше 44 dp.
     *
     * Стрелки месяца нарисованы 32 dp по макету, и область нажатия доводится
     * модификатором, а не размером рисунка. Клетки календаря в обход не попадают: они
     * не нажимаются вовсе (раскрытие дня вынесено из объёма задачи).
     */
    @Test
    fun everyClickableTargetIsAtLeastTheMinimum() {
        show(HistoryStates.september())

        val nodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("the screen must have clickable elements", nodes.isNotEmpty())
        nodes.forEach { node ->
            val density = node.layoutInfo.density
            val width = with(density) { node.size.width.toDp() }
            val height = with(density) { node.size.height.toDp() }
            val minimum = SklyankaSizes.MinTouchTarget - TOLERANCE
            assertTrue(
                "clickable node ${node.id} is $width×$height, below ${SklyankaSizes.MinTouchTarget}",
                width >= minimum && height >= minimum,
            )
        }
    }

    private fun show(state: HistoryUiState, onIntent: (HistoryIntent) -> Unit = {}) {
        composeRule.setContent {
            SklyankaTheme { HistoryScreen(state = state, onIntent = onIntent) }
        }
    }

    private companion object {
        const val SEPTEMBER_DAYS = 30
        const val PROFILE_NAME = "profile-b"

        /** Тексты, которые обязаны совпасть с макетом дословно. */
        const val SEPTEMBER_LABEL = "Сентябрь 2026"
        const val INSIGHT_TEXT = "Чаще всего пропускается вечерний приём в 20:00."

        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
