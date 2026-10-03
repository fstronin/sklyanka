package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.CourseCompletion
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteOutcome
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.medication.WeekAdherence
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.RefillUi
import ru.stohastika.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.stohastika.sklyanka.feature.meds.card.WatchMirrorUi
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд карточки лекарства: состояние из мира сценария и собранная композиция.
 *
 * Состояние собирается тем же путём, каким `MedicationCardViewModel` превращает лекарство
 * и его неделю приёмов в состояние экрана: ход курса считает `CourseProgress`, остаток —
 * `StockPanel` (SKL-12), неделю — `WeekAdherence` (ЭКР-6), подпись титула —
 * `MedicationSubtitle` (ЭКР-7). Выдуманное состояние проверяло бы разметку, а приёмка
 * SKL-10 говорит о продукте.
 *
 * Лекарство запрашивается **в паре с профилем** (ПР-10): чужое не открывается, и
 * [stateOrNull] возвращает `null` — как карточка, которой нечего показать.
 *
 * Нажатия уходят намерениями; архивация применяется настоящим
 * `ArchiveMedicationUseCase` с портом планировщика мира (ПР-11, НАП-11). Переходы
 * «Назад» и «Изменить» состояние карточки не меняют — их разбирает маршрут `app`.
 */
@OptIn(ExperimentalTestApi::class)
internal object MedCardStand {

    private var lastWorld: AcceptanceWorld? = null
    private var closedHere = false

    /** Состояние карточки либо `null`, если лекарство чужого профиля (ПР-10). */
    fun stateOrNull(world: AcceptanceWorld, medication: String): MedCardUiState? {
        resetIfNewWorld(world)
        val name = world.medicationNamed(medication)
        val card = world.medications.get(name)
        val profileId = world.profiles.get(world.profiles.active).id
        if (card.profileId != profileId) return null
        return state(world, card).copy(closed = closedHere)
    }

    /** Состояние карточки; открытие закрытой карточки — ошибка шага, а не пустой экран. */
    fun state(world: AcceptanceWorld, medication: String): MedCardUiState = requireNotNull(
        stateOrNull(world, medication),
    ) { "the card of «$medication» does not open in the active profile (ПР-10)" }

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(
        world: AcceptanceWorld,
        medication: String,
        refill: RefillUi? = null,
        deleteConfirmation: Boolean = false,
        question: (ComposeUiTest) -> T,
    ): T {
        var answer: T? = null
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationCardScreen(
                        state = state(world, medication)
                            .copy(refill = refill, deleteConfirmation = deleteConfirmation),
                        onIntent = {},
                    )
                }
            }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Действие человека: карточка отдаёт намерения, стенд применяет их к миру. */
    fun act(
        world: AcceptanceWorld,
        medication: String,
        deleteConfirmation: Boolean = false,
        action: (ComposeUiTest) -> Unit,
    ): List<MedCardIntent> {
        val intents = mutableListOf<MedCardIntent>()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationCardScreen(
                        state = state(world, medication).copy(deleteConfirmation = deleteConfirmation),
                        onIntent = intents::add,
                    )
                }
            }
            action(this)
            waitForIdle()
        }
        intents.forEach { apply(world, medication, it) }
        return intents
    }

    /** Архивация возвращает человека в список лекарств: маршрут закрывает карточку. */
    val closed: Boolean get() = closedHere

    /** Собрать состояние карточки из лекарства и его недельной истории — как ViewModel. */
    fun state(world: AcceptanceWorld, card: Medication): MedCardUiState {
        val today = world.clock.today()
        val from = today.minusDays((DayCell.WEEK_LENGTH - 1).toLong())
        val profileId = world.profiles.get(world.profiles.active).id
        val history: List<Intake> = runBlocking {
            world.repositories.intakeStore.observeByMedication(profileId, card.id, from, today).first()
        }
        val scheduled = card.scheduleMode == ScheduleMode.SCHEDULED
        return MedCardUiState(
            medicationId = card.id,
            title = card.name,
            form = card.form,
            subtitle = MedicationSubtitle.of(card),
            schedule = scheduleOf(card),
            course = CourseProgress.of(card, today),
            // Остаток приходит готовым из SKL-12: второй ответ на тот же вопрос не заводится.
            stock = StockPanel.of(card),
            stockUnit = DoseUnit.ofCode(card.doseUnit) ?: card.form.doseUnit(),
            week = WeekAdherence.of(card, history, today),
            // Поле состояния остаётся швом пост-MVP (ADR-0009); экран строку часов не рисует.
            watchMirror = if (scheduled) {
                WatchMirrorUi(
                    checked = card.mirrorToWatch,
                    enabled = !card.isArchived,
                )
            } else {
                null
            },
            isArchived = card.isArchived,
            courseFinishedOffer = CourseCompletion.isFinished(card, today),
            canDelete = runBlocking { world.repositories.intakeStore.hasTaken(profileId, card.id) }.not(),
            deleteConfirmation = false,
            refill = null,
            notice = null,
        )
    }

    private fun scheduleOf(card: Medication): ScheduleBlockUi = if (card.scheduleMode == ScheduleMode.SCHEDULED) {
        ScheduleBlockUi.Scheduled(
            timesPerDay = card.times.size,
            doseAmount = card.doseAmount,
            doseUnit = DoseUnit.ofCode(card.doseUnit) ?: card.form.doseUnit(),
            times = card.times.sorted(),
        )
    } else {
        ScheduleBlockUi.AsNeeded(maxPerDay = card.maxPerDay ?: 0)
    }

    private fun apply(world: AcceptanceWorld, medication: String, intent: MedCardIntent) {
        when (intent) {
            MedCardIntent.Archive -> {
                val card = world.medications.get(world.medicationNamed(medication))
                val useCase = ArchiveMedicationUseCase(world.repositories.medicationStore, world.planner())
                runBlocking { useCase(card.id, ArchiveReason.MANUAL) }
                closedHere = true
            }

            MedCardIntent.DeleteConfirmed -> {
                // Удаление — тем же доменным сценарием, что в продукте (SKL-99, ПР-14).
                val card = world.medications.get(world.medicationNamed(medication))
                val useCase = DeleteMedicationUseCase(
                    medications = world.repositories.medicationStore,
                    intakes = world.repositories.intakeStore,
                    planner = world.planner(),
                    analytics = world.analytics,
                )
                val outcome = runBlocking { useCase(card.id) }
                if (outcome is DeleteOutcome.Deleted) {
                    world.undoActions.publishDeletion(medicationId = card.id, medicationName = card.name)
                    closedHere = true
                }
            }

            // «Удалить» открывает шторку и состояние карточки не меняет; отмена — тем более.
            MedCardIntent.DeleteRequested, MedCardIntent.DeleteDismissed -> Unit

            // Переходы наружу состояние карточки не меняют: их разбирает маршрут.
            else -> Unit
        }
    }

    private fun resetIfNewWorld(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            closedHere = false
        }
    }
}
