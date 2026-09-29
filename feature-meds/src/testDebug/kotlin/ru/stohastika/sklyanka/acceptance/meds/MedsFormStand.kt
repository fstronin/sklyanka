package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Стенд формы лекарства для шагов приёмки: черновик, собранная композиция и намерения.
 *
 * Правила — не копия: применимость блоков и сброс неприменимых значений считает
 * `MedicationDraft.normalized()` и `applicability()` из домена, тексты берутся
 * из ресурсов модуля. Отображение намерения в новый черновик повторяет
 * `MedicationFormViewModel.reduce` намеренно: это отображение разметки, а не правило
 * продукта, и живёт оно в ViewModel. Проверка самих правил — дело
 * `MedicationFormViewModelTest`, а здесь проверяется, что собранный экран показывает
 * то, что человек выбрал.
 *
 * Состояние переживает шаги одного сценария и обнуляется на новом мире: мир сценария
 * создаётся заново, и это единственный признак нового сценария, видимый стенду
 * (тот же приём, что в `TodayScreenProbe`).
 */
@OptIn(ExperimentalTestApi::class)
internal object MedsFormStand {

    private var lastWorld: AcceptanceWorld? = null
    private var state: MedFormUiState? = null
    private var cancelledHere = false

    /** Форма нового лекарства; времена можно задать предпосылкой сценария. */
    fun open(world: AcceptanceWorld, times: List<LocalTime> = emptyList()): MedFormUiState {
        resetIfNewWorld(world)
        val current = state
        if (current != null) return current
        val draft = MedicationDraft.new(profileId(world), world.clock.today()).copy(times = times)
        return MedFormUiState.of(draft.normalized(), FormMode.Create, FormInputs()).also { state = it }
    }

    /**
     * Форма правки: тот же черновик, заполненный из существующего лекарства.
     *
     * Повторяет `MedicationFormViewModel.open(medicationId)`: `MedicationDraft.of` даёт
     * черновик, заголовок шапки — название лекарства, тексты числовых полей — записанные
     * значения. Правила переиспользуются, а не пересказываются.
     */
    fun openEdit(world: AcceptanceWorld, medication: Medication): MedFormUiState {
        resetIfNewWorld(world)
        val current = state
        if (current != null) return current
        val draft = MedicationDraft.of(medication)
        return MedFormUiState.of(draft, FormMode.Edit(medication.name), inputsOf(draft)).also { state = it }
    }

    /** Открыта ли форма в этом сценарии и была ли она закрыта «Отменой». */
    val cancelled: Boolean get() = cancelledHere

