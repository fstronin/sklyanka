package ru.heuri.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.profile.TodayFromWorld
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.MedicationListRules
import ru.heuri.sklyanka.domain.medication.SaveResult
import ru.heuri.sklyanka.domain.medication.normalized
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.feature.meds.form.FormInputs
import ru.heuri.sklyanka.feature.meds.form.FormMode
import ru.heuri.sklyanka.feature.meds.form.MedFormIntent
import ru.heuri.sklyanka.feature.meds.form.MedFormUiState
import ru.heuri.sklyanka.feature.meds.form.MedicationFormScreen
import ru.heuri.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.heuri.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.heuri.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.heuri.sklyanka.feature.today.TAG_STOCK_BANNER
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.ui.locale.withInterfaceLocale
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Шаги сценарной приёмки «выключенный порог остатка гасит предупреждение» (SKL-09,
 * группа `@composition`; заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужны форма и «Сегодня» одновременно: форма
 * живёт в `feature-meds`, «Сегодня» — в `feature-today`, и только здесь оба экрана
 * существуют сразу (`SKL-26/TDD.md §4.3`). Сохранение — настоящим `SaveMedicationUseCase`
 * из мира сценария: приёмка проверяет следствие сохранения, а не повторяет его правила.
 *
 * Доза и расписание выбраны так, чтобы предпосылка была сильной: при включённом
 * напоминании остаток 9 при расходе 4 в сутки дал бы предупреждение (2 дня < 5).
 * Иначе проверка «не показано» проходила бы и с забытым порогом.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedsStockCompositionSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var state: MedFormUiState? = null

    // --- Предыстория -----------------------------------------------------------

    @Допустим("^в форме лекарства «([^»]+)» задан остаток (\\d+) таблеток$")
    fun theFormHoldsThatStock(medication: String, amount: Int) {
        val profileId = world.profiles.get(world.profiles.active).id
        val draft = MedicationDraft.new(profileId, world.clock.today()).copy(
            name = medication,
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND,
            doseAmount = DOSE_PER_INTAKE,
            scheduleMode = ScheduleMode.SCHEDULED,
            times = TIMES,
            stockLeft = BigDecimal(amount),
            stockThresholdDays = DEFAULT_THRESHOLD_DAYS,
        )
        assertTrue(
            "the premise must be a fired threshold: with the reminder on, the stock must not last $DEFAULT_THRESHOLD_DAYS days",
            MedicationListRules.stockThresholdReached(draft.toMedication("premise-check")),
        )
        state = MedFormUiState.of(draft.normalized(), FormMode.Create, FormInputs(stockLeft = amount.toString()))
        form { test -> test.onNodeWithTag(TAG_STOCK_FIELD, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Допустим("^переключатель «Напомнить о покупке» выключен$")
    fun thePurchaseReminderIsOff() {
        val intents = form { test -> test.onNodeWithTag(TAG_STOCK_REMINDER).performClick() }
        assertTrue(
            "the switch must turn the reminder off: $intents",
            MedFormIntent.StockReminderToggled(false) in intents,
        )
        assertNull("the draft must hold no threshold", requireNotNull(state).draft.stockThresholdDays)
    }

    // --- Действие и следствие ----------------------------------------------------

    @Когда("^лекарство сохранено и человек открывает экран «Сегодня»$")
    fun theMedicationIsSavedAndTodayIsOpened() {
        val intents = form { test -> test.onNodeWithTag(TAG_SAVE_BUTTON).performClick() }
        assertTrue("the form must ask to save the medication: $intents", MedFormIntent.Save in intents)
        val result = world.save(requireNotNull(state).draft)
        assertTrue("the medication must be saved, not refused: $result", result is SaveResult.Saved)
    }

    @Тогда("^предупреждения об остатке для «([^»]+)» не показано$")
    fun theStockWarningIsNotShown(medication: String) {
        val name = world.medicationNamed(medication)
        assertNull(
            "the saved medication must carry no threshold (ПР-13)",
            world.medications.get(name).stockThresholdDays,
        )
        runComposeUiTest {
            setContent { Pinned { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            onAllNodesWithTag(TAG_STOCK_BANNER, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Обвязка ------------------------------------------------------------------

    /** Собранная форма: нажатие отдаёт намерения, шаг применяет их к состоянию. */
    private fun form(action: (ComposeUiTest) -> Unit): List<MedFormIntent> {
        val intents = mutableListOf<MedFormIntent>()
        val shown = requireNotNull(state) { "the form was not opened" }
        runComposeUiTest {
            setContent {
                Pinned {
                    MedicationFormScreen(
                        state = shown,
                        onIntent = intents::add,
                        onCancel = {},
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
                }
            }
            action(this)
            waitForIdle()
        }
        intents.forEach { state = apply(requireNotNull(state), it) }
        return intents
    }

    private fun apply(current: MedFormUiState, intent: MedFormIntent): MedFormUiState = when (intent) {
        is MedFormIntent.StockReminderToggled -> MedFormUiState.of(
            current.draft.copy(stockThresholdDays = if (intent.enabled) DEFAULT_THRESHOLD_DAYS else null).normalized(),
            current.mode,
            current.inputs,
        )

        // Сохранение состояние не меняет: его следствие разбирает мир и «Сегодня».
        else -> current
    }

    /** Язык интерфейса закреплён: даты и склонения читаются по-русски при любом телефоне (НФТ-43). */
    @Composable
    private fun Pinned(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        val DOSE_PER_INTAKE: BigDecimal = BigDecimal("2")
        val TIMES: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        const val DEFAULT_THRESHOLD_DAYS = 5
    }
}
