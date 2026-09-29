package ru.stohastika.sklyanka.domain.schedule

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Сценарий генерации целиком: расписание, хранилище, журнал.
 *
 * Хранилище — в памяти: транзакционность и уникальность проверяет инструментальный тест
 * `core-data` на реальной базе, здесь проверяется правило.
 */
@DisplayName("Planning turns schedules into intakes and keeps doing it idempotently")
class PlanIntakesUseCaseTest {

    private val clock = Planning.clock()
    private val profiles = FakeProfileRepository()
    private val medications = FakeMedicationRepository(clock)
    private val intakes = FakeIntakeRepository(clock)
    private val log = RecordingOperationLog()

    private val plan = PlanIntakesUseCase(
        profiles = profiles,
        medications = medications,
        intakes = intakes,
        time = clock,
        log = log,
        policy = Planning.POLICY,
    )

    init {
        profiles.put(profile(Planning.OWNER_ID, isSelf = true))
        profiles.put(profile(Planning.RELATIVE_ID, isSelf = false))
    }

    private fun profile(id: String, isSelf: Boolean) = Profile(
        id = id,
        name = if (isSelf) "owner" else "relative",
        color = 0,
        isSelf = isSelf,
        mirrorReminders = !isSelf,
    )

    private fun pendingOf(medicationId: String = Planning.MEDICATION_ID): List<Intake> =
        intakes.of(medicationId).filter { it.isPending }

    private fun datesOf(medicationId: String = Planning.MEDICATION_ID): List<LocalDate> =
        pendingOf(medicationId).map { it.scheduledAt.toLocalDate() }

    // --- Happy path -----------------------------------------------------------------------

    @Test
    fun `a three times a day course over a week becomes twenty one intakes`() = runTest {
        medications.put(
            Planning.medication(
                times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
                endDate = Planning.WINDOW_END,
            ),
        )

        val result = plan(PlanScope.AllProfiles, PlanReason.APP_START)

        assertEquals(21, result.created)
        assertEquals(21, pendingOf().size)
        assertEquals(Planning.DEPTH_DAYS, datesOf().distinct().size)
        datesOf().groupingBy { it }.eachCount().forEach { (date, count) -> assertEquals(3, count, "date $date") }
    }

    @Test
    fun `an open ended course fills exactly the window`() = runTest {
        medications.put(Planning.medication(times = listOf(Planning.MORNING, Planning.EVENING)))

        plan()

        assertEquals(14, pendingOf().size)
        assertEquals(Planning.WINDOW_END.atTime(Planning.EVENING), pendingOf().last().scheduledAt)
        assertTrue(pendingOf().none { it.scheduledAt.toLocalDate() > Planning.WINDOW_END })
    }

    // --- Идемпотентность --------------------------------------------------------------------

    @Test
    fun `planning twice does not double the intakes`() = runTest {
        medications.put(
            Planning.medication(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END),
        )
        plan()
        val afterFirst = intakes.stored.keys.toSet()

        val second = plan()

        assertEquals(14, pendingOf().size)
        assertEquals(afterFirst, intakes.stored.keys.toSet())
        assertTrue(second.changedNothing)
    }