    /** Применить намерение напрямую: так Предыстория задаёт заполненную форму. */
    fun intent(world: AcceptanceWorld, intent: MedFormIntent): MedFormUiState {
        val next = apply(open(world), intent)
        state = next
        return next
    }

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(world: AcceptanceWorld, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationFormScreen(
                        state = open(world),
                        onIntent = {},
                        onCancel = {},
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
                }
            }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Действие человека: экран отдаёт намерения, стенд применяет их к черновику. */
    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit): List<MedFormIntent> {
        val intents = mutableListOf<MedFormIntent>()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationFormScreen(
                        state = open(world),
                        onIntent = intents::add,
                        onCancel = { cancelledHere = true },
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
                }
            }
            action(this)
            waitForIdle()
        }
        intents.forEach { state = apply(open(world), it) }
        return intents
    }

    /** Нажатие «Отмены» в шапке: форма закрывается без сохранения. */
    fun cancel(world: AcceptanceWorld, cancelLabel: String) {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationFormScreen(
                        state = open(world),
                        onIntent = {},
                        onCancel = { cancelledHere = true },
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
                }
            }
            onNodeWithText(cancelLabel).performClick()
            waitForIdle()
        }
    }

    private fun apply(current: MedFormUiState, intent: MedFormIntent): MedFormUiState {
        val draft = current.draft
        val inputs = current.inputs
        return when (intent) {
            is MedFormIntent.NameChanged -> current.with(draft.copy(name = intent.value))
            is MedFormIntent.ReleaseFormChanged -> current.with(draft.copy(form = intent.form))
            is MedFormIntent.ShapeChanged -> current.with(draft.copy(shape = intent.shape))
            is MedFormIntent.DoseAmountChanged -> current.with(
                draft.copy(doseAmount = decimalOf(intent.text)),
                inputs.copy(doseAmount = intent.text),
            )

            is MedFormIntent.StrengthChanged -> current.with(
                draft.copy(
                    strength = decimalOf(intent.text),
                    strengthUnit = if (decimalOf(intent.text) == null) null else STRENGTH_UNIT,
                ),
                inputs.copy(strength = intent.text),
            )

            is MedFormIntent.ModeChanged -> current.with(draft.copy(scheduleMode = intent.mode))
            MedFormIntent.TimeAdded -> current.with(draft.copy(times = draft.times + nextTime(draft.times)))
            MedFormIntent.TimeRemovedFromEnd -> current.with(draft.copy(times = draft.times.dropLast(1)))
            is MedFormIntent.TimeRemoved ->
                current.with(draft.copy(times = draft.times.filterIndexed { index, _ -> index != intent.index }))

            is MedFormIntent.TimeChanged -> current.with(
                draft.copy(
                    times = draft.times.mapIndexed { index, time ->
                        if (index == intent.index) intent.time else time
                    },
                ),
            )

            MedFormIntent.MaxPerDayIncreased -> current.with(draft.copy(maxPerDay = (draft.maxPerDay ?: 0) + 1))
            MedFormIntent.MaxPerDayDecreased ->
                current.with(draft.copy(maxPerDay = maxOf(0, (draft.maxPerDay ?: 0) - 1)))

            is MedFormIntent.FoodRelationChanged -> current.with(draft.copy(foodRelation = intent.relation))
            is MedFormIntent.StockChanged -> current.with(
                draft.copy(stockLeft = decimalOf(intent.text)),
                inputs.copy(stockLeft = intent.text),
            )

            is MedFormIntent.StockReminderToggled -> current.with(
                draft.copy(stockThresholdDays = if (intent.enabled) DEFAULT_THRESHOLD_DAYS else null),
            )

            is MedFormIntent.StartDateChanged -> current.with(draft.copy(startDate = intent.date))
            is MedFormIntent.EndDateChanged -> current.with(draft.copy(endDate = intent.date))
            is MedFormIntent.IndefiniteToggled -> current.with(
                draft.copy(endDate = if (intent.indefinite) null else draft.startDate),
            )

            MedFormIntent.Save -> current
        }
    }

    private fun MedFormUiState.with(draft: MedicationDraft, inputs: FormInputs = this.inputs): MedFormUiState =
        MedFormUiState.of(draft.normalized(), mode, inputs)

    private fun resetIfNewWorld(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            state = null
            cancelledHere = false
        }
    }

    /** Текст числовых полей при открытии правки: то, что уже записано (как в ViewModel). */
    private fun inputsOf(draft: MedicationDraft): FormInputs = FormInputs(
        doseAmount = draft.doseAmount.plain(),
        strength = draft.strength.plain(),
        stockLeft = draft.stockLeft.plain(),
    )

    private fun BigDecimal?.plain(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

    private fun profileId(world: AcceptanceWorld): String = world.profiles.get(world.profiles.active).id

    private fun decimalOf(text: String): BigDecimal? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

    private fun nextTime(times: List<LocalTime>): LocalTime =
        times.lastOrNull()?.plusHours(TIME_STEP_HOURS) ?: DEFAULT_TIME

    private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
    private const val TIME_STEP_HOURS = 4L
    private const val DEFAULT_THRESHOLD_DAYS = 5
    private const val STRENGTH_UNIT = "mg"
}
