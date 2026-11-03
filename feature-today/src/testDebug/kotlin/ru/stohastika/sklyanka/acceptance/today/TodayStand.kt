package ru.stohastika.sklyanka.acceptance.today

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.InMemoryDatabaseLife
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.UndoMarkUseCase
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.startup.ExplainDatabaseResetUseCase
import ru.stohastika.sklyanka.domain.stock.StockWarning
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.DayProgress
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.TodayAgenda
import ru.stohastika.sklyanka.domain.today.TodayLayout
import ru.stohastika.sklyanka.domain.undo.UndoableMark
import ru.stohastika.sklyanka.feature.today.AnalyticsTestKit
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.feature.today.TodayViewModel
import ru.stohastika.sklyanka.feature.today.UndoNotice
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Повестка суток и состояние экрана «Сегодня» из мира сценария приёмки.
 *
 * Собирается настоящим доменом ([BuildTodayAgenda]) и тем же правилом отображения,
 * которым `TodayViewModel` превращает повестку в состояние экрана. Выдуманное состояние
 * проверяло бы разметку, а приёмка SKL-08 говорит о продукте: какие приёмы попали
 * в сутки активного профиля и что о них сказано.
 */
internal object TodayStand {

    fun agenda(world: AcceptanceWorld): TodayAgenda {
        // Планировщик здесь не запускается намеренно: окно генерации начинается «сейчас»
        // (SKL-06), и запуск после перевода часов вперёд не создал бы приёма, время
        // которого уже прошло. Приёмы суток порождает Предыстория, в свой момент.
        val profileName = world.profiles.active
        val profile = world.profiles.get(profileName)
        val date = world.clock.today()
        return BuildTodayAgenda()(
            date = date,
            intakes = world.intakes.ofProfile(world.profiles, profileName)
                .filter { it.scheduledAt.toLocalDate() == date },
            activeMedications = world.medications.all().filter { it.profileId == profile.id && !it.isArchived },
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
            stockWarning = stockWarning(world, profile.id),
        )
    }

    /**
     * Самое срочное предупреждение об остатке — тем же доменным сценарием, что в продукте.
     *
     * Плашка на экране показывает одно предупреждение из сработавших порогов, и решает,
     * какое (SKL-12): копии этого выбора в стенде нет.
     */
    private fun stockWarning(world: AcceptanceWorld, profileId: String): StockWarning? {
        val useCase = StockWarningForProfileUseCase(
            world.repositories.medicationStore,
            world.clock.time,
            world.log,
        )
        return runBlocking { useCase(profileId).first() }
    }

    fun progress(world: AcceptanceWorld): DayProgress = agenda(world).progress

    /** Лекарства, приёмы которых экран действительно показывает. */
    fun shownMedicationIds(world: AcceptanceWorld): Set<String> = when (val layout = agenda(world).layout) {
        is TodayLayout.Plain -> layout.groups
        is TodayLayout.Dense -> layout.sections.flatMap { it.groups }
    }.flatMap { group -> group.items.map { it.medication.id } }.toSet()

    fun state(world: AcceptanceWorld, toggles: TodayScreenToggles = TodayScreenToggles()): TodayUiState {
        val agenda = agenda(world)
        return if (agenda.isFirstRun) {
            TodayUiState.Empty(agenda.date)
        } else {
            TodayUiState.Content(
                date = agenda.date,
                progress = agenda.progress,
                stockWarning = agenda.stockWarning,
                reliability = reliability(world),
                // Окно отмены — из того же магазина, что в продукте (SKL-30): срок
                // проверяется магазином, а не таймером стенда.
                undo = (world.undoActions.live() as? UndoableMark)?.let { mark ->
                    UndoNotice(mark.intakeIds, mark.result, mark.medicationNames)
                },
                layout = agenda.layout,
                prn = agenda.prn,
                toggledSections = toggles.toggledSections,
                confirmingGroup = toggles.confirmationAt(world.clock.nowLocal()),
                prnCollapsed = toggles.prnCollapsed,
                nothingToday = agenda.hasNothingToday,
            )
        }
    }