    @Test
    fun `three runs in a row leave the same seven intakes and open no transaction twice`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))

        repeat(3) { plan() }

        assertEquals(7, pendingOf().size)
        // Пустая разница не открывает транзакцию: второй и третий запуски ничего не пишут.
        assertEquals(1, intakes.applyCalls)
    }

    @Test
    fun `planning does not resurrect a marked intake`() = runTest {
        val at = Planning.TODAY.atTime(LocalTime.of(9, 0))
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))
        intakes.put(Planning.taken(at, id = "taken"))

        plan()

        assertEquals(1, intakes.of(Planning.MEDICATION_ID).count { it.scheduledAt == at })
        assertEquals(IntakeStatus.TAKEN, intakes.stored.getValue("taken").status)
    }

    // --- Окно пополняется ---------------------------------------------------------------------

    @Test
    fun `the next day the window gains a date and yesterday is left alone`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))
        plan()
        val first = intakes.of(Planning.MEDICATION_ID).first()

        clock.setLocal(Planning.TODAY.plusDays(1).atTime(3, 0))
        plan()

        assertNotNull(intakes.stored[first.id])
        assertEquals(first, intakes.stored[first.id])
        assertEquals(8, intakes.of(Planning.MEDICATION_ID).size)
        assertTrue(pendingOf().any { it.scheduledAt == Planning.WINDOW_END.plusDays(1).atTime(9, 0) })
    }

    @Test
    fun `turning the clock back deletes nothing`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))
        plan()
        val before = intakes.stored.toMap()

        clock.setLocal(Planning.TODAY.minusDays(1).atTime(7, 0))
        val result = plan()

        assertEquals(before, intakes.stored)
        assertEquals(0, result.deleted)
    }

    @Test
    fun `changing the time zone neither adds nor removes intakes`() = runTest {
        clock.setLocal(Planning.TODAY.atTime(12, 0))
        medications.put(
            Planning.medication(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END),
        )
        plan()
        val before = intakes.stored.toMap()

        clock.moveTo(java.time.ZoneId.of("+06:00"))
        val result = plan()

        assertEquals(before, intakes.stored)
        assertTrue(result.changedNothing)
        val scheduleTimes = setOf(Planning.MORNING, Planning.EVENING)
        assertTrue(intakes.stored.values.all { it.scheduledAt.toLocalTime() in scheduleTimes })
    }

    // --- Правка расписания ---------------------------------------------------------------------

    @Test
    fun `moving the morning intake keeps the evening records and their identifiers`() = runTest {
        val medication = Planning.medication(times = listOf(Planning.MORNING, Planning.EVENING))
        medications.put(medication)
        plan()
        val eveningBefore = intakes.of(medication.id).filter { it.scheduledAt.toLocalTime() == Planning.EVENING }

        medications.put(medication.copy(times = listOf(LocalTime.of(9, 0), Planning.EVENING)))
        plan(PlanScope.Medication(medication.id), PlanReason.MEDICATION_SAVED)

        val eveningAfter = intakes.of(medication.id).filter { it.scheduledAt.toLocalTime() == Planning.EVENING }
        assertEquals(eveningBefore, eveningAfter)
        assertTrue(intakes.of(medication.id).none { it.scheduledAt.toLocalTime() == Planning.MORNING })
        assertEquals(7, intakes.of(medication.id).count { it.scheduledAt.toLocalTime() == LocalTime.of(9, 0) })
    }

    @Test
    fun `shortening the course removes the intakes that fell out of it`() = runTest {
        val medication = Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.WINDOW_END)
        medications.put(medication)
        plan()

        medications.put(medication.copy(endDate = Planning.TODAY.plusDays(2)))
        val result = plan(PlanScope.Medication(medication.id), PlanReason.MEDICATION_SAVED)

        assertEquals(3, pendingOf().size)
        assertEquals(4, result.deleted)
        assertTrue(pendingOf().none { it.scheduledAt.toLocalDate() > Planning.TODAY.plusDays(2) })
    }

    @Test
    fun `dropping the end date extends the course within the window`() = runTest {
        val medication = Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.TODAY.plusDays(2))
        medications.put(medication)
        plan()

        medications.put(medication.copy(endDate = null))
        plan(PlanScope.Medication(medication.id), PlanReason.MEDICATION_SAVED)

        assertEquals(7, pendingOf().size)
        assertEquals(Planning.WINDOW_END.atTime(9, 0), pendingOf().last().scheduledAt)
    }

    // --- Режимы, архив, профили -------------------------------------------------------------------

    @Test
    fun `a prn medication produces nothing and does not disturb its neighbours`() = runTest {
        medications.put(Planning.medication(id = Planning.OTHER_MEDICATION_ID, mode = ScheduleMode.PRN))
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.WINDOW_END))

        plan()

        assertTrue(intakes.of(Planning.OTHER_MEDICATION_ID).isEmpty())
        assertEquals(7, pendingOf().size)
    }

    @Test
    fun `an archived medication is not planned by a full pass`() = runTest {
        medications.put(
            Planning.medication(
                times = listOf(LocalTime.of(9, 0)),
                startDate = Planning.TODAY.minusDays(5),
                archivedAt = Planning.TODAY.minusDays(1).atTime(10, 0).atZone(Planning.ZONE).toInstant(),
            ),
        )

        plan()

        assertTrue(intakes.stored.isEmpty())
    }

    @Test
    fun `archiving sweeps the future pending intakes and keeps the marked history`() = runTest {
        val medication = Planning.medication(
            times = listOf(LocalTime.of(9, 0)),
            startDate = Planning.TODAY.minusDays(5),
        )
        medications.put(medication)
        plan()
        val history = Planning.taken(Planning.TODAY.minusDays(1).atTime(9, 0), id = "history")
        intakes.put(history)

        medications.archive(medication.id, ArchiveReason.MANUAL)
        val result = plan(PlanScope.Medication(medication.id), PlanReason.MEDICATION_ARCHIVED)

        assertEquals(7, result.deleted)
        assertTrue(pendingOf().isEmpty())
        assertEquals(history, intakes.stored["history"])
    }

    /**
     * Возврат из архива в 10:00 восстанавливает и сегодняшний приём 09:00: курс начался
     * пять дней назад, правило дня начала курса к нему не применяется, а отметить его
     * человек успеет до конца суток (ОВ-4).
     */
    @Test
    fun `returning from the archive restores the whole window including today`() = runTest {
        val medication = Planning.medication(
            times = listOf(LocalTime.of(9, 0)),
            startDate = Planning.TODAY.minusDays(5),
            archivedAt = Planning.TODAY.minusDays(1).atTime(10, 0).atZone(Planning.ZONE).toInstant(),
        )
        medications.put(medication)
        clock.setLocal(Planning.TODAY.atTime(10, 0))

        medications.put(medication.copy(archivedAt = null, archiveReason = null))
        plan()

        assertEquals(7, pendingOf().size)
        assertEquals(Planning.TODAY.atTime(9, 0), pendingOf().first().scheduledAt)
    }

    /** ПР-10, ПРФ-8: приёмы подопечного рождаются, пока на экране открыт профиль владельца. */
    @Test
    fun `a full pass covers every profile and keeps their intakes apart`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.WINDOW_END))
        medications.put(
            Planning.medication(
                id = Planning.OTHER_MEDICATION_ID,
                profileId = Planning.RELATIVE_ID,
                times = listOf(Planning.MORNING, Planning.EVENING),
                endDate = Planning.WINDOW_END,
            ),
        )

        plan(PlanScope.AllProfiles, PlanReason.BOOT)

        assertEquals(7, pendingOf().size)
        assertEquals(14, pendingOf(Planning.OTHER_MEDICATION_ID).size)
        assertTrue(intakes.of(Planning.MEDICATION_ID).none { it.medicationId == Planning.OTHER_MEDICATION_ID })
    }

    @Test
    fun `a profile scoped pass leaves the other profile alone`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))
        medications.put(
            Planning.medication(
                id = Planning.OTHER_MEDICATION_ID,
                profileId = Planning.RELATIVE_ID,
                times = listOf(Planning.MORNING),
            ),
        )

        plan(PlanScope.Profile(Planning.OWNER_ID), PlanReason.MANUAL_CHECK)

        assertEquals(7, pendingOf().size)
        assertTrue(intakes.of(Planning.OTHER_MEDICATION_ID).isEmpty())
    }

    // --- Журнал ------------------------------------------------------------------------------------

    @Test
    fun `generation leaves a trace with identifiers and the reason but no medical data`() = runTest {
        val medication = Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.WINDOW_END)
        medications.put(medication)

        plan(PlanScope.AllProfiles, PlanReason.APP_START)

        val record = log.records.single()
        assertEquals(OpEvent.INTAKES_GENERATED, record.event)
        assertEquals(medication.id, record.medicationId)
        assertEquals(medication.profileId, record.profileId)
        assertEquals("7", record.after)
        assertEquals(PlanReason.APP_START.code, record.detail)
        assertTrue(log.records.none { it.toString().contains(medication.name) })
    }

    @Test
    fun `sweeping intakes is logged as its own event`() = runTest {
        val medication = Planning.medication(times = listOf(LocalTime.of(9, 0)))
        medications.put(medication)
        plan()
        log.records.clear()

        medications.put(medication.copy(times = listOf(LocalTime.of(10, 0))))
        plan(PlanScope.Medication(medication.id), PlanReason.MEDICATION_SAVED)

        assertTrue(OpEvent.INTAKES_REVOKED in log.events())
        assertTrue(OpEvent.INTAKES_GENERATED in log.events())
        assertEquals(
            PlanReason.MEDICATION_SAVED.code,
            log.records.first { it.event == OpEvent.INTAKES_REVOKED }.detail,
        )
    }

    @Test
    fun `a run that changes nothing writes nothing to the journal`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0))))
        plan()
        log.records.clear()

        plan()

        assertTrue(log.records.isEmpty())
    }

    @Test
    fun `the result reports what happened to every medication of the run`() = runTest {
        medications.put(Planning.medication(times = listOf(LocalTime.of(9, 0)), endDate = Planning.WINDOW_END))

        val first = plan()
        val second = plan()

        assertEquals(1, first.medications.size)
        assertEquals(7, first.medications.single().created)
        assertEquals(Planning.TODAY, first.window.from)
        assertEquals(Planning.WINDOW_END, first.window.toInclusive)
        assertEquals(7, second.unchanged)
        assertFalse(first.changedNothing)
    }
}
