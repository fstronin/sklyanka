package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_END_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_INDEFINITE
import ru.stohastika.sklyanka.feature.meds.form.TAG_PERIOD_PRESETS
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_THRESHOLD_OPTIONS

/**
 * Экранные шаги приёмки порога покупки (SKL-74) и пресетов срока (SKL-75), группа `@screen`.
 *
 * Состояние формы собирает [MedsFormStand] из доменного черновика, нажатия уходят
 * намерениями: проверяется то, что человек видит, а не копия разметки. Состав ряда
 * порогов и пресетов — доменные списки (`StockThresholds.Options`, `CourseEndPreset.Options`),
 * и шаг сверяет с ними, а не с перечислением по месту.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsPeriodAndStockScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- SKL-74: порог напоминания о покупке ---------------------------------------------------

    @Допустим("^переключатель «Напомнить о покупке» включён$")
    fun theStockReminderIsOn() {
        MedsFormStand.open(world)
        if (!MedsFormStand.open(world).applicability.stockThresholdEnabled) {
            val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).performClick() }
            assertTrue(
                "the stock reminder must turn on: $intents",
                intents.any { it is MedFormIntent.StockReminderToggled && it.enabled },
            )
        }
        assertTrue("the reminder must be on", MedsFormStand.open(world).applicability.stockThresholdEnabled)
    }

    @Тогда("^человеку доступны пороги (.+) дней$")
    fun theThresholdsAreAvailable(values: String) {
        val expected = THRESHOLDS.split(values).map { it.trim().toInt() }
        assertEquals("the offered thresholds are the domain set", StockThresholds.Options, expected)
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_STOCK_THRESHOLD_OPTIONS).assertIsDisplayed()
            expected.forEach { days ->
                test.onNode(
                    hasText(days.toString()) and hasAnyAncestor(hasTestTag(TAG_STOCK_THRESHOLD_OPTIONS)),
                ).assertIsDisplayed()
            }
        }
    }

    @И("^отмечен порог (\\d+) дней$")
    fun thatThresholdIsSelected(days: Int) {
        assertEquals(days, MedsFormStand.open(world).draft.stockThresholdDays)
        MedsFormStand.ask(world) { test ->
            test.onNode(
                hasText(days.toString()) and hasAnyAncestor(hasTestTag(TAG_STOCK_THRESHOLD_OPTIONS)),
            ).assertIsSelected()
        }
    }

    // --- SKL-75: пресеты срока окончания -------------------------------------------------------

    @Допустим("^в блоке «Период приёма» выключено «Принимать бессрочно»$")
    fun theIndefiniteSwitchIsOff() {
        MedsFormStand.open(world)
        if (MedsFormStand.open(world).draft.endDate == null) {
            val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_INDEFINITE).performClick() }
            assertTrue(
                "the indefinite switch must turn off: $intents",
                intents.any { it == MedFormIntent.IndefiniteToggled(false) },
            )
        }
        assertNotNull("the course must hold an end date", MedsFormStand.open(world).draft.endDate)
    }

    @Тогда("^под строкой «Окончание» показаны пресеты (.+)$")
    fun thePresetsStandUnderTheEndDate(values: String) {
        val expected = QUOTED.findAll(values).map { it.groupValues[1] }.toList()
        assertTrue("the scenario must name the presets", expected.isNotEmpty())
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_PERIOD_PRESETS).assertIsDisplayed()
            expected.forEach { label ->
                test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS))).assertIsDisplayed()
            }
        }
    }

    @Тогда("^в блоке «Период приёма» есть переключатель «Принимать бессрочно»$")
    fun theIndefiniteSwitchIsThere() {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_INDEFINITE).assertIsDisplayed()
            test.onNodeWithText(context.getString(R.string.form_period_indefinite)).assertIsDisplayed()
        }
    }

    @Допустим("^в блоке «Период приёма» включено «Принимать бессрочно»$")
    fun theIndefiniteSwitchIsTurnedOn() {
        MedsFormStand.open(world)
        if (MedsFormStand.open(world).draft.endDate != null) {
            val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_INDEFINITE).performClick() }
            assertTrue(
                "the indefinite switch must turn on: $intents",
                intents.any { it == MedFormIntent.IndefiniteToggled(true) },
            )
        }
        assertNull("the course must be open-ended", MedsFormStand.open(world).draft.endDate)
    }

    @Тогда("^пресеты показаны приглушёнными и не выбираются$")
    fun thePresetsAreDisabled() {
        val labels = CourseEndPreset.Options.map { context.getString(presetLabel(it)) } +
            context.getString(R.string.period_preset_other)
        MedsFormStand.ask(world) { test ->
            labels.forEach { label ->
                test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_PERIOD_PRESETS)))
                    .assertHasNoClickAction()
            }
        }
    }

    @И("^строкой «Окончание» показано «(.+)»$")
    fun theEndDateRowShows(value: String) {
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_END_DATE).fetchSemanticsNode()
            assertTrue(
                "the end date must read «$value»: ${MedsProbe.textsOf(node)}",
                value in MedsProbe.textsOf(node),
            )
        }
    }

    // --- Обвязка --------------------------------------------------------------------------------

    /** Подпись пресета — та же четвёрка строк, что у `CourseEndPreset.label()` (§5.2 SKL-75). */
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
        /** «3, 5, 7, 10 и 14» — перечисление порогов через запятую и «и». */
        val THRESHOLDS = Regex("\\s*(?:,|и)\\s*")

        /** Подписи пресетов в кавычках: «1 неделя», «Другая дата». */
        val QUOTED = Regex("«([^»]+)»")
    }
}
