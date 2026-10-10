package ru.stohastika.sklyanka.acceptance.meds

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.DateTarget
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.form.TAG_INDEFINITE
import ru.stohastika.sklyanka.feature.meds.form.TAG_PERIOD_PRESETS
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

/**
 * Экранные шаги приёмки пресетов срока окончания курса (SKL-75, `@screen`).
 *
 * Состояние формы собирает [MedsFormStand] из доменного черновика, нажатия уходят
 * намерениями. Состав пресетов — доменный список [CourseEndPreset.Options]; подписи
 * берутся из ресурсов, а не из перечисления по месту. «Другая дата» открывает тот же
 * диалог SKL-69, что и строка «Окончание»: шаг собирает экран с диалогом и проверяет
 * заголовок, не подменяя контракт дизайн-системы.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsPresetScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Диалог «Другая дата» показан последним нажатием. */
    private var dialogTitleSeen: Boolean = false

    // --- Выбор пресета ------------------------------------------------------------------

    @Когда("^человек выбирает пресет «([^»]+)»$")
    fun thePersonChoosesPreset(label: String) {
        val preset = presetOf(label)
        val intents = MedsFormStand.act(world) { test ->
            test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS))).performClick()
        }
        assertTrue(
            "the preset «$label» must set the end date: $intents",
            intents.any { it is MedFormIntent.CourseEndPresetChosen && it.preset == preset },
        )
    }

    @Тогда("^выбранный пресет отмечен$")
    fun theChosenPresetIsMarked() {
        val state = MedsFormStand.open(world)
        val end = requireNotNull(state.draft.endDate) { "a preset must set the end date" }
        val preset =
            requireNotNull(CourseEndPreset.Options.firstOrNull { it.endDateFrom(state.draft.startDate) == end }) {
                "the end date $end must match one of the presets"
            }
        MedsFormStand.ask(world) { test ->
            test.onNode(
                hasText(context.getString(presetLabel(preset))) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS)),
            )
                .assertIsSelected()
        }
    }

    @Тогда("^отмечен пресет «([^»]+)»$")
    fun thatPresetIsMarked(label: String) {
        MedsFormStand.ask(world) { test ->
            test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS))).assertIsSelected()
        }
    }

    @Тогда("^ни один пресет не отмечен как выбранный$")
    fun noPresetIsMarked() {
        assertNoPresetIsSelected()
    }

    @Тогда("^ни один пресет не отмечен$")
    fun noPresetIsMarkedAtAll() {
        assertNoPresetIsSelected()
    }

    // --- Дата начала и окончания ----------------------------------------------------------

    @Допустим("^человек заводит лекарство с датой начала (.+)$")
    fun thePersonStartsTheCourseOn(date: String) {
        MedsFormStand.open(world)
        val draft = MedsFormStand.open(world).draft.copy(startDate = world.dateOf(date))
        MedsFormStand.showDraft(world, draft)
    }

    @Когда("^человек выключает «Принимать бессрочно» и выбирает пресет «([^»]+)»$")
    fun thePersonTurnsTheIndefiniteOffAndChoosesPreset(label: String) {
        val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_INDEFINITE).performClick() }
        assertTrue(
            "the indefinite switch must turn off: $intents",
            intents.any { it == MedFormIntent.IndefiniteToggled(false) },
        )
        thePersonChoosesPreset(label)
    }

    @Тогда("^«Принимать бессрочно» выключено$")
    fun theIndefiniteIsOff() {
        assertNotNull("the course must hold an end date", MedsFormStand.open(world).draft.endDate)
    }

    @Допустим("^человек выбрал пресет «([^»]+)» — окончание (.+)$")
    fun thePersonChoseThatPresetEndingOn(label: String, end: String) {
        val preset = presetOf(label)
        MedsFormStand.open(world)
        val expected = preset.endDateFrom(MedsFormStand.open(world).draft.startDate)
        assertEquals("the scenario names the preset's end date", world.dateOf(end), expected)
        MedsFormStand.showDraft(world, MedsFormStand.open(world).draft.copy(endDate = expected))
    }

    @Когда("^человек меняет дату начала на (.+)$")
    fun thePersonChangesTheStartDate(date: String) {
        val draft = MedsFormStand.open(world).draft.copy(startDate = world.dateOf(date))
        MedsFormStand.showDraft(world, draft)
    }

    @Тогда("^строкой «Окончание» показано ([^«].+)$")
    fun theEndDateRowShowsTheDate(date: String) {
        assertEndDateRowShows(date)
    }

    @Тогда("^строкой «Окончание» по-прежнему показано ([^«].+)$")
    fun theEndDateRowStillShowsTheDate(date: String) {
        assertEndDateRowShows(date)
    }

    private fun assertEndDateRowShows(date: String) {
        val expected = world.dateOf(date)
        val shown = formatted(expected)
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(ru.stohastika.sklyanka.feature.meds.form.TAG_END_DATE).fetchSemanticsNode()
            val texts = MedsProbe.textsOf(node)
            assertTrue("the end date must read «$shown»: $texts", shown in texts)
        }
        assertEquals("the draft must hold the shown date", expected, MedsFormStand.open(world).draft.endDate)
    }

    // --- «Другая дата» --------------------------------------------------------------------

    @Когда("^человек нажимает «Другая дата»$")
    fun thePersonPressesTheOtherDate() {
        dialogTitleSeen = false
        val state = MedsFormStand.open(world)
        val dialogTitle = context.getString(R.string.form_dialog_end_date)
        runComposeUiTest {
            setContent {
                var dialog by remember { mutableStateOf(false) }
                SklyankaTheme {
                    MedicationFormScreen(
                        state = state,
                        onIntent = {},
                        onPickDate = { target -> if (target == DateTarget.END) dialog = true },
                        onPickTime = { _, _ -> },
                    )
                    if (dialog) {
                        DateTimePickerDialog(
                            title = dialogTitle,
                            initial = state.draft.startDate,
                            onDismiss = { dialog = false },
                            onConfirm = { dialog = false },
                        )
                    }
                }
            }
            onNode(
                hasText(context.getString(R.string.period_preset_other)) and
                    hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS)),
            ).performClick()
            waitForIdle()
            dialogTitleSeen = onAllNodesWithText(dialogTitle).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Тогда("^открывается диалог с заголовком «(.+)»$")
    fun theDialogWithThatTitleOpens(title: String) {
        assertEquals(context.getString(R.string.form_dialog_end_date), title)
        assertTrue("the date dialog must open on «Другая дата»", dialogTitleSeen)
    }

    @Допустим("^человек открыл «Другую дату» и выбрал (.+)$")
    fun thePersonPickedTheOtherDate(date: String) {
        val draft = MedsFormStand.open(world).draft.copy(endDate = world.dateOf(date))
        MedsFormStand.showDraft(world, draft)
    }

    // --- Правка существующего курса ---------------------------------------------------------

    @Допустим("^есть лекарство «([^»]+)» с началом (.+?) и окончанием (.+)$")
    fun thereIsMedicationWithThatPeriod(medication: String, start: String, end: String) {
        if (!world.medications.has(medication)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(MORNING),
            )
            world.medications.update(medication) { it.copy(name = medication) }
        }
        world.medications.update(medication) {
            it.copy(startDate = world.dateOf(start), endDate = world.dateOf(end))
        }
        world.medications.focusOn(medication)
    }

    // --- Обвязка --------------------------------------------------------------------------

    private fun assertNoPresetIsSelected() {
        val labels = CourseEndPreset.Options.map { context.getString(presetLabel(it)) } +
            context.getString(R.string.period_preset_other)
        MedsFormStand.ask(world) { test ->
            labels.forEach { label ->
                test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS)))
                    .assertIsNotSelected()
            }
        }
    }

    private fun presetOf(label: String): CourseEndPreset = requireNotNull(
        CourseEndPreset.Options.firstOrNull { context.getString(presetLabel(it)) == label },
    ) { "unknown preset: $label" }

    /** Дата так, как её покажет строка «Окончание»: тот же шаблон и локаль стенда. */
    private fun formatted(date: java.time.LocalDate): String {
        val locale = context.resources.configuration.locales[0]
        val pattern = context.getString(R.string.date_pattern_full)
        return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
    }

    private fun presetLabel(preset: CourseEndPreset): Int = when (preset) {
        CourseEndPreset.WEEK_1 -> R.string.period_preset_week_1
        CourseEndPreset.WEEKS_2 -> R.string.period_preset_weeks_2
        CourseEndPreset.MONTH_1 -> R.string.period_preset_month_1
        CourseEndPreset.MONTHS_2 -> R.string.period_preset_months_2
        CourseEndPreset.MONTHS_3 -> R.string.period_preset_months_3
        CourseEndPreset.MONTHS_6 -> R.string.period_preset_months_6
        CourseEndPreset.YEAR_1 -> R.string.period_preset_year_1
    }

    private companion object {
        val MORNING: LocalTime = LocalTime.of(8, 0)
    }
}
