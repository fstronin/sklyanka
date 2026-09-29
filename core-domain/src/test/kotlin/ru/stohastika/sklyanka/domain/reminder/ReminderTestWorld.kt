package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.stohastika.sklyanka.domain.intake.IntakeWindow
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.MarkSideEffects
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Обвязка тестов подсистемы напоминаний: хранилища в памяти, управляемые часы и пояс,
 * фейковые будильники и уведомления.
 *
 * Собрана вокруг сценария приёмки: профиль «Я», лекарство с приёмом в 14:00 ежедневно,
 * «сейчас» 25 сентября 2026, 09:00 (`docs/tasks/SKL-13/acceptance.feature`, Предыстория).
 * Часы и пояс управляемые, иначе окно, полночь и перелёт непроверяемы (НФТ-48).
 */
internal class ReminderTestWorld(zone: ZoneId = MOSCOW) {

    val clock = FixedTimeSource(Instant.EPOCH, zone)

    val profiles = FakeProfileRepository()
    val medications = FakeMedicationRepository(clock)
    val intakes = FakeIntakeRepository(clock, medications)
    val opLog = RecordingOperationLog()
    val window: IntakeWindow = EndOfDayIntakeWindow()

    val alarms = FakeAlarmGateway()
    val notifications = FakeNotificationGateway()
    val watch = FakeWatchGateway()
    val journal = RecordingReminderJournal()
    val settings = FakeReminderSettings()
    val permissions = FakePermissionStateReader()
    val plan = FakeReminderPlanSource(intakes, medications, profiles)
    val deferred = FakeDeferredReminders()

    val sweep = SweepMissedIntakesUseCase(intakes, window, clock, opLog)

    val reschedule = RescheduleRemindersUseCase(
        plan = plan,
        alarms = alarms,
        notifications = notifications,
        journal = journal,
        sweepMissed = sweep,
        time = clock,
    )

    val snooze = SnoozeReminderUseCase(
        plan = plan,
        alarms = alarms,
        notifications = notifications,
        journal = journal,
        deferred = deferred,
        time = clock,
    )

    val fired = HandleFiredReminderUseCase(
        plan = plan,
        cards = plan,
        notifications = notifications,
        watch = watch,
        alarms = alarms,
        journal = journal,
        settings = settings,
        window = window,
        time = clock,
    )

    val dayBoundary = SweepAtDayBoundaryUseCase(
        sweepMissed = sweep,
        plan = plan,
        notifications = notifications,
        alarms = alarms,
        journal = journal,
        time = clock,
    )

    val dismiss = DismissReminderUseCase(
        plan = plan,
        cards = plan,
        notifications = notifications,
        watch = watch,
    )

    val diagnostics = BuildReminderDiagnosticsUseCase(
        journal = journal,
        alarms = alarms,
        permissions = permissions,
        plan = plan,
        reliability = ReadReminderReliabilityUseCase(permissions),
        time = clock,
    )

    /** Отметка идёт через единственную точку отметки, вместе с её последствиями (НАП-30). */
    val mark = MarkIntakeUseCase(
        intakes = intakes,
        medications = medications,
        profiles = profiles,
        time = clock,
        sideEffects = MarkSideEffects { dismiss(it) },
    )

    init {
        profiles.put(Fixtures.profile())
        medications.put(Fixtures.scheduledMedication().copy(times = listOf(AFTERNOON.toLocalTime())))
        moveTo(NOW)
    }

    fun momentOf(local: LocalDateTime): Instant = local.atZone(clock.zone()).toInstant()

    fun moveTo(local: LocalDateTime) {
        clock.advanceTo(momentOf(local))
    }

    fun flyTo(zone: ZoneId) {
        clock.moveTo(zone)
    }

    /** Ожидающийся приём на указанное местное время. */
    fun pending(
        at: LocalDateTime = AFTERNOON,
        id: String = Fixtures.INTAKE_ID,
        medicationId: String = Fixtures.MEDICATION_ID,
    ): Intake {
        val intake = Fixtures.pendingIntake().copy(id = id, scheduledAt = at, medicationId = medicationId)
        intakes.put(intake)
        return intake
    }

    /** Второе лекарство того же профиля на то же время — общая карточка (НАП-36). */
    fun secondMedication(at: LocalDateTime = AFTERNOON): Intake {
        medications.put(
            Fixtures.scheduledMedication().copy(
                id = SECOND_MEDICATION_ID,
                name = "vitamin",
                times = listOf(at.toLocalTime()),
            ),
        )
        return pending(at = at, id = SECOND_INTAKE_ID, medicationId = SECOND_MEDICATION_ID)
    }

    /** Лекарство чужого профиля; дублирование напоминаний задаётся отдельно (ПРФ-6). */
    fun foreignMedication(mirrorReminders: Boolean, at: LocalDateTime = AFTERNOON): Intake {
        profiles.put(
            Fixtures.profile(name = "mother", isSelf = false)
                .copy(id = Fixtures.OTHER_PROFILE_ID, mirrorReminders = mirrorReminders),
        )
        medications.put(
            Fixtures.scheduledMedication().copy(
                id = FOREIGN_MEDICATION_ID,
                profileId = Fixtures.OTHER_PROFILE_ID,
                times = listOf(at.toLocalTime()),
            ),
        )
        return pending(at = at, id = FOREIGN_INTAKE_ID, medicationId = FOREIGN_MEDICATION_ID)
    }

    fun archive(medicationId: String = Fixtures.MEDICATION_ID) {
        medications.stored[medicationId]?.let {
            medications.put(it.copy(archivedAt = clock.now(), archiveReason = ArchiveReason.COURSE_FINISHED))
        }
    }

    fun unarchive(medicationId: String = Fixtures.MEDICATION_ID) {
        medications.stored[medicationId]?.let { medications.put(it.copy(archivedAt = null, archiveReason = null)) }
    }

    fun usePrn() {
        medications.put(Fixtures.prnMedication())
    }

    fun medication(id: String = Fixtures.MEDICATION_ID): Medication = requireNotNull(medications.stored[id])

    fun stored(id: String = Fixtures.INTAKE_ID): Intake = requireNotNull(intakes.stored[id])

    suspend fun take(id: String = Fixtures.INTAKE_ID) =
        mark(MarkRequest(intakeId = id, result = MarkResult.TAKEN, source = IntakeSource.PHONE))

    suspend fun takeOnWatch(id: String = Fixtures.INTAKE_ID, at: Instant) =
        mark(MarkRequest(intakeId = id, result = MarkResult.TAKEN, source = IntakeSource.WATCH, actedAt = at))

    suspend fun skip(id: String = Fixtures.INTAKE_ID) =
        mark(MarkRequest(intakeId = id, result = MarkResult.SKIPPED, source = IntakeSource.PHONE))

    companion object {
        val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

        /** «Сейчас» из предыстории приёмки: 25 сентября 2026, 09:00. */
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 25, 9, 0)

        /** Приём «Магний B6» в 14:00. */
        val AFTERNOON: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0)

        const val SECOND_MEDICATION_ID = "55555555-5555-4555-8555-555555555555"
        const val SECOND_INTAKE_ID = "66666666-6666-4666-8666-666666666666"
        const val FOREIGN_MEDICATION_ID = "77777777-7777-4777-8777-777777777777"
        const val FOREIGN_INTAKE_ID = "88888888-8888-4888-8888-888888888888"
    }
}
