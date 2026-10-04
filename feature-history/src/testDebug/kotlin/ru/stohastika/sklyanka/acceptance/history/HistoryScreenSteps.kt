package ru.stohastika.sklyanka.acceptance.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MedicationAdherence
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ReportMedicationRow
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.MedicationBreakdown
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.ReportPdfComposer
import ru.stohastika.sklyanka.feature.history.ReportPdfContent
import ru.stohastika.sklyanka.feature.history.TAG_ARCHIVED_MARK
import ru.stohastika.sklyanka.feature.history.TAG_BREAKDOWN
import ru.stohastika.sklyanka.feature.history.TAG_BREAKDOWN_ROW
import ru.stohastika.sklyanka.feature.history.TAG_CALENDAR
import ru.stohastika.sklyanka.feature.history.TAG_DAY
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.history.TAG_INSIGHT
import ru.stohastika.sklyanka.feature.history.TAG_LEGEND
import ru.stohastika.sklyanka.feature.history.TAG_MARKED_BY_YOU
import ru.stohastika.sklyanka.feature.history.TAG_MONTH_LABEL
import ru.stohastika.sklyanka.feature.history.TAG_PREVIOUS_MONTH
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_ACTION
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_DATA_NOTE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.history.TAG_TILE_DAYS_WITHOUT_MISSES
import ru.stohastika.sklyanka.feature.history.TAG_TILE_MISSES
import ru.stohastika.sklyanka.feature.history.documentText
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки истории приёмов и отчёта врачу (SKL-18, группа `@screen`).
 *
 * Шаги живут в `feature-history`, потому что говорят об одном экране — истории
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий домен ([HistoryFromWorld]),
 * нажатия применяет стенд ([HistoryScreenProbe]); проверки собраны здесь.
 *
 * Предыстория сценариев — доменные шаги общего артефакта (`HistoryContextSteps`): экранные
 * шаги только читают её и не заводят данных, если сценарий этого не просит.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class HistoryScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val locale = context.resources.configuration.locales[0]

    /** Приверженность сентября до переключения месяца: ею проверяется «остаётся прежней». */
    private var septemberBefore: AdherencePeriod? = null

    // --- Действия ---------------------------------------------------------------

    @When("^человек смотрит на историю за (.+)$")
    fun thePersonLooksAtTheHistoryOf(monthName: String) = select(monthName)

    @Given("^человек смотрит историю за (.+)$")
    fun thePersonLooksAtTheHistoryOfAsGiven(monthName: String) = select(monthName)

    @When("^человек смотрит на календарь (.+)$")
    fun thePersonLooksAtTheCalendarOf(monthName: String) = select(monthName)

    @When("^человек смотрит на блок «По лекарствам»$")
    fun thePersonLooksAtTheBreakdown() = Unit

    @When("^человек смотрит на строку-вывод$")
    fun thePersonLooksAtTheInsight() = Unit

    @When("^человек открывает историю$")
    fun thePersonOpensTheHistory() = Unit

    @When("^человек открывает историю за (.+)$")
    fun thePersonOpensTheHistoryOf(monthName: String) = select(monthName)

    @When("^человек открывает историю профиля «([^»]+)» за (.+)$")
    fun thePersonOpensTheHistoryOfThatProfile(profile: String, monthName: String) {
        assertEquals("the scenario speaks about the active profile", profile, world.profiles.active)
        select(monthName)
    }

    @When("^человек нажимает «Предыдущий месяц»$")
    fun thePersonPressesThePreviousMonth() {
        HistoryScreenProbe.act(world) { test -> test.onNodeWithTag(TAG_PREVIOUS_MONTH).performClick() }
    }

    @When("^человек переключается на «(.+)»$")
    fun thePersonSwitchesTo(monthName: String) {
        val target = RussianDates.month(monthName, world.clock.today())
        if (HistoryScreenProbe.selected(world) == SEPTEMBER && target != SEPTEMBER) {
            septemberBefore = HistoryFromWorld.period(world, SEPTEMBER)
        }
        HistoryScreenProbe.switchTo(world, target)
    }

    // --- Проверки: сводка ---------------------------------------------------------

    @Then("^«пропусков за месяц» — (\\d+)$")
    fun theMissesOfTheMonthAre(count: Int) = assertTile(TAG_TILE_MISSES, R.plurals.cd_history_misses, count)

    @Then("^«дней без пропусков» — (\\d+)$")
    fun theDaysWithoutMissesAre(count: Int) =
        assertTile(TAG_TILE_DAYS_WITHOUT_MISSES, R.plurals.cd_history_days_without_misses, count)

    // --- Проверки: календарь -------------------------------------------------------

    @Then("^показана легенда «всё принято», «частично», «пропуск»$")
    fun theLegendIsShown() {
        val legend = HistoryScreenProbe.ask(world) { test -> texts(test, TAG_LEGEND) }
        listOf(R.string.history_legend_all_taken, R.string.history_legend_partial, R.string.history_legend_missed)
            .map { context.getString(it) }
            .forEach { word -> assertTrue("no «$word» in the legend: $legend", word in legend) }
    }

    @Then("^день 15 сентября показан как «([^»]+)»$")
    fun theFifteenthIsShownAs(word: String) {
        val expected = stateWord(word)
        val description = dayDescriptions().firstOrNull { it.startsWith(dayLabel(SEPTEMBER.atDay(15))) }
        assertNotNull("day 15 is not on the screen", description)
        assertTrue("day 15 must be shown as «$expected»: $description", description!!.contains(expected))
    }

    /**
     * НФТ-33: состояние дня названо словами, а не только цветом.
     *
     * Проверяется каждый день календаря, а не только дни с приёмами: легенда из трёх
     * цветов сама по себе недоступна, и клетка без подписи не значит ничего. Клеток
     * без подписи не остаётся ни одной — тогда и у дней с приёмами она есть.
     */
    @Then("^у каждого дня с приёмами состояние передано подписью или значком, а не одним цветом$")
    fun everyDayStateIsSpelledOut() {
        val words = listOf(
            R.string.history_legend_all_taken,
            R.string.history_legend_partial,
            R.string.history_legend_missed,
            R.string.history_state_no_intakes,
            R.string.history_state_ahead,
            R.string.history_state_before_history,
        ).map { context.getString(it) }
        val descriptions = dayDescriptions()
        assertEquals(
            "every day of the month must be on the screen",
            HistoryScreenProbe.selected(world).lengthOfMonth(),
            descriptions.size,
        )
        descriptions.forEach { description ->
            assertTrue(
                "a day carries no words, only colour: $description",
                words.any { description.contains(it) },
            )
        }
    }

    @Then("^30 сентября 2026 выделено как сегодняшний день$")
    fun theThirtiethIsMarkedAsToday() {
        val label = dayLabel(SEPTEMBER.atDay(30))
        val description = dayDescriptions().firstOrNull { it.startsWith(label) }
        assertNotNull("day 30 is not on the screen", description)
        assertTrue(
            "day 30 must be marked as today: $description",
            description!!.contains(todayMarker()),
        )
    }

    @Then("^показан «(.+)»$")
    fun theMonthIsShown(label: String) {
        val shown = HistoryScreenProbe.ask(world) { test -> texts(test, TAG_MONTH_LABEL) }
        assertTrue("no month «$label» on the screen: $shown", shown.any { it == label })
    }

    @Then("^календарь и разбивка по лекарствам показывают данные (.+)$")
    fun theCalendarAndTheBreakdownShow(monthName: String) {
        val month = RussianDates.month(monthName, world.clock.today())
        val period = HistoryFromWorld.period(world, month)
        assertEquals("the calendar must open exactly the named month", month, YearMonth.from(period.from))
        assertEquals("the calendar must open exactly the named month", month.atEndOfMonth(), period.to)
        assertEquals("the calendar must hold one cell per day", month.lengthOfMonth(), dayDescriptions().size)
        assertTrue("the breakdown of the month must have rows", period.byMedication.isNotEmpty())
        period.byMedication.forEach { row ->
            assertTrue(
                "the breakdown must show «${row.name}» of the named month: ${breakdownRowTexts(row.name)}",
                breakdownRowTexts(row.name).isNotEmpty(),
            )
        }
    }

    @Then("^дни с (\\d+) по (\\d+) сентября показаны как дни без приёмов$")
    fun thoseDaysAreShownAsDaysWithoutIntakes(from: Int, to: Int) {
        val neutral = listOf(R.string.history_state_no_intakes, R.string.history_state_before_history)
            .map { context.getString(it) }
        val descriptions = dayDescriptions()
        (from..to).forEach { day ->
            val date = SEPTEMBER.atDay(day)
            val description = descriptions.firstOrNull { it.startsWith(dayLabel(date)) }
            assertNotNull("day $day is not on the screen", description)
            assertTrue("day $day must stay neutral: $description", neutral.any { description!!.contains(it) })
        }
    }

    @Then("^они не учитываются как пропуски в сводке за месяц$")
    fun theyAreNotCountedAsMisses() {
        val period = HistoryFromWorld.period(world, HistoryScreenProbe.selected(world))
        assertTrue(
            "days before the history began must not be misses: " +
                period.days.filter { it.state == DayState.MISSED }.map { it.date },
            period.days.none { it.date.dayOfMonth in 1..11 && it.state == DayState.MISSED },
        )
        assertEquals("the summary must not count them", 0, period.missesInPeriod)
    }

    // --- Проверки: разбивка по лекарствам ------------------------------------------

    @Then("^показана строка «([^»]+)» с долей принятого (\\d+)%$")
    fun theRowShowsItsShare(medication: String, percent: Int) {
        val display = displayName(medication)
        val texts = breakdownRowTexts(display)
        assertTrue("no row «$display» on the screen: $texts", display in texts)
        val expected = context.getString(R.string.history_percent, percent)
        assertTrue("the row «$display» must show $expected: $texts", expected in texts)
    }

    /**
     * Лекарство с низкой приверженностью выделено.
     *
     * «Выделено» — это цвет полосы, и цвет в семантике не живёт. Проверяются обе
     * половины: правило домена (`low`) и картинка (акцентная полоса есть ровно тогда,
     * когда правило её просит). Без второй половины подмена `row.low` в разметке
     * осталась бы незамеченной.
     */
    @Then("^строка «([^»]+)» выделена визуально иначе, чем строки с высокой долей принятого$")
    fun theRowIsVisuallyDifferent(medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        val rows = period().byMedication
        val named = requireNotNull(rows.firstOrNull { it.medicationId == id }) { "no row for «$medication»" }
        assertTrue("the rule must mark «$medication» as low: $named", named.low)
        assertTrue(
            "the rows with a high share must not be low: ${rows.filterNot { it.medicationId == id }}",
            rows.filterNot { it.medicationId == id }.none { it.low },
        )
        assertTrue("the accent bar of the low row is not drawn", accentPixels(rows) > 0)
        assertEquals(
            "without the low flag the breakdown must carry no accent at all",
            0,
            accentPixels(rows.map { it.copy(low = false) }),
        )
    }

    @Then("^в блоке «По лекарствам» есть строка «([^»]+)» с долей принятого за период курса$")
    fun theArchivedCourseHasItsRow(medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        val row = requireNotNull(period().byMedication.firstOrNull { it.medicationId == id }) {
            "the archived course must stay in the history (ПР-11)"
        }
        assertTrue("the course must be marked as archived: $row", row.archived)
        assertEquals("the row must count the course period, not the month", COURSE_INTAKES, row.planned)
        assertTrue("the row of «$medication» is not on the screen", breakdownRowTexts(row.name).isNotEmpty())
        val marked = HistoryScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_ARCHIVED_MARK, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("the archived course must be marked by a word, not only by colour", marked)
    }

    @Then("^дни с (\\d+) по (\\d+) сентября учитывают приёмы «([^»]+)» в календаре$")
    fun thoseDaysAccountForThatMedication(from: Int, to: Int, medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        val days = period().days.associateBy { it.date }
        val descriptions = dayDescriptions()
        val neutral = listOf(R.string.history_state_no_intakes, R.string.history_state_before_history)
            .map { context.getString(it) }
        (from..to).forEach { day ->
            val date = SEPTEMBER.atDay(day)
            val outcome = requireNotNull(days[date]) { "no outcome for $date" }
            assertTrue(
                "day $day must account for the intakes of «$medication»: ${outcome.state}",
                outcome.state.hasIntakes,
            )
            val description = descriptions.firstOrNull { it.startsWith(dayLabel(date)) }
            assertNotNull("day $day is not on the screen", description)
            assertTrue(
                "day $day is shown as empty although it has intakes: $description",
                neutral.none {
                    description!!.contains(it)
                },
            )
        }
        assertTrue(
            "the explanation must name the medication whose intakes the days hold",
            world.intakes.of(name).any { it.medicationId == id },
        )
    }

    @Then("^строки «([^»]+)» в блоке «По лекарствам» нет$")
    fun thereIsNoRowFor(medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        assertTrue(
            "the row of «$medication» must not be attributed to this month",
            period().byMedication.none { it.medicationId == id },
        )
        assertTrue(
            "the row of «$medication» is on the screen",
            breakdownRowTexts(world.medications.get(name).name).isEmpty(),
        )
    }

    @Then("^история сентября 2026 при возврате к нему остаётся прежней$")
    fun septemberStaysTheSame() {
        val before = requireNotNull(septemberBefore) { "the scenario never switched away from September" }
        HistoryScreenProbe.switchTo(world, SEPTEMBER)
        assertEquals("returning to September must show the same history", before, period(SEPTEMBER))
    }

    // --- Проверки: строка-вывод ------------------------------------------------------

    @Then("^показана строка «([^»]+)»$")
    fun theInsightLineIsShown(text: String) {
        val shown = HistoryScreenProbe.ask(world) { test -> texts(test, TAG_INSIGHT) }
        assertTrue("no «$text» among $shown", shown.any { it == text })
    }

    @Then("^строка-вывод не показывается$")
    fun theInsightLineIsHidden() {
        assertEquals(
            "the insight line must stay hidden",
            0,
            HistoryScreenProbe.ask(world) { test ->
                test.onAllNodesWithTag(TAG_INSIGHT, useUnmergedTree = true).fetchSemanticsNodes().size
            },
        )
    }

    @Then("^она констатирует наблюдаемый факт$")
    fun theInsightStatesTheFact() {
        val time = requireNotNull(MissedPatternRule().dominantMissedTime(period())) {
            "the rule must find a leader, and the line must state it"
        }
        val shown = HistoryScreenProbe.ask(world) { test -> texts(test, TAG_INSIGHT) }
        assertEquals("the line must state exactly the observed fact", listOf(insightText(time)), shown)
    }

    @Then("^не содержит совета, оценки поведения человека или побуждения к действию$")
    fun theInsightGivesNoAdvice() {
        val shown = HistoryScreenProbe.ask(world) { test -> texts(test, TAG_INSIGHT) }.joinToString(" ")
        assertTrue("there is no insight line to judge", shown.isNotEmpty())
        val offenders = ADVICE_WORDS.filter { shown.contains(it, ignoreCase = true) }
        assertEquals("the insight line must stay descriptive: $shown", emptyList<String>(), offenders)
    }

    // --- Проверки: пустой месяц -------------------------------------------------------

    @Then("^в плитках сводки вместо чисел показаны прочерки$")
    fun theTilesShowDashes() {
        val dashes = HistoryScreenProbe.ask(world) { test ->
            test.onAllNodesWithText(context.getString(R.string.history_value_dash), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size
        }
        assertEquals("every tile of an empty month must show a dash", 3, dashes)
    }

    @Then("^календарь показан пустым$")
    fun theCalendarIsEmpty() {
        HistoryScreenProbe.ask(world) { test -> test.onNodeWithTag(TAG_CALENDAR).assertIsDisplayed() }
        val descriptions = dayDescriptions()
        assertEquals(HistoryScreenProbe.selected(world).lengthOfMonth(), descriptions.size)
        val neutral = listOf(R.string.history_state_no_intakes, R.string.history_state_before_history)
            .map { context.getString(it) }
        descriptions.forEach { description ->
            assertTrue("an empty month must stay neutral: $description", neutral.any { description.contains(it) })
        }
    }

    @Then("^блок «По лекарствам» скрыт$")
    fun theBreakdownIsHidden() {
        assertEquals(
            0,
            HistoryScreenProbe.ask(world) { test ->
                test.onAllNodesWithTag(TAG_BREAKDOWN, useUnmergedTree = true).fetchSemanticsNodes().size
            },
        )
    }

    @Then("^экран не выглядит сломанным и не показывает сообщения об ошибке$")
    fun theScreenLooksIntact() {
        HistoryScreenProbe.ask(world) { test ->
            test.onNodeWithTag(TAG_HISTORY_SCREEN).assertIsDisplayed()
            test.onNodeWithTag(TAG_CALENDAR).assertIsDisplayed()
        }
        val labels = allTexts() + allDescriptions()
        val offenders = ERROR_WORDS.filter { word -> labels.any { it.contains(word, ignoreCase = true) } }
        assertEquals("an empty month must not look like a failure: $labels", emptyList<String>(), offenders)
    }

    // --- Проверки: профили -------------------------------------------------------------

    @Then("^на экране показана надпись «([^»]+)»$")
    fun theLabelIsShown(label: String) {
        val shown = HistoryScreenProbe.ask(world, chrome = chrome()) { test ->
            test.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).fetchSemanticsNodes().flatMap {
                textsOf(it)
            }
        }
        assertTrue("the label «$label» is not shown: $shown", shown.any { it.equals(label, ignoreCase = true) })
    }

    @Then("^экран несёт цветную полосу и подкрашенную шапку профиля «([^»]+)»$")
    fun theScreenCarriesTheStripeAndTintedHeader(profile: String) {
        assertEquals("the scenario speaks about the active profile", profile, world.profiles.active)
        HistoryScreenProbe.ask(world, chrome = chrome()) { test ->
            test.onNodeWithTag(TAG_PROFILE_STRIPE, useUnmergedTree = true).assertIsDisplayed()
        }
        assertNotEquals(
            "a foreign profile must tint the header (ПРФ-11)",
            roleColour(ProfileChromeState.Owner) { SklyankaTheme.colors.profileHeader },
            roleColour(chrome()) { SklyankaTheme.colors.profileHeader },
        )
    }

    @Then("^акцентный цвет действий — цвет профиля «([^»]+)»$")
    fun theAccentIsTheProfileColour(profile: String) {
        assertEquals("the scenario speaks about the active profile", profile, world.profiles.active)
        assertEquals(
            "the accent of a foreign profile must be its own colour (ПРФ-11)",
            FOREIGN_COLOUR.color,
            roleColour(chrome()) { SklyankaTheme.colors.accent },
        )
    }

    @Then("^эта отметка подписана «отметили вы»$")
    fun theMarkIsCaptioned() {
        HistoryScreenProbe.ask(world, chrome = chrome()) { test ->
            test.onNodeWithTag(TAG_MARKED_BY_YOU, useUnmergedTree = true).assertIsDisplayed()
            assertTrue(
                "the caption must read «отметили вы»",
                texts(test, TAG_MARKED_BY_YOU).contains(context.getString(UiR.string.status_marked_by_you)),
            )
        }
    }

    @Then("^подписи «отметили вы» на экране нет$")
    fun thereIsNoCaption() {
        assertEquals(
            0,
            HistoryScreenProbe.ask(world) { test ->
                test.onAllNodesWithTag(TAG_MARKED_BY_YOU, useUnmergedTree = true).fetchSemanticsNodes().size
            },
        )
    }

    @Then("^в сводке, календаре и разбивке по лекарствам учтены только приёмы профиля «([^»]+)»$")
    fun onlyThatProfilesIntakesAreCounted(profile: String) {
        val owner = world.profiles.get(profile)
        assertEquals("the scenario speaks about the active profile", profile, world.profiles.active)
        val counted = period()
        assertEquals("the period must belong to the active profile (ПР-10)", owner.id, counted.profileId)
        val ownIds = ownMedicationIds(owner.id)
        assertTrue(
            "a medication of another profile appeared in the breakdown",
            counted.byMedication.all { it.medicationId in ownIds },
        )
        val window = HistoryWindow.of(HistoryScreenProbe.selected(world))
        assertTrue(
            "an intake of another profile appeared in the slice of the period",
            world.history.slice(owner.id, window).intakes.all { it.medicationId in ownIds },
        )
    }

    @Then("^приёмы профиля «([^»]+)» не видны ни в одном блоке$")
    fun theForeignIntakesAreInvisible(profile: String) {
        val foreign = world.profiles.get(profile)
        val taken = world.intakes.ofProfile(world.profiles, profile).count { it.status == IntakeStatus.TAKEN }
        assertTrue("«$profile» must have intakes for the exclusion to mean anything", taken > 0)
        val foreignNames = world.medications.all().filter { it.profileId == foreign.id }.map { it.name }
        val labels = allTexts() + allDescriptions()
        foreignNames.forEach { name ->
            assertTrue("an intake of «$profile» is visible on the screen: $name", labels.none { it.contains(name) })
        }
        val active = world.profiles.get(world.profiles.active)
        assertTrue(
            "the period shows a medication of another profile",
            period().byMedication.all { it.medicationId in ownMedicationIds(active.id) },
        )
    }

    // --- Действия и проверки: отчёт врачу ------------------------------------------------

    @When("^(?:человек|он) нажимает «Отчёт врачу»$")
    fun thePersonPressesTheReportAction() {
        val intents = HistoryScreenProbe.act(world) { test -> test.onNodeWithTag(TAG_REPORT_ACTION).performClick() }
        assertTrue("the button must ask for the report: $intents", intents.contains(HistoryIntent.ReportRequested))
    }

    @Then("^приложение формирует отчёт о приверженности за (.+)$")
    fun theReportIsBuiltFor(monthName: String) {
        val month = RussianDates.month(monthName, world.clock.today())
        val report = requireNotNull(world.history.report) { "the report of $month was not built" }
        assertEquals("the report must cover the selected month", month, YearMonth.from(report.period.from))
        assertEquals("the report must cover the selected month", month, YearMonth.from(report.period.to))
        HistoryScreenProbe.ask(world) { test -> test.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed() }
    }

    @Then("^предлагает передать его системным способом обмена$")
    fun theSheetOffersTheShare() {
        HistoryScreenProbe.ask(world) { test ->
            test.onNodeWithTag(TAG_REPORT_SHARE).assertIsDisplayed()
            assertTrue(
                "the report must be shareable by the system",
                texts(test, TAG_REPORT_SHARE).contains(context.getString(R.string.history_report_share)),
            )
        }
    }

    @Then("^приложение предупреждает, что отчёт содержит медицинские данные в открытом виде$")
    fun theWarningIsShown() {
        HistoryScreenProbe.ask(world) { test ->
            test.onNodeWithTag(TAG_REPORT_DATA_NOTE).assertIsDisplayed()
            assertTrue(
                "the note must name the open medical data (НФТ-18)",
                texts(test, TAG_REPORT_DATA_NOTE).contains(context.getString(R.string.history_report_data_note)),
            )
        }
    }

    @Then("^в отчёте названы период и профиль$")
    fun theReportNamesThePeriodAndProfile() {
        val report = report()
        val document = shownDocument()
        assertTrue(
            "the report must name its profile",
            document.contains(context.getString(R.string.report_document_profile, report.profileName)),
        )
        assertTrue(
            "the report must name the end of its period",
            document.contains(fullDate(report.period.to)),
        )
        assertTrue(
            "the report must open the period with its first day",
            document.contains(report.period.from.dayOfMonth.toString()),
        )
        assertTrue("the period must carry its own label", document.contains(periodLabel()))
    }

    @Then("^в отчёте есть сводные числа за месяц$")
    fun theReportCarriesTheSummaryNumbers() {
        val report = report()
        val document = shownDocument()
        assertTrue(document.contains(context.getString(R.string.report_document_taken_label)))
        assertTrue(
            "the report must repeat the number of days without misses",
            document.contains(report.period.daysWithoutMisses.toString()),
        )
        assertTrue(
            "the report must repeat the number of misses",
            document.contains(report.period.missesInPeriod.toString()),
        )
    }

    @Then("^в отчёте есть разбивка по лекарствам, включая «([^»]+)» с пометкой, что курс в архиве$")
    fun theReportHoldsTheArchivedCourse(medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        val row = requireNotNull(report().medications.firstOrNull { it.medicationId == id }) {
            "the archived course must be in the report (ПР-11)"
        }
        assertTrue("the archived course must be marked in the report", row.archived)
        val document = shownDocument()
        assertTrue(document.contains(context.getString(R.string.report_document_medications_title)))
        assertTrue("the report must name «${row.name}»", document.contains(row.name))
        assertTrue(
            "the report must say the course is archived",
            document.contains(context.getString(R.string.report_document_archived)),
        )
    }

    @Then("^в отчёте перечислены даты пропусков$")
    fun theReportListsTheMissedDates() {
        val report = report()
        val rows = report.medications.filter { it.missedDates.isNotEmpty() }
        assertTrue("the scenario must leave a medication with missed dates", rows.isNotEmpty())
        val blocks = document().medicationBlocks
        rows.forEach { row ->
            val block = requireNotNull(blocks.firstOrNull { it.lines.first().text.startsWith(row.name) }) {
                "the report must have a block for «${row.name}»"
            }
            val blockText = block.lines.joinToString("\n") { it.text }
            assertTrue(
                "the report must label the misses of «${row.name}»: $blockText",
                blockText.contains(missesLabel()),
            )
            row.missedDates.forEach { date ->
                assertTrue(
                    "the report must list day ${date.dayOfMonth} of «${row.name}»: $blockText",
                    blockText.contains(date.dayOfMonth.toString()),
                )
            }
        }
    }

    /**
     * Выводов в отчёте нет по построению: ни одного поля с оценкой в составе, ни одного
     * слова-совета в документе. Обе половины проверяются здесь, а не запоминаются ревьюером.
     */
    @Then("^отчёт содержит только числа и факты$")
    fun theReportHoldsOnlyNumbersAndFacts() {
        val report = report()
        val document = shownDocument()
        assertTrue(
            "the report must carry the counted numbers",
            document.contains(report.period.daysWithoutMisses.toString()) &&
                document.contains(report.period.missesInPeriod.toString()),
        )
        val opinionFields = (DoctorReport::class.java.declaredFields + ReportMedicationRow::class.java.declaredFields)
            .map { it.name }
            .filter { field -> OPINION_WORDS.any { field.contains(it, ignoreCase = true) } }
        assertEquals("the report composition must have no place for advice", emptyList<String>(), opinionFields)
    }

    @Then("^не содержит выводов о лечении, советов и оценок$")
    fun theReportGivesNoAdvice() {
        val document = shownDocument()
        val offenders = ADVICE_WORDS.filter { document.contains(it, ignoreCase = true) }
        assertEquals("the report must stay descriptive: $document", emptyList<String>(), offenders)
    }

    // --- Обвязка ------------------------------------------------------------------------

    private fun select(monthName: String) {
        HistoryScreenProbe.select(world, RussianDates.month(monthName, world.clock.today()))
    }

    private fun period(month: YearMonth = HistoryScreenProbe.selected(world)): AdherencePeriod =
        HistoryFromWorld.period(world, month)

    private fun report(): DoctorReport = requireNotNull(world.history.report) { "the scenario never built a report" }

    private fun assertTile(tag: String, plurals: Int, count: Int) {
        val expected = context.resources.getQuantityString(plurals, count, count)
        val shown = HistoryScreenProbe.ask(world) { test ->
            test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
        }
        assertEquals("the tile must read «$expected»", expected, shown)
    }

    private fun dayDescriptions(): List<String> = HistoryScreenProbe.ask(world) { test ->
        test.onAllNodesWithTag(TAG_DAY, useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()
        }
    }

    private fun breakdownRowTexts(displayName: String): List<String> = HistoryScreenProbe.ask(world) { test ->
        test.onAllNodesWithTag(TAG_BREAKDOWN_ROW, useUnmergedTree = true).fetchSemanticsNodes()
            .map { textsOf(it) }
            .firstOrNull { displayName in it }
            .orEmpty()
    }

    private fun allTexts(): List<String> = HistoryScreenProbe.ask(world) { test ->
        textsOf(test.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun allDescriptions(): List<String> = HistoryScreenProbe.ask(world) { test ->
        descriptionsOf(test.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    /**
     * Документ отчёта собирается настоящим композером, а не читается со шторки.
     *
     * В шторке состава документа больше нет (решение дизайна по ОВ-15), а байты PDF
     * текстом не являются вовсе: приёмка говорит о документе, а не о способе его показа.
     */
    private fun document(): ReportPdfContent = ReportPdfComposer(context).compose(report(), world.clock.zone)

    private fun shownDocument(): String = document().documentText()

    /** Подпись периода из шаблона ресурса: «Период:» до первого подстановочного знака. */
    private fun periodLabel(): String =
        context.getString(R.string.report_document_period).substringBefore("%1\$s").trim()

    /** Подпись пропусков из шаблона ресурса: «Пропуски:» до первого подстановочного знака. */
    private fun missesLabel(): String =
        context.getString(R.string.report_document_missed_dates).substringBefore("%1\$s").trim()

    private fun texts(test: ComposeUiTest, tag: String): List<String> = test
        .onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { textsOf(it) }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun descriptionsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap { descriptionsOf(it) }

    private fun displayName(medication: String): String = world.medications.get(world.medicationNamed(medication)).name

    private fun ownMedicationIds(profileId: String): Set<String> =
        world.medications.all().filter { it.profileId == profileId }.map { it.id }.toSet()

    /** Оформление по активному профилю — правилом пробы, а не второй его копией (ПРФ-11). */
    private fun chrome(): ProfileChromeState = HistoryScreenProbe.chrome(world)

    private fun roleColour(state: ProfileChromeState, role: @Composable () -> Color): Color {
        var captured: Color? = null
        runComposeUiTest {
            setContent { ProfileChrome(state) { captured = role() } }
        }
        return requireNotNull(captured) { "the theme did not provide the colour role" }
    }

    private fun dayLabel(date: LocalDate): String = date.format(
        DateTimeFormatter.ofPattern(context.getString(R.string.history_date_pattern), locale),
    )

    private fun fullDate(date: LocalDate): String = date.format(
        DateTimeFormatter.ofPattern(context.getString(R.string.history_date_pattern_full), locale),
    )

    private fun stateWord(word: String): String = when (word) {
        "всё принято" -> context.getString(R.string.history_legend_all_taken)
        "частично" -> context.getString(R.string.history_legend_partial)
        "пропуск" -> context.getString(R.string.history_legend_missed)
        "без приёмов" -> context.getString(R.string.history_state_no_intakes)
        else -> error("unknown day state: $word")
    }

    /**
     * Слово, которым экран помечает сегодняшний день.
     *
     * Берётся из шаблона подписи, а не переписывается в шаг: шаблон — часть макета,
     * и переписанное слово разошлось бы с ним при первой же правке.
     */
    private fun todayMarker(): String = context.getString(R.string.cd_history_day_today)
        .replace("%1\$s", "")
        .replace("%2\$s", "")
        .trim(',', ' ')

    private fun insightText(time: LocalTime): String {
        val template = when (PartOfDay.of(time)) {
            PartOfDay.MORNING -> R.string.history_insight_morning
            PartOfDay.DAY -> R.string.history_insight_day
            PartOfDay.EVENING -> R.string.history_insight_evening
            PartOfDay.NIGHT -> R.string.history_insight_night
        }
        val formatted = time.format(
            DateTimeFormatter.ofPattern(context.getString(R.string.history_time_pattern), locale),
        )
        return context.getString(template, formatted)
    }

    /**
     * Пиксели акцентного цвета на картинке блока «По лекарствам».
     *
     * Снимок во временный каталог: эталоны эти шаги не трогают и не пишут. Цвет полосы —
     * единственный носитель «выделено», и в семантике он не живёт; поэтому вопрос
     * задаётся картинке, как это делает шаг различимости в `core-ui`.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun accentPixels(rows: List<MedicationAdherence>): Int {
        val target = File.createTempFile("history-breakdown", ".png")
        try {
            captureRoboImage(
                file = target,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
                roborazziComposeOptions = RoborazziComposeOptions {
                    size(widthDp = CAPTURE_WIDTH, heightDp = CAPTURE_HEIGHT)
                },
            ) {
                SklyankaTheme {
                    Box(modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background)) {
                        MedicationBreakdown(rows = rows, insightTime = null)
                    }
                }
            }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "the breakdown was not rendered" }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val accent = lightColors().accent.toArgb()
            return pixels.count { it == accent }
        } finally {
            target.delete()
        }
    }

    private companion object {
        val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
        val FOREIGN_COLOUR = ProfileColor.Terracotta

        const val COURSE_INTAKES = 10
        const val CAPTURE_WIDTH = 390
        const val CAPTURE_HEIGHT = 800

        /** Слова, которыми были бы названы совет, оценка или побуждение, — на любом языке. */
        val ADVICE_WORDS = listOf(
            "рекоменд",
            "совет",
            "следу",
            "обрат",
            "перенес",
            "увелич",
            "уменьш",
            "оцен",
            "плохо",
            "хорошо",
            "нужно",
            "должн",
            "риск",
            "recommend",
            "advice",
            "should",
        )

        /** Имена полей состава отчёта, в которых поместился бы вывод о лечении. */
        val OPINION_WORDS = listOf("advice", "recommend", "opinion", "risk", "assessment", "conclusion")

        /** Слова, которыми было бы названо сообщение об ошибке. */
        val ERROR_WORDS = listOf("ошибк", "не удалось", "сбой", "error", "failed")
    }
}
