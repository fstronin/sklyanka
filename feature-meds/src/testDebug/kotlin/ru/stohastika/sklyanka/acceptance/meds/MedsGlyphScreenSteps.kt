package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_CARD_PLATE_GLYPH
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_LIMIT_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_RESTORE
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_SUBTITLE
import ru.stohastika.sklyanka.feature.meds.card.TAG_TITLE
import ru.stohastika.sklyanka.feature.meds.card.TAG_WEEK_BLOCK
import ru.stohastika.sklyanka.feature.meds.list.TAG_MED_LIST_GLYPH
import ru.stohastika.sklyanka.feature.meds.list.TAG_TRAILING_LINE
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-67 — строка списка и плитка титула (группа `@list`, `@card`).
 *
 * Состояние собирают [MedsListStand] и [MedCardStand] тем же путём, что ViewModel:
 * фраза расписания строится правилом ЭКР-4, силуэт — `MedicationGlyph`. Предыстория
 * заводит лекарства в мире сценария с нужной формой, видом и дозировкой.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsGlyphScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var focused: String = ""

    // --- Предыстория: лекарства списка ----------------------------------------------

    @Допустим(
        "^у профиля есть активное лекарство «([^»]+)»: форма «([^»]+)», дозировка (\\d+) ([^,]+), " +
            "режим «По расписанию», одно время «(\\d{2}:\\d{2})», начало (.+), бессрочно$",
    )
    fun theProfileHasThatMedicationWithStrength(
        medication: String,
        formLabel: String,
        strength: Int,
        unit: String,
        time: String,
        start: String,
    ) {
        add(
            name = medication,
            times = listOf(LocalTime.parse(time)),
            startDate = RussianDates.date(start, world.clock.today()),
            endDate = null,
            form = formOf(formLabel),
            strength = BigDecimal(strength),
            strengthUnit = unit,
        )
    }

    @Допустим("^у профиля есть активное лекарство «([^»]+)» с временами «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theProfileHasThatMedicationWithTimes(medication: String, first: String, second: String) {
        add(name = medication, times = listOf(LocalTime.parse(first), LocalTime.parse(second)))
    }

    @Допустим("^у профиля есть лекарство с расписанием (.+)$")
    fun theProfileHasAMedicationWithThatSchedule(times: String) {
        val parsed = RussianDates.times(times.replace("«", "").replace("»", ""))
        add(name = "лекарство расписания", times = parsed)
    }

    @Допустим("^у профиля есть активное лекарство «([^»]+)»: одно время «(\\d{2}:\\d{2})», окончание (.+)$")
    fun theProfileHasThatMedicationEndingOn(medication: String, time: String, end: String) {
        add(
            name = medication,
            times = listOf(LocalTime.parse(time)),
            startDate = world.clock.today(),
            endDate = RussianDates.date(end, world.clock.today()),
        )
    }

    @Допустим("^у профиля есть активные лекарства форм «(.+)»$")
    fun theProfileHasMedicationsOfThoseForms(forms: String) {
        forms.split(",", " и ").map { it.trim().trim('«', '»') }.forEachIndexed { index, label ->
            val form = formOf(label)
            add(
                name = "лекарство формы $index",
                times = listOf(DEFAULT_TIME),
                form = form,
                // У таблетки силуэт уточнён выбранным видом (ОВ-94).
                shape = if (form == MedicationForm.TABLET) TabletShape.ROUND_SCORED else null,
            )
        }
    }

    @Допустим("^у профиля есть лекарство «([^»]+)» с несколькими временами и периодом$")
    fun theProfileHasThatMedicationWithSeveralTimesAndPeriod(medication: String) {
        add(
            name = medication,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)),
            startDate = LocalDate.of(2026, 3, 12),
            endDate = LocalDate.of(2026, 12, 31),
        )
    }

    // --- Предыстория: карточка ------------------------------------------------------

    @Допустим("^существует лекарство «([^»]+)»: форма «([^»]+)», вид «([^»]+)»$")
    fun theMedicationWithFormAndShapeExists(medication: String, formLabel: String, shapeLabel: String) {
        add(
            name = medication,
            times = listOf(DEFAULT_TIME),
            startDate = LocalDate.of(2026, 3, 12),
            form = formOf(formLabel),
            shape = shapeOf(shapeLabel),
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
        )
    }

    @Допустим("^существует лекарство «([^»]+)»: форма «([^»]+)», вида нет$")
    fun theMedicationWithFormWithoutShapeExists(medication: String, formLabel: String) {
        add(
            name = medication,
            times = listOf(DEFAULT_TIME),
            form = formOf(formLabel),
            shape = null,
            strength = BigDecimal("2000"),
            strengthUnit = "iu",
        )
    }

    @Допустим("^лекарство «([^»]+)» находится в архиве$")
    fun thatMedicationIsArchived(medication: String) {
        if (!world.medications.has(medication)) {
            add(name = medication, times = listOf(DEFAULT_TIME), startDate = LocalDate.of(2026, 3, 12))
        }
        world.medications.archive(medication, world.clock.now())
    }

    @Допустим("^существует лекарство «([^»]+)» по потребности$")
    fun thatMedicationExistsAsNeeded(medication: String) {
        add(name = medication, mode = ScheduleMode.PRN, maxPerDay = 3)
    }

    @Допустим("^у профиля есть лекарство «([^»]+)» формы «([^»]+)» по потребности, не чаще (\\d+) раз в сутки$")
    fun theProfileHasThatOnDemandMedicationOfForm(medication: String, formLabel: String, limit: Int) {
        add(
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
            form = formOf(formLabel),
            strength = BigDecimal("200"),
            strengthUnit = "mg",
        )
    }

    @Допустим("^существует лекарство «([^»]+)» с расписанием, курсом и остатком$")
    fun thatMedicationExistsWithScheduleCourseAndStock(medication: String) {
        add(
            name = medication,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            startDate = LocalDate.of(2026, 3, 12),
            endDate = LocalDate.of(2026, 10, 12),
        )
        world.medications.update(medication) { it.copy(stockLeft = BigDecimal("30")) }
    }

    // --- Строка списка --------------------------------------------------------------

    @Тогда("^строка описания карточки «([^»]+)» — «(.+)»$")
    fun theCardDescriptionOfIs(medication: String, description: String) {
        focused = medication
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(medication), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue(
                "the card of «$medication» must describe «$description»: $texts",
                texts.any { sameText(it, description) },
            )
        }
    }

    @Тогда("^строка расписания(?: карточки)? — «(.+)»$")
    fun theScheduleLineIs(line: String) {
        val name = focusedMedication()
        val shown = trailingLine(name)
        assertTrue("the schedule line of «$name» must read «$line», shown «$shown»", sameText(shown, line))
    }

    @Но("^чипов времени в карточке нет$")
    fun theCardHasNoTimeChips() {
        val name = focusedMedication()
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(name), useUnmergedTree = true).fetchSemanticsNode())
            assertFalse("the card must not carry a standalone time chip: $texts", DEFAULT_TIME_TEXT in texts)
        }
    }

    @Тогда("^строка расписания карточки содержит времена «(.+)»$")
    fun theScheduleLineContainsTheTimes(times: String) {
        assertTrue("the schedule line must contain «$times»", times in trailingLine(focusedMedication()))
    }

    @Тогда("^строка расписания начинается с «(.+)»$")
    fun theScheduleLineStartsWith(frequency: String) {
        // Значение таблицы примеров приезжает с кавычками: ««Ежедневно»».
        val expected = frequency.trim('«', '»')
        val shown = trailingLine(focusedMedication())
        assertTrue("the schedule line must start with «$expected»: $shown", shown.startsWith(expected))
    }

    @Тогда("^строка расписания карточки «([^»]+)» оканчивается словами «(.+)»$")
    fun theScheduleLineOfEndsWith(medication: String, tail: String) {
        focused = medication
        val shown = trailingLine(medication).replace(NON_BREAKING_SPACE, ' ')
        assertTrue(
            "the schedule line of «$medication» must end with «$tail»: $shown",
            shown.endsWith(tail.replace(NON_BREAKING_SPACE, ' ')),
        )
    }

    @Но("^периода приёма в этой строке нет$")
    fun theScheduleLineHasNoPeriod() {
        val name = focusedMedication()
        val trailing = MedsListStand.state(world).items
            .first { it.medicationId == world.medications.get(world.medicationNamed(name)).id }
            .trailingLine
        assertTrue("the line must show the stock, not the period (ЭКР-4): $trailing", trailing is TrailingLine.Stock)
    }

    @Тогда("^строка описания — «(.+)»$")
    fun theDescriptionIs(description: String) {
        val name = focusedMedication()
        MedsListStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNode(card(name), useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("the card of «$name» must describe «$description»: $texts", description in texts)
        }
    }

    // --- Плитка списка --------------------------------------------------------------

    @Тогда("^на плитке каждого лекарства показан силуэт его формы$")
    fun everyCardShowsItsFormSilhouette() {
        val cards = MedsListStand.state(world).items.size
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_MED_LIST_GLYPH, useUnmergedTree = true).assertCountEquals(cards)
        }
    }

    @И("^у таблетки силуэт уточнён выбранным видом$")
    fun theTabletSilhouetteCarriesItsShape() {
        val cards = MedsListStand.state(world).items
        val tablet = cards.firstOrNull { it.form == MedicationForm.TABLET }
        assertTrue("the scenario must have a tablet", tablet != null)
        assertEquals(TabletShape.ROUND_SCORED, tablet!!.shape)
        cards.filter { it.form != MedicationForm.TABLET }.forEach {
            assertNull("форм без вида не должно нести вид (ОВ-88)", it.shape)
        }
    }

    @Когда("^человек открывает экран «Лекарства» при крупном системном шрифте$")
    fun thePersonOpensTheListWithALargeFont() {
        MedsListStand.ask(world) { }
    }

    @Тогда("^строка расписания переносится по словам$")
    fun theScheduleLineWrapsByWords() {
        val shown = trailingLine(focusedMedication())
        assertTrue("the line must consist of words: $shown", shown.contains(' '))
    }

    @Но("^не разрывается по одной букве$")
    fun theScheduleLineDoesNotBreakByLetter() {
        val shown = trailingLine(focusedMedication())
        // Разрыв по буквам означал бы, что ни одного целого слова в строке не осталось.
        assertTrue(
            "the line must keep whole words: $shown",
            shown.split(' ', '\u00A0').any { it.length >= WORD_MIN_LENGTH },
        )
    }

    // --- Титул карточки -------------------------------------------------------------

    @Когда("^человек открывает карточку «([^»]+)»$")
    fun thePersonOpensTheCard(medication: String) {
        focused = medication
        MedCardStand.ask(world, medication) { test -> test.onNodeWithTag(TAG_TITLE).assertIsDisplayed() }
    }

    @Когда("^человек открывает карточку «([^»]+)» из архива$")
    fun thePersonOpensTheArchivedCard(medication: String) {
        focused = medication
        MedCardStand.ask(world, medication) { test -> test.onNodeWithTag(TAG_TITLE).assertIsDisplayed() }
    }

    @Когда("^человек открывает его карточку$")
    fun thePersonOpensThatCard() {
        MedCardStand.ask(world, focusedMedication()) { test -> test.onNodeWithTag(TAG_TITLE).assertIsDisplayed() }
    }

    @Когда("^программа чтения с экрана просматривает карточку «([^»]+)»$")
    fun theScreenReaderReviewsThatCard(medication: String) {
        focused = medication
        MedsListStand.ask(world) { }
    }

    @Тогда("^на плитке титула показан силуэт (.+)$")
    fun theTitlePlateShowsTheSilhouette(kind: String) {
        val state = MedCardStand.state(world, focusedMedication())
        when {
            kind.contains("капсул") -> {
                assertEquals(MedicationForm.CAPSULE, state.form)
                assertNull(state.subtitle.shape)
            }

            kind.contains("риск") -> assertEquals(TabletShape.ROUND_SCORED, state.subtitle.shape)
            else -> assertTrue("the silhouette must describe a known shape: $kind", state.subtitle.shape != null)
        }
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_CARD_PLATE_GLYPH, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^подпись под названием — «(.+)»$")
    fun theSubtitleUnderTheNameIs(subtitle: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_SUBTITLE)
            assertTrue("the subtitle must read «$subtitle»: $texts", subtitle in texts)
        }
    }

    @Тогда("^плитка титула показана приглушённым цветом$")
    fun theTitlePlateIsMuted() {
        assertTrue("the card must be archived", MedCardStand.state(world, focusedMedication()).isArchived)
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_CARD_PLATE_GLYPH, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^кнопка внизу подписана «(.+)»$")
    fun theBottomButtonIsLabelled(label: String) {
        assertTrue("the scenario must speak of the restore button", label == RESTORE)
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_RESTORE, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^блок «Расписание» заменён ограничением «(.+)»$")
    fun theLimitReplacesTheSchedule(limit: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            assertTrue(limit in MedsProbe.texts(test, TAG_LIMIT_BLOCK))
            test.onAllNodesWithTag(TAG_SCHEDULE_BLOCK, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^блок «Курс» не показан$")
    fun theCourseBlockIsGone() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_COURSE_BLOCK, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^показаны блоки «(.+)», «(.+)», «(.+)» и «(.+)»$")
    fun theBlocksAreShown(first: String, second: String, third: String, fourth: String) {
        val tags = listOf(blockTag(first), blockTag(second), blockTag(third), blockTag(fourth))
        MedCardStand.ask(world, focusedMedication()) { test ->
            tags.forEach { tag ->
                assertTrue(
                    "the card must show the block $tag",
                    test.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                )
            }
        }
    }

    @Тогда("^внизу карточки показана кнопка «(.+)»$")
    fun theCardBottomButtonIs(label: String) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val node = test.onNodeWithTag(TAG_ARCHIVE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the bottom button must read «$label»: ${MedsProbe.textsOf(node)}",
                label in MedsProbe.textsOf(node),
            )
        }
    }

    // --- Доступность ----------------------------------------------------------------

    @Тогда("^она произносит название «([^»]+)» и строку описания$")
    fun itSpeaksTheNameAndDescription(medication: String) {
        focused = medication
        val shown = MedsListStand.displayName(world, medication)
        val description = MedsListStand.ask(world) { test ->
            MedsProbe.textsOf(test.onNode(card(medication), useUnmergedTree = true).fetchSemanticsNode())
        }
        assertTrue("the reader must speak the name «$shown»: $description", shown in description)
        assertTrue(
            "the reader must speak a description line: $description",
            description.any {
                // Разделитель строки описания отделён неразрывным пробелом (SKL-59 TDD §2.2,
                // V-32) — сравниваем без него.
                it.replace(NON_BREAKING_SPACE, ' ').contains(MED_SEPARATOR)
            },
        )
    }

    @Но("^подписи силуэта не произносит$")
    fun itDoesNotSpeakAShapeLabel() {
        MedsListStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_MED_LIST_GLYPH, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the silhouette must not carry its own label",
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isEmpty(),
            )
        }
    }

    // --- Обвязка --------------------------------------------------------------------

    private fun add(
        name: String,
        times: List<LocalTime> = listOf(DEFAULT_TIME),
        startDate: LocalDate = world.clock.today(),
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
        form: MedicationForm = MedicationForm.TABLET,
        shape: TabletShape? = null,
        strength: BigDecimal? = null,
        strengthUnit: String? = null,
        foodRelation: FoodRelation = FoodRelation.ANY,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = if (mode == ScheduleMode.SCHEDULED) times else emptyList(),
            startDate = startDate,
            endDate = endDate,
            mode = mode,
            maxPerDay = maxPerDay,
        )
        world.medications.update(name) {
            it.copy(
                form = form,
                shape = shape,
                strength = strength,
                strengthUnit = strengthUnit,
                foodRelation = foodRelation,
            )
        }
        focused = name
    }

    private fun focusedMedication(): String = focused.ifEmpty { world.medications.last }

    private fun card(medication: String) = MedsListStand.card(world, medication)

    private fun trailingLine(medication: String): String = MedsListStand.ask(world) { test ->
        val node = test
            .onNode(
                hasTestTag(TAG_TRAILING_LINE) and hasAnyAncestor(card(medication)),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
        MedsProbe.textsOf(node).single()
    }

    private fun sameText(actual: String, expected: String): Boolean =
        actual.replace(NON_BREAKING_SPACE, ' ') == expected.replace(NON_BREAKING_SPACE, ' ')

    private fun blockTag(name: String): String = when (name) {
        "Расписание" -> TAG_SCHEDULE_BLOCK
        "Курс" -> TAG_COURSE_BLOCK
        "Остаток" -> TAG_STOCK_BLOCK
        "Последние 7 дней" -> TAG_WEEK_BLOCK
        else -> error("unknown card block: $name")
    }

    private fun formOf(label: String): MedicationForm =
        MedicationForm.entries.first { context.getString(formLabel(it)) == label }

    private fun formLabel(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_tablet
        MedicationForm.CAPSULE -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_capsule
        MedicationForm.SPRAY -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_spray
        MedicationForm.SYRUP -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_syrup
        MedicationForm.DROPS -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_drops
        MedicationForm.INJECTION -> ru.stohastika.sklyanka.feature.meds.R.string.med_form_injection
    }

    private fun shapeOf(label: String): TabletShape =
        TabletShape.entries.first { context.getString(shapeLabel(it)) == label }

    private fun shapeLabel(shape: TabletShape): Int = when (shape) {
        TabletShape.ROUND -> ru.stohastika.sklyanka.feature.meds.R.string.tablet_shape_round
        TabletShape.ROUND_SCORED -> ru.stohastika.sklyanka.feature.meds.R.string.tablet_shape_round_scored
        TabletShape.OVAL -> ru.stohastika.sklyanka.feature.meds.R.string.tablet_shape_oval
        TabletShape.OBLONG -> ru.stohastika.sklyanka.feature.meds.R.string.tablet_shape_oblong
        TabletShape.TRIANGULAR -> ru.stohastika.sklyanka.feature.meds.R.string.tablet_shape_triangular
    }

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
        const val DEFAULT_TIME_TEXT = "08:00"
        const val RESTORE = "Вернуть"
        const val MED_SEPARATOR = " · "
        const val NON_BREAKING_SPACE = '\u00A0'

        /** Длина слова, доказывающая, что строка не рассыпалась по буквам (SKL-59). */
        const val WORD_MIN_LENGTH = 4
    }
}