    /**
     * Подтверждение групповой отметки живо, пока часы стенда не дошли до дедлайна.
     *
     * Таймер живёт в `TodayViewModel` и в стенде не крутится: сценарий сам двигает
     * `ScenarioClock`, а срок зеркалится дедлайном ([TodayScreenToggles.confirmingUntil],
     * `TDD.md §14.2`).
     */
    private fun TodayScreenToggles.confirmationAt(now: LocalDateTime): LocalTime? =
        confirming?.takeIf { confirmingUntil != null && now < confirmingUntil }

    /**
     * Надёжность напоминаний — тем же доменным сценарием, что в `TodayViewModel`.
     *
     * Стенд не пересказывает правило «какая причина»: `ReminderReliability` сводит два
     * состояния разрешений к причине в домене (SKL-13), и второй копии этого правила
     * в приёмке нет.
     */
    private fun reliability(world: AcceptanceWorld) = ReadReminderReliabilityUseCase(world.system)().warning

    /**
     * Настоящий `TodayViewModel` на мире сценария — для проверок жизненного цикла окна
     * отмены (SKL-30): уход с экрана проверяется на том же графе сценариев, что у продукта,
     * а не на пересказе.
     */
    fun viewModel(world: AcceptanceWorld): TodayViewModel {
        val time = world.clock.time
        val markIntake = MarkIntakeUseCase(
            world.repositories.intakeStore,
            world.repositories.medicationStore,
            world.repositories.profileStore,
            time,
            world.reminders.markSideEffects(),
        )
        return TodayViewModel(
            observeAgenda = ObserveTodayAgendaUseCase(
                activeProfile = world.profileSession.activeStore,
                intakes = world.repositories.intakeStore,
                medications = world.repositories.medicationStore,
                sweepMissed = SweepMissedIntakesUseCase(
                    world.repositories.intakeStore,
                    world.intakes.window,
                    time,
                    world.log,
                ),
                stockWarnings = StockWarningForProfileUseCase(world.repositories.medicationStore, time, world.log),
                time = time,
                build = BuildTodayAgenda(),
            ),
            markIntake = markIntake,
            markGroup = MarkIntakeGroupUseCase(
                markIntake,
                ScenarioTransactor(world.intakes, world.medications),
                time,
                world.log,
            ),
            createPrn = CreatePrnIntakeUseCase(
                world.repositories.intakeStore,
                world.repositories.medicationStore,
                world.repositories.profileStore,
                time,
            ),
            undoMark = UndoMarkUseCase(
                world.repositories.intakeStore,
                world.repositories.medicationStore,
                world.repositories.profileStore,
            ),
            undoActions = world.undoActions,
            undoDelete = UndoDeleteMedicationUseCase(
                world.repositories.medicationStore,
                MedicationPlanner { _, reason ->
                    PlanResult(
                        window = GenerationWindow.of(world.clock.today(), MIN_PLAN_DEPTH_DAYS),
                        reason = reason,
                        medications = emptyList(),
                    )
                },
            ),
            time = time,
            readReliability = ReadReminderReliabilityUseCase(world.system),
            explainDatabaseReset = ExplainDatabaseResetUseCase(InMemoryDatabaseLife(), time),
            // Стенд проверяет не события, а поведение экрана: подмены аналитики те же,
            // что у модульных тестов модуля.
            analytics = AnalyticsTestKit(CoroutineScope(Dispatchers.Unconfined)).recorder,
        )
    }
}

/** Наименьшая глубина окна генерации: тесты «Сегодня» ничего не планируют. */
private const val MIN_PLAN_DEPTH_DAYS = 3

/**
 * Ручные переключения экрана, накопленные сценарием.
 *
 * В продукте они живут в потоке `TodayViewModel` и переживают пересборку повестки дня
 * (`TDD.md §5.2`). Стенд собирает экран заново на каждый шаг, поэтому накопленное
 * хранит [TodayScreenProbe] между шагами сценария, а состояние принимает готовым.
 *
 * Подтверждение групповой отметки — тоже зрительное состояние (`TDD.md §14.2`):
 * [confirmingUntil] — зеркало десятисекундного срока `TodayViewModel`, а не второй
 * таймер: часы двигает сам сценарий.
 */
data class TodayScreenToggles(
    val toggledSections: Set<PartOfDay> = emptySet(),
    val confirming: LocalTime? = null,
    val confirmingUntil: LocalDateTime? = null,
    val prnCollapsed: Boolean = false,
)
