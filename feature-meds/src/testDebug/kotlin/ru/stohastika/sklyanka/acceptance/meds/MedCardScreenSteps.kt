package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_BACK
import ru.stohastika.sklyanka.feature.meds.card.TAG_BLOCK_TRAILING
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BAR
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.card.TAG_LIMIT_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_SUBTITLE
import ru.stohastika.sklyanka.feature.meds.card.TAG_TIME_CHIP
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_DAY
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_LEGEND
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.feature.meds.card.TAG_TITLE as TAG_CARD_TITLE
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки карточки лекарства (SKL-10, `@screen`; заведены SKL-26).
 *
 * Состояние карточки собирает [MedCardStand] тем же путём, что `MedicationCardViewModel`,
 * а проверки задают вопросы собранной композиции. Русский текст берётся из ресурсов,
 * даты — форматтером локали стенда; ключи сценария переводятся в имена модели
 * [MedsListStand.displayName] (НФТ-19).
 *
 * Строка «Напоминать на часах» на карточке не рисуется: интеграция с Wear Engine отложена
 * (`ADR-0009`), и шаги часов проверяют отсутствие строки, а не её состояние.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedCardScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Лекарство, о котором говорит сценарий. */
    private var focused: String = ""

    // --- Открытие карточки ---------------------------------------------------------

    @Когда("^человек открывает карточку лекарства «([^»]+)»$")
    fun thePersonOpensTheCard(medication: String) {
        focused = medication
        MedCardStand.ask(world, medication) { test -> test.onNodeWithTag(TAG_CARD_TITLE).assertIsDisplayed() }
    }

    // «Человек открывает карточку лекарства «X» D» объявлено в общем артефакте
    // (`StockSteps`): тем же открытием карточки на дату пользуется SKL-12.

    @Когда("^человек открывает карточку (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheCardAt(date: String, time: String) {
        focused = focusedMedication()
        world.clock.moveTo(world.dateOf(date).atTime(LocalTime.parse(time)))
        MedCardStand.ask(world, focused) { test -> test.onNodeWithTag(TAG_CARD_TITLE).assertIsDisplayed() }
    }

    @Когда("^человек смотрит на блок «Последние 7 дней» (.+)$")
    fun thePersonLooksAtTheWeekBlock(date: String) {
        focused = focusedMedication()
        world.clock.moveToDate(world.dateOf(date))
        MedCardStand.ask(world, focused) { }
    }

    // --- Титул ---------------------------------------------------------------------

    @Тогда("^показано название «([^»]+)»$")
    fun theTitleIsShown(medication: String) {
        val shown = MedsListStand.displayName(world, medication)
        MedCardStand.ask(world, medication) { test ->
            val texts = MedsProbe.texts(test, TAG_CARD_TITLE)
            assertTrue("the title must read «$shown»: $texts", shown in texts)
        }
    }

    @Тогда("^под ним подпись «(.+)»$")
    fun theSubtitleIsShown(subtitle: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_SUBTITLE)
            assertTrue("the subtitle must read «$subtitle»: $texts", subtitle in texts)
        }
    }

    @Тогда("^в подписи титула вида таблетки нет$")
    fun theSubtitleCarriesNoTabletShape() {
        assertNull("the shape must be inapplicable to the form (ПР-9)", state().subtitle.shape)
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_SUBTITLE)
            assertTrue(
                "the subtitle must not name a tablet shape: $texts",
                texts.none { text -> SHAPES.any { it in text } },
            )
        }
    }

    @Тогда("^в подписи титула показано «(.+)»$")
    fun theSubtitleShows(subtitle: String) {
        focused = focusedMedication()
        MedCardStand.ask(world, focused) { test ->
            val texts = MedsProbe.texts(test, TAG_SUBTITLE)
            assertTrue("the subtitle must read «$subtitle»: $texts", texts.any { subtitle in it })
        }
    }

    // --- Расписание и ограничение ----------------------------------------------------

    @Тогда("^в блоке «Расписание» показано «(.+)»$")
    fun theScheduleBlockShows(text: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(text in MedsProbe.texts(test, TAG_SCHEDULE_BLOCK))
        }
    }

    @Тогда("^показаны чипы времени «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theTimeChipsAreShown(first: String, second: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val chips = MedsProbe.texts(test, TAG_TIME_CHIP)
            assertTrue("the chips must show $first and $second: $chips", first in chips && second in chips)
        }
    }

    @Тогда("^в карточке в блоке «Расписание» показаны чипы «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theCardTimeChipsAreShown(first: String, second: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val chips = MedsProbe.texts(test, TAG_TIME_CHIP)
            assertTrue("the chips must show $first and $second: $chips", first in chips && second in chips)
        }
    }

    @Тогда("^вместо блока «Расписание» показано ограничение «(.+)»$")
    fun theLimitReplacesTheSchedule(limit: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(limit in MedsProbe.texts(test, TAG_LIMIT_BLOCK))
            test.onAllNodesWithTag(TAG_SCHEDULE_BLOCK, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Курс ----------------------------------------------------------------------

    @Тогда("^в блоке «Курс» показан период «(.+)»$")
    fun theCourseBlockShowsThePeriod(period: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(period in MedsProbe.texts(test, TAG_COURSE_BLOCK))
        }
    }

    @Тогда("^в блоке «Курс» показано «(.+)»$")
    fun theCourseBlockShows(text: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(text in MedsProbe.texts(test, TAG_COURSE_BLOCK))
        }
    }

    @Тогда("^справа от заголовка показано «(.+)»$")
    fun theBlockTrailingShows(text: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(text in MedsProbe.texts(test, TAG_BLOCK_TRAILING))
        }
    }

    @Тогда("^показана полоса хода курса$")
    fun theCourseBarIsShown() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_COURSE_BAR, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^полосы хода курса нет$")
    fun thereIsNoCourseBar() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_COURSE_BAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^числа оставшихся дней нет$")
    fun thereAreNoDaysLeft() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNode(
                hasTestTag(TAG_BLOCK_TRAILING) and hasAnyAncestor(hasTestTag(TAG_COURSE_BLOCK)),
                useUnmergedTree = true,
            ).assertDoesNotExist()
        }
    }

    @Тогда("^показано, что остался один день курса$")
    fun theCourseHasItsLastDay() {
        val course = state().course
        assertTrue("the course must be bounded", course is CourseProgress.Bounded)
        assertTrue("the day must be the last one of the course", (course as CourseProgress.Bounded).isLastDay)
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(
                context.getString(R.string.card_course_last_day) in MedsProbe.texts(test, TAG_BLOCK_TRAILING),
            )
        }
    }

    @Тогда("^блока «Курс» на карточке нет$")
    fun thereIsNoCourseBlock() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_COURSE_BLOCK, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Остаток ---------------------------------------------------------------------

    @Тогда("^блока «Остаток» на карточке нет$")
    fun thereIsNoStockBlock() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_STOCK_BLOCK, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Неделя приёмов ----------------------------------------------------------------

    @Тогда("^в блоке «Последние 7 дней» показаны семь столбиков с буквами дней недели$")
    fun theWeekShowsSevenLabelledDays() {
        assertEquals(DayCell.WEEK_LENGTH, state().week.days.size)
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_ADHERENCE_DAY, useUnmergedTree = true).assertCountEquals(DayCell.WEEK_LENGTH)
            val labels = MedsProbe.texts(test, TAG_ADHERENCE_DAY)
            assertEquals("every day must carry its letter", DayCell.WEEK_LENGTH, labels.count { it.isNotBlank() })
        }
    }

    @Тогда("^показана легенда «Принято», «Пропущено», «Сегодня»$")
    fun theWeekLegendIsShown() {
        val legend = listOf(
            UiR.string.adherence_legend_taken,
            UiR.string.adherence_legend_missed,
            UiR.string.adherence_legend_today,
        ).map { context.getString(it) }
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_ADHERENCE_LEGEND)
            legend.forEach { item -> assertTrue("the legend must read «$item»: $texts", item in texts) }
        }
    }

    @Тогда("^кружок (.+?) нарисован полым, с обводкой$")
    fun thatDayIsDrawnAsToday(date: String) {
        val day = world.dateOf(date)
        val cell = state().week.days.first { it.date == day }
        assertTrue("$date must be the current day (ЭКР-6)", cell is DayCell.Today)
        // «Полый с обводкой» — решение разметки; смысл несёт описание для чтения с экрана:
        // «пятница, сегодня».
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithContentDescription(
                context.getString(R.string.cd_adherence_today, fullWeekday(day)),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    @Тогда("^принятый сегодня приём в счётчик «12 из 14 приёмов» не входит$")
    fun todaysTakenIntakeIsNotCounted() {
        val week = state().week
        assertTrue("the last cell must be today", week.days.last() is DayCell.Today)
        // Числитель — только завершившиеся сутки (ЭКР-6): сегодняшний приём в него не входит.
        assertEquals(
            "the numerator must count completed days only",
            week.days.filterIsInstance<DayCell.Completed>().sumOf { it.taken },
            week.taken,
        )
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(counterText() in MedsProbe.texts(test, TAG_BLOCK_TRAILING))
        }
    }

    @Тогда("^(.+?) показано днём с принятыми приёмами$")
    fun thatDayIsShownAsTaken(date: String) {
        val day = world.dateOf(date)
        val cell = state().week.days.first { it.date == day } as DayCell.Completed
        assertTrue("the day must be completed", cell.taken == cell.planned && cell.planned > 0)
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithContentDescription(
                context.getString(R.string.cd_adherence_completed, fullWeekday(day), cell.taken, cell.planned),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    @Тогда("^счётчик недели учитывает оба приёма (.+)$")
    fun theWeekCounterCountsBothIntakes(date: String) {
        val day = world.dateOf(date)
        val cell = state().week.days.first { it.date == day } as DayCell.Completed
        assertEquals("both intakes of the day must be taken", 2, cell.taken)
        assertTrue("the counter must include them", state().week.taken >= cell.taken)
    }

    @Тогда("^столбики за (.+) нарисованы пустыми, без цвета$")
    fun thoseDaysAreDrawnEmpty(dates: String) {
        val days = RussianDates.dates(dates, world.clock.today())
        val cells = state().week.days
        days.forEach { day ->
            val cell = cells.first { it.date == day }
            assertTrue("$day must precede the course", cell is DayCell.BeforeCourse)
        }
        MedCardStand.ask(world, focusedMedication()) { test ->
            days.forEach { day ->
                test.onNodeWithContentDescription(
                    context.getString(R.string.cd_adherence_before_course, fullWeekday(day)),
                    useUnmergedTree = true,
                ).assertExists()
            }
        }
    }

    @Тогда("^в счётчик недели эти дни не входят$")
    fun thoseDaysAreNotCounted() {
        val name = focusedMedication()
        val start = world.medications.get(world.medicationNamed(name)).startDate
        // Дни до начала курса пусты по природе: записей в них нет, и в знаменатель
        // счётчика они не попадают.
        assertTrue(
            "no intakes may exist before the course start: ${world.intakes.of(name)}",
            world.intakes.of(name).none { it.scheduledAt.toLocalDate() < start },
        )
    }

    @Тогда("^различить принятый и пропущенный день можно без восприятия цвета$")
    fun theDayStatesAreDistinguishableWithoutColour() {
        val cells = state().week.days.filterIsInstance<DayCell.Completed>()
        val taken = cells.first { it.taken == it.planned && it.planned > 0 }
        val missed = cells.first { it.taken == 0 && it.planned > 0 }
        val takenText = context.getString(
            R.string.cd_adherence_completed,
            fullWeekday(taken.date),
            taken.taken,
            taken.planned,
        )
        val missedText = context.getString(
            R.string.cd_adherence_completed,
            fullWeekday(missed.date),
            missed.taken,
            missed.planned,
        )
        assertNotEquals("the states must differ in words, not only in colour (НФТ-33)", takenText, missedText)
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithContentDescription(takenText, useUnmergedTree = true).assertExists()
            test.onNodeWithContentDescription(missedText, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^в блоке «Последние 7 дней» показаны фактические приёмы, а пропусков нет$")
    fun theOnDemandWeekShowsNoMisses() {
        val week = state().week
        // У «по потребности» плана не существует (ПР-4): знаменатель — фактические записи,
        // и дня «принято 0 из 2» быть не может.
        assertTrue(
            "an on-demand week must have no missed days: ${week.days}",
            week.days.filterIsInstance<DayCell.Completed>().none { it.planned > 0 && it.taken < it.planned },
        )
        assertEquals(
            "the denominator must equal the actual records",
            world.intakes.of(focusedMedication()).count { it.scheduledAt.toLocalDate() >= week.days.first().date },
            week.planned,
        )
    }

    // --- Часы (ADR-0009: строки на карточке нет) -----------------------------------------

    @Тогда("^строки «Напоминать на часах» на карточке нет$")
    fun thereIsNoWatchRow() {
        val medication = focusedMedication()
        // Правило ЧАС-17 в состоянии: у «по потребности» части часов нет вовсе.
        assertNull("an on-demand medication has no watch part (ЧАС-17)", state().watchMirror)
        // И на экране строки нет: интеграция с Wear Engine отложена (`ADR-0009`), строка
        // вернётся вместе с ней. Проверка адресует формулировку из сценария.
        MedCardStand.ask(world, medication) { test ->
            test.onAllNodesWithText(WATCH_ROW).assertCountEquals(0)
        }
    }

    // --- Правка ---------------------------------------------------------------------------

    @Когда("^человек нажимает «Изменить» в карточке «([^»]+)»$")
    fun thePersonPressesEdit(medication: String) {
        focused = medication
        val intents = MedCardStand.act(world, medication) { test -> test.onNodeWithTag(TAG_EDIT).performClick() }
        assertTrue("the card must open the edit form: $intents", MedCardIntent.Edit in intents)
        val card = world.medications.get(world.medicationNamed(medication))
        MedsFormStand.openEdit(world, card)
    }

    @Тогда("^открывается форма правки с названием «([^»]+)» в шапке$")
    fun theEditFormOpensWithThatName(medication: String) {
        val shown = MedsListStand.displayName(world, medication)
        val card = world.medications.get(world.medicationNamed(medication))
        val mode = MedsFormStand.openEdit(world, card).mode
        assertEquals("the form must be opened for editing", shown, (mode as FormMode.Edit).title)
        MedsFormStand.ask(world) { test ->
            assertTrue(shown in MedsProbe.texts(test, TAG_SCREEN_HEADER))
        }
    }

    // --- Архивация ------------------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «В архив» в карточке «([^»]+)»$")
    fun thePersonPressesArchive(date: String, time: String, medication: String) {
        focused = medication
        world.clock.moveTo(world.dateOf(date).atTime(LocalTime.parse(time)))
        val intents = MedCardStand.act(world, medication) { test -> test.onNodeWithTag(TAG_ARCHIVE).performClick() }
        assertTrue("the card must ask to archive the course: $intents", MedCardIntent.Archive in intents)
    }

    @Тогда("^человек возвращается в список лекарств$")
    fun thePersonReturnsToTheList() {
        assertTrue("archiving closes the card (маршрут `app`)", MedCardStand.closed)
        MedsListStand.ask(world) { test ->
            assertTrue(context.getString(R.string.meds_title) in MedsProbe.texts(test, TAG_SCREEN_HEADER))
        }
    }

    @Тогда("^«([^»]+)» в списке активных больше нет$")
    fun thatMedicationIsGoneFromTheActiveList(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_MEDICATION_CARD, useUnmergedTree = true)
                .assertCountEquals(0)
        }
        assertTrue(
            "«$medication» must leave the active list (ПР-11)",
            MedsListStand.state(world).items.none { it.medicationId == id },
        )
    }

    @Тогда("^карточка архивированного лекарства показывает их в блоке «Последние 7 дней»$")
    fun theArchivedCardShowsTheHistory() {
        val medication = focusedMedication()
        assertTrue("the card must be archived", state().isArchived)
        val week = state().week
        val completed = week.days.filterIsInstance<DayCell.Completed>().filter { it.planned > 0 }
        assertTrue("the archived week must keep the marks: ${week.days}", completed.isNotEmpty())
        assertTrue(
            "the shown days must be fully taken",
            completed.all { it.taken == it.planned },
        )
        MedCardStand.ask(world, medication) { test ->
            val descriptions = descriptionsOf(test)
            completed.forEach { cell ->
                assertTrue(
                    "the taken day ${cell.date} must be described: $descriptions",
                    context.getString(
                        R.string.cd_adherence_completed,
                        fullWeekday(cell.date),
                        cell.taken,
                        cell.planned,
                    ) in descriptions,
                )
            }
        }
    }

    // --- Переходы и профили ----------------------------------------------------------------

    @Допустим("^человек открыл карточку «([^»]+)» из списка лекарств$")
    fun thePersonOpenedTheCardFromTheList(medication: String) {
        focused = medication
        MedsListStand.ask(world) { }
        MedCardStand.ask(world, medication) { test -> test.onNodeWithTag(TAG_CARD_TITLE).assertIsDisplayed() }
    }

    @Когда("^он нажимает «Назад»$")
    fun thePersonPressesBack() {
        val intents = MedCardStand.act(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_BACK).performClick()
        }
        assertTrue("the back arrow must return to the list: $intents", MedCardIntent.Back in intents)
    }

    @Тогда("^открывается список лекарств$")
    fun theListOpens() {
        MedsListStand.ask(world) { test ->
            assertTrue(context.getString(R.string.meds_title) in MedsProbe.texts(test, TAG_SCREEN_HEADER))
        }
    }

    @Когда("^человек открывает список лекарств$")
    fun thePersonOpensTheList() {
        MedsListStand.ask(world) { }
    }

    @Тогда("^«([^»]+)» в списке нет$")
    fun thatMedicationIsNotInTheList(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        MedsListStand.ask(world) { test ->
            assertTrue(
                "the list must not show a foreign medication (ПР-10)",
                MedsListStand.state(world).items.none { it.medicationId == id },
            )
        }
    }

    @Тогда("^карточка «([^»]+)» из профиля «([^»]+)» не открывается$")
    fun thatCardDoesNotOpenInThatProfile(medication: String, profile: String) {
        assertEquals("the scenario must be in that profile", profile, world.profiles.active)
        assertNull(
            "a medication of another profile must not open (ПР-10)",
            MedCardStand.stateOrNull(world, medication),
        )
    }

    // --- Обвязка ---------------------------------------------------------------------------

    /** Лекарство сценария: названное шагом либо последнее заведённое Предысторией. */
    private fun focusedMedication(): String = focused.takeIf { it.isNotEmpty() } ?: world.medications.last

    private fun state(): MedCardUiState = MedCardStand.state(world, focusedMedication())

    private fun counterText(): String = context.resources.getQuantityString(
        R.plurals.card_week_counter,
        state().week.planned,
        state().week.taken,
        state().week.planned,
    )

    /** Полное название дня недели — тем же шаблоном, каким его собирает экран. */
    private fun fullWeekday(date: LocalDate): String = date.format(
        DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern_weekday_full),
            context.resources.configuration.locales[0],
        ),
    )

    private fun descriptionsOf(test: ComposeUiTest): List<String> =
        test.onAllNodesWithTag(TAG_ADHERENCE_DAY, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { descriptions(it) }

    private fun descriptions(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap { descriptions(it) }

    private companion object {
        val SHAPES = listOf("Круглая", "Овальная", "Продолговатая", "Треугольная")

        /** Формулировка строки часов из сценария; на карточке её нет (`ADR-0009`). */
        const val WATCH_ROW = "Напоминать на часах"
    }
}
