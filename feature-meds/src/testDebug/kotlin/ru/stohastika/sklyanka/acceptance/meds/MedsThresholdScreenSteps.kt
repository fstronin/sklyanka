package ru.stohastika.sklyanka.acceptance.meds

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.domain.stock.StockForecast
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_THRESHOLD_OPTIONS
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Экранные шаги приёмки порога напоминания о покупке (SKL-74, `@screen`).
 *
 * Состояние формы собирает [MedsFormStand] из доменного черновика, нажатия уходят
 * намерениями: проверяется то, что человек видит. Состав ряда порогов — доменный список
 * [StockThresholds.Options], и шаг сверяет с ним, а не с перечислением по месту.
 * «Порог сохранён» проверяется настоящей выгрузкой и загрузкой файла с настоящим
 * кодеком `JsonTransferCodec` из `core-data`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsThresholdScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // --- Переключатель и порог ---------------------------------------------------------

    @Допустим("^переключатель «Напомнить о покупке» выключен$")
    fun theStockReminderIsOff() {
        MedsFormStand.open(world)
        if (MedsFormStand.open(world).applicability.stockThresholdEnabled) {
            val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).performClick() }
            assertTrue(
                "the switch must turn the reminder off: $intents",
                intents.any { it == MedFormIntent.StockReminderToggled(false) },
            )
        }
        assertNull("the draft must hold no threshold", MedsFormStand.open(world).draft.stockThresholdDays)
    }

    @Когда("^человек включает «Напомнить о покупке»$")
    fun thePersonTurnsTheStockReminderOn() {
        val before = MedsFormStand.open(world).applicability.stockThresholdEnabled
        assertFalse("the switch must start off", before)
        val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).performClick() }
        assertTrue(
            "the switch must report enabling: $intents",
            intents.any { it is MedFormIntent.StockReminderToggled && it.enabled },
        )
    }

    @Когда("^человек выключает «Напомнить о покупке»$")
    fun thePersonTurnsTheStockReminderOff() {
        assertTrue("the switch must start on", MedsFormStand.open(world).applicability.stockThresholdEnabled)
        val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).performClick() }
        assertTrue(
            "the switch must report disabling: $intents",
            intents.any { it == MedFormIntent.StockReminderToggled(false) },
        )
    }

    @Тогда("^включён переключатель «Напомнить о покупке»$")
    fun theStockReminderSwitchIsOn() {
        val state = MedsFormStand.open(world)
        assertTrue("the reminder must be on", state.applicability.stockThresholdEnabled)
        assertNotNull("the reminder must hold a threshold", state.draft.stockThresholdDays)
    }

    @Тогда("^у лекарства не задан порог напоминания$")
    fun theThresholdIsGone() {
        assertNull("the threshold must be cleared", MedsFormStand.open(world).draft.stockThresholdDays)
    }

    @Тогда("^предупреждение об остатке для него не показывается$")
    fun theStockWarningIsNotShown() {
        // Предупреждение включается порогом: пустой порог и есть выключенное
        // предупреждение (ПР-13), отдельного флага рядом со значением нет.
        assertNull("no threshold means no warning", MedsFormStand.open(world).draft.stockThresholdDays)
    }

    @Тогда("^порог напоминания равен (\\d+) дням$")
    fun theThresholdIs(days: Int) {
        assertEquals(days, MedsFormStand.open(world).draft.stockThresholdDays)
        MedsFormStand.ask(world) { test -> thresholdOption(test, days).assertIsSelected() }
    }

    @Тогда("^под строкой показано «Когда останется на (\\d+) дн\\S+»$")
    fun theCaptionUnderTheRowIs(days: Int) {
        val expected = context.resources.getQuantityString(R.plurals.stock_threshold_caption, days, days)
        MedsFormStand.ask(world) { test -> test.onNodeWithText(expected).assertExists() }
    }

    @Когда("^человек выбирает порог (\\d+) дней$")
    fun thePersonChoosesThreshold(days: Int) {
        val intents = MedsFormStand.act(world) { test -> thresholdOption(test, days).performClick() }
        assertTrue(
            "the chip $days must change the threshold: $intents",
            intents.any { it is MedFormIntent.StockThresholdChanged && it.days == days },
        )
    }

    @Тогда("^выбранный порог отмечен$")
    fun theChosenThresholdIsMarked() {
        val days = requireNotNull(MedsFormStand.open(world).draft.stockThresholdDays) { "no threshold was chosen" }
        MedsFormStand.ask(world) { test -> thresholdOption(test, days).assertIsSelected() }
    }

    // --- Сохранённый порог -------------------------------------------------------------

    @Допустим("^есть лекарство «([^»]+)» с остатком (\\d+) таблеток и порогом (\\d+) дней$")
    fun thereIsMedicationWithStockAndThreshold(medication: String, units: Int, days: Int) {
        ensureMedication(medication)
        world.medications.update(medication) {
            it.copy(stockLeft = BigDecimal(units), stockThresholdDays = days)
        }
    }

    @Допустим("^есть лекарство «([^»]+)» с порогом (\\d+) дней$")
    fun thereIsMedicationWithThreshold(medication: String, days: Int) {
        ensureMedication(medication)
        world.medications.update(medication) { it.copy(stockThresholdDays = days) }
    }

    @Допустим("^у лекарства «([^»]+)» сохранён порог (\\d+) дней$")
    fun theSavedThresholdIs(medication: String, days: Int) {
        ensureMedication(medication)
        world.medications.update(medication) { it.copy(stockThresholdDays = days) }
    }

    @Когда("^человек открывает форму правки «([^»]+)»$")
    fun thePersonOpensTheEditForm(medication: String) {
        val name = world.medicationNamed(medication)
        MedsFormStand.showEdit(world, world.medications.get(name))
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).assertExists() }
    }

    @Тогда("^порог лекарства не меняется сам по себе на (\\d+) дней$")
    fun theSavedThresholdDoesNotFollowTheNewDefault(days: Int) {
        val name = world.medications.last
        val saved = world.medications.get(name).stockThresholdDays
        assertFalse("the saved threshold must not become the new default", saved == days)
        assertFalse(
            "the edit form must show the saved threshold, not the new default",
            MedsFormStand.open(world).draft.stockThresholdDays == days,
        )
    }

    // --- Перенос данных ------------------------------------------------------------------

    @Когда("^содержимое выгружается в файл и загружается обратно$")
    fun theContentGoesToAFileAndBack() {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        val exported = world.transfer.export(handle = ROUND_TRIP_HANDLE)
        assertTrue("the export must succeed: $exported", exported is ExportOutcome.Saved)
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        world.transfer.read(handle = ROUND_TRIP_HANDLE)
        val outcome = world.transfer.apply()
        assertTrue("the import must succeed: $outcome", outcome is TransferOutcome.Applied)
    }

    @Тогда("^у загруженного лекарства «([^»]+)» порог напоминания равен (\\d+) дням$")
    fun theImportedMedicationKeepsItsThreshold(medication: String, days: Int) {
        val imported = requireNotNull(world.medications.all().firstOrNull { it.name == medication }) {
            "the imported medication must be in the world: ${world.medications.names()}"
        }
        assertEquals(days, imported.stockThresholdDays)
    }

    // --- Предупреждение о покупке (сценарии @warnings) ------------------------------------

    @Допустим("^у лекарства «([^»]+)» остаток на (\\d+) дней$")
    fun theStockOfThatMedicationLastsThatManyDays(medication: String, days: Int) {
        val name = ensureMedication(medication)
        val daily = requireNotNull(StockForecast.dailyUse(world.medications.get(name))) {
            "«$medication» must have a known daily use for this step"
        }
        world.medications.update(name) { it.copy(stockLeft = daily * BigDecimal(days)) }
        assertEquals("forecast of $medication", StockForecast.Days(days), StockForecast.of(world.medications.get(name)))
    }

    @Допустим("^у лекарства «([^»]+)» остаток ровно на (\\d+) дней$")
    fun theStockOfThatMedicationLastsExactlyThatManyDays(medication: String, days: Int) {
        theStockOfThatMedicationLastsThatManyDays(medication, days)
    }

    @Допустим("^у лекарства задан порог (\\d+) дней$")
    fun theWarningThresholdOfTheMedicationIs(days: Int) {
        world.medications.update(world.medications.last) { it.copy(stockThresholdDays = days) }
    }

    @Тогда("^показано предупреждение о покупке лекарства «([^»]+)»$")
    fun theWarningOfThatMedicationIsShown(medication: String) {
        val expected = world.medications.get(world.medicationNamed(medication)).id
        val warning = warningOnToday()
        assertNotNull("expected a purchase warning for $medication", warning)
        assertEquals("the plate shows $warning", expected, warning!!.medicationId)
    }

    @Тогда("^предупреждение о покупке лекарства «([^»]+)» не показывается$")
    fun theWarningOfThatMedicationIsNotShown(medication: String) {
        val name = world.medicationNamed(medication)
        assertNull(
            "unexpected warning for $medication",
            ru.stohastika.sklyanka.domain.stock.StockWarning.of(world.medications.get(name)),
        )
        assertTrue(
            "the plate still shows $medication",
            warningOnToday()?.medicationId != world.medications.get(name).id,
        )
    }

    @Тогда("^предупреждение о покупке (показывается|не показывается)$")
    fun theWarningIsShownOrNot(shown: String) {
        if (shown == SHOWN) {
            assertNotNull("the scenario expects a warning", warningOnToday())
        } else {
            assertNull("the scenario expects no warning", warningOnToday())
        }
    }

    /**
     * Предупреждение, которое увидит «Сегодня»: считается настоящим доменом (ПР-13).
     *
     * Строку плашки проверяет вёрстка «Сегодня» (SKL-08), здесь важно решение
     * «показывать или нет» — оно доменное.
     */
    private fun warningOnToday(): ru.stohastika.sklyanka.domain.stock.StockWarning? {
        val useCase = ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase(
            world.repositories.medicationStore,
            world.clock.time,
            world.log,
        )
        return kotlinx.coroutines.runBlocking {
            useCase(world.profiles.get(world.profiles.active).id).first()
        }
    }

    // --- Обвязка -------------------------------------------------------------------------

    /** Лекарство заводится по первому упоминанию: порядок шагов Предыстории не гарантирован. */
    private fun ensureMedication(medication: String): String {
        if (!world.medications.has(medication)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(MORNING),
            )
            // Имя из сценария остаётся и в модели: им же проверяется перенос данных.
            world.medications.update(medication) { it.copy(name = medication) }
        }
        world.medications.focusOn(medication)
        return medication
    }

    private fun thresholdOption(test: androidx.compose.ui.test.ComposeUiTest, days: Int) =
        test.onNode(hasText(days.toString()) and hasAnyAncestor(hasTestTag(TAG_STOCK_THRESHOLD_OPTIONS)))

    private companion object {
        const val ROUND_TRIP_HANDLE = "chosen://skl74-round-trip"
        const val SHOWN = "показывается"
        val MORNING: LocalTime = LocalTime.of(8, 0)
    }
}
