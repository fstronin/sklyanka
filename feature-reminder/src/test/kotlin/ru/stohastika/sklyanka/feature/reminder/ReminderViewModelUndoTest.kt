package ru.stohastika.sklyanka.feature.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.intake.IntakeTransition
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkDecision
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.intake.UndoableMarkStore
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderCardItem
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderIntake
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Публикация окна отмены из карточки напоминания (SKL-30).
 *
 * Окно открывается только действием, **закрывшим** карточку: частичная отметка строки
 * и «Отложить» не дают отмены ни на карточке, ни на «Сегодня» (ОВ-81/ОВ-6).
 *
 * Проверяется на JVM: эмулятора в среде задачи нет, а поведению ViewModel он и не нужен.
 * Показ снэкбара на «Сегодня» — предмет экранного прогона и Compose-теста.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelUndoTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a mark that closes the card publishes the intake with its name`() = runTest {
        val world = ReminderTestWorld()
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)
        assertTrue(viewModel.state.value is ReminderUiState.Card)

        viewModel.onTaken("i-1")

        assertEquals(ReminderUiState.Closed, viewModel.state.value)
        val mark = requireNotNull(world.undoMarks.live())
        assertEquals(listOf("i-1"), mark.intakeIds)
        assertEquals(listOf("Magnesium B6"), mark.medicationNames)
    }

    @Test
    fun `a partial row mark neither closes the card nor opens a window`() = runTest {
        val world = ReminderTestWorld(withSecondRow = true)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onTaken("i-1")

        val state = viewModel.state.value
        assertTrue("the card must stay open", state is ReminderUiState.Card)
        assertEquals(listOf("i-2"), (state as ReminderUiState.Card).items.map { it.intakeId })
        assertNull(world.undoMarks.live())
    }

    @Test
    fun `the bottom button publishes only the intakes the action actually changed`() = runTest {
        val world = ReminderTestWorld(withSecondRow = true)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)
        // Второй приём отмечен часами, пока карточка открыта: в действие он не входит.
        world.markOnWatch("i-2")

        viewModel.onTakenAll()

        assertEquals(ReminderUiState.Closed, viewModel.state.value)
        val mark = requireNotNull(world.undoMarks.live())
        assertEquals(listOf("i-1"), mark.intakeIds)
        assertEquals(listOf("Magnesium B6"), mark.medicationNames)
    }

    @Test
    fun `a snooze gives no undo window`() = runTest {
        val world = ReminderTestWorld(withSecondRow = true)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onSnooze()

        assertEquals(ReminderUiState.Closed, viewModel.state.value)
        assertNull(world.undoMarks.live())
    }

    @Test
    fun `an already closed card publishes nothing`() = runTest {
        val world = ReminderTestWorld()
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)
        world.markOnWatch("i-1")

        viewModel.onTaken("i-1")

        assertEquals(ReminderUiState.Closed, viewModel.state.value)
        assertNull(world.undoMarks.live())
    }
}

/** Мир карточки напоминания в памяти: те же правила перехода, что в домене. */
private class ReminderTestWorld(withSecondRow: Boolean = false) {

    val profileId = "profile-self"
    val scheduledAt: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0)
    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val clock = FixedTimeSource(scheduledAt.atZone(zone).toInstant(), zone)
    val undoMarks = UndoableMarkStore(clock)

    private val intakes = linkedMapOf<String, Intake>(
        "i-1" to pending("i-1", "m-1"),
    ).apply {
        if (withSecondRow) this["i-2"] = pending("i-2", "m-2")
    }

    private val medications = buildMap {
        put("m-1", medication("m-1", "Magnesium B6"))
        put("m-2", medication("m-2", "Panangin"))
    }

    private val profile = Profile(
        id = profileId,
        name = "self",
        color = 0,
        isSelf = true,
        mirrorReminders = false,
    )

    fun markOnWatch(intakeId: String) {
        val current = requireNotNull(intakes[intakeId])
        intakes[intakeId] = current.copy(
            status = IntakeStatus.TAKEN,
            actedAt = clock.now(),
            source = IntakeSource.WATCH,
        )
    }

    fun viewModel() = ReminderViewModel(
        cards = FakeCards(),
        mark = MarkIntakeUseCase(FakeIntakes(), FakeMedications(), FakeProfiles(), clock),
        snooze = SnoozeReminderUseCase(NoSnoozePlan(), NoAlarms(), NoNotifications(), NoJournal(), NoDeferred(), clock),
        settings = FakeSettings(),
        undoMarks = undoMarks,
    )

    private fun pending(id: String, medicationId: String) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    private fun medication(id: String, name: String) = Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal.ONE,
        doseUnit = DoseUnit.TABLET.code,
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(14, 0)),
        startDate = LocalDate.of(2026, 9, 1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private inner class FakeCards : ReminderCardSource {

        override suspend fun card(slot: ReminderSlot, mirroredToWatch: Boolean): ReminderCard? = ReminderCard(
            slot = slot,
            scheduledAt = slot.scheduledAt,
            items = intakes.values.filter { it.isPending }.map { intake ->
                val medication = requireNotNull(medications[intake.medicationId])
                ReminderCardItem(
                    intakeId = intake.id,
                    medicationId = medication.id,
                    medicationName = medication.name,
                    form = medication.form,
                    doseAmount = medication.doseAmount,
                    doseUnit = medication.doseUnit,
                    strength = medication.strength,
                    strengthUnit = medication.strengthUnit,
                )
            },
            mirroredToWatch = false,
            profileIsSelf = true,
            profileName = profile.name,
        )
    }

    private inner class FakeIntakes : IntakeRepository {

        override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = flowOf(emptyList())

        override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
            flowOf(emptyList())

        override fun observeByMedication(
            profileId: String,
            medicationId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Intake>> = flowOf(emptyList())

        override suspend fun get(intakeId: String): Intake? = intakes[intakeId]

        override suspend fun insertMissing(intakes: List<Intake>): Int = 0

        override suspend fun scheduledBetween(
            profileId: String,
            medicationId: String,
            from: LocalDateTime,
            toExclusive: LocalDateTime,
        ): List<Intake> = emptyList()

        override suspend fun applyPlan(
            profileId: String,
            medicationId: String,
            diff: IntakePlanDiff,
        ): PlanApplyResult = PlanApplyResult(created = 0, deleted = 0)

        override suspend fun markIfPending(
            intakeId: String,
            status: IntakeStatus,
            source: IntakeSource,
            actorProfileId: String?,
        ): Boolean = false

        override suspend fun expiredPending(before: LocalDateTime): List<Intake> = emptyList()

        override suspend fun markAtomically(command: MarkCommand): MarkOutcome {
            val current = intakes[command.intakeId] ?: return MarkOutcome.NotFound
            return when (val decision = IntakeTransition.decide(current, command)) {
                is MarkDecision.Keep -> MarkOutcome.Ignored(decision.intake, decision.reason)
                is MarkDecision.Write -> {
                    intakes[command.intakeId] = decision.intake
                    if (decision.superseded) {
                        MarkOutcome.Superseded(decision.intake, current)
                    } else {
                        MarkOutcome.Applied(decision.intake)
                    }
                }
            }
        }

        override suspend fun undoAtomically(command: UndoCommand): UndoOutcome = UndoOutcome.NotFound

        override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome = PrnOutcome.UnknownMedication

        override suspend fun markMissed(ids: List<String>, at: Instant): Int = 0
    }

    private inner class FakeMedications : MedicationRepository {

        override fun observeActive(profileId: String): Flow<List<Medication>> = flowOf(emptyList())

        override fun observeArchived(profileId: String): Flow<List<Medication>> = flowOf(emptyList())

        override suspend fun get(medicationId: String): Medication? = medications[medicationId]

        override suspend fun save(medication: Medication) = Unit

        override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean = false

        override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) = Unit
    }

    private inner class FakeProfiles : ProfileRepository {

        override fun observeAll(): Flow<List<Profile>> = flowOf(listOf(profile))

        override suspend fun get(profileId: String): Profile? = profile.takeIf { it.id == profileId }

        override suspend fun getSelf(): Profile = profile

        override suspend fun save(profile: Profile) = Unit
    }

    private class FakeSettings : ReminderSettings {

        override suspend fun read(): ReminderSettingsValues = ReminderSettingsValues()

        override suspend fun setPersistentEnabled(enabled: Boolean) = Unit

        override suspend fun setSnoozeMinutes(minutes: Int) = Unit

        override suspend fun setAlarmStrategy(kind: AlarmKind) = Unit
    }
}

/** Порты откладывания, которые тесту окна отмены не нужны: откладывать здесь нечего. */
private class NoSnoozePlan : ReminderPlanSource {

    override suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake> = emptyList()

    override suspend fun intake(intakeId: String): ReminderIntake? = null

    override suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake> = emptyList()
}

private class NoAlarms : AlarmGateway {

    override suspend fun schedule(intakeId: String, at: Instant, attempt: Int) = Unit

    override suspend fun cancel(intakeId: String) = Unit

    override suspend fun scheduled(): Map<String, Instant> = emptyMap()

    override suspend fun scheduleAnchor(at: Instant) = Unit

    override suspend fun scheduleDayBoundary(at: Instant) = Unit

    override fun canScheduleExact(): Boolean = true

    override suspend fun alarmKind(): AlarmKind = AlarmKind.ALARM_CLOCK
}

private class NoNotifications : NotificationGateway {

    override suspend fun show(card: ReminderCard, channel: ReminderChannel) = Unit

    override suspend fun cancel(slot: ReminderSlot) = Unit

    override suspend fun isShown(slot: ReminderSlot): Boolean = false

    override suspend fun retainOnly(pendingIntakeIds: Set<String>) = Unit

    override fun areNotificationsEnabled(): Boolean = true
}

/** Отложенное хранилище, которое тесту окна отмены не нужно: откладывать здесь нечего. */
private class NoDeferred : DeferredReminders {

    override suspend fun all(): List<DeferredReminder> = emptyList()

    override suspend fun find(intakeId: String): DeferredReminder? = null

    override suspend fun put(reminder: DeferredReminder) = Unit

    override suspend fun remove(intakeId: String): Boolean = false
}

private class NoJournal : ReminderJournal {

    override suspend fun record(entry: ReminderLogEntry) = Unit

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = emptyList()

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = null

    override suspend fun firedIntakeIds(since: Instant): Set<String> = emptySet()

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}
