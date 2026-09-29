package ru.stohastika.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.domain.schedule.Planning
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Сохранение формы целиком: проверка, запись курса, порождение приёмов, след в журнале.
 *
 * Часы стоят на 25 сентября 2026, 10:00 — как в `acceptance.feature`: приём на 08:00
 * дня начала курса к этому моменту уже прошёл и порождён быть не должен (SKL-06).
 */
@DisplayName("Saving a course writes it, plans its intakes and leaves a trace")
class SaveMedicationUseCaseTest {

    private val clock = Planning.clock(at = Drafts.TODAY.atTime(10, 0))
    private val profiles = FakeProfileRepository()
    private val medications = FakeMedicationRepository(clock)
    private val intakes = FakeIntakeRepository(clock)
    private val log = RecordingOperationLog()
    private val transactor = CountingTransactor()

    /** Причины, с которыми форма звала порт перепланирования. */
    private val planReasons = mutableListOf<Pair<String, PlanReason>>()

    private val save = SaveMedicationUseCase(
        validate = ValidateMedicationDraft(),
        medications = medications,
        planner = MedicationPlanner { medicationId, reason ->
            planReasons += medicationId to reason
            PlanIntakesUseCase(
                profiles = profiles,
                medications = medications,
                intakes = intakes,
                time = clock,
                log = log,
                policy = Planning.POLICY,
            )(PlanScope.Medication(medicationId), reason)
        },
        transactor = transactor,
        log = log,
        newId = { Drafts.NEW_ID },
    )

    // --- Успешное сохранение --------------------------------------------------------------

    @Test
    fun `a saved schedule immediately produces the intakes of the window`() = runTest {
        val result = save(Drafts.valid())

        assertTrue(result is SaveResult.Saved)
        val saved = result as SaveResult.Saved
        assertEquals(Drafts.NEW_ID, saved.medicationId)
        // Семь суток по два приёма минус утренний приём дня начала курса: он уже прошёл.
        assertEquals(13, saved.createdIntakes)
        assertEquals(13, intakes.of(Drafts.NEW_ID).size)
    }

    @Test
    fun `the intake whose time has already passed on the first day is not born`() = runTest {
        save(Drafts.valid())

        val today = intakes.of(Drafts.NEW_ID).filter { it.scheduledAt.toLocalDate() == Drafts.TODAY }

        assertEquals(listOf(Drafts.EVENING), today.map { it.scheduledAt.toLocalTime() })
    }

    @Test
    fun `the dose unit is derived from the release form and not asked of the person`() = runTest {
        save(Drafts.valid().copy(form = MedicationForm.SYRUP))

        assertEquals(DoseUnit.MILLILITRE.code, medications.stored.getValue(Drafts.NEW_ID).doseUnit)
    }

    @Test
    fun `the name is stored trimmed`() = runTest {
        save(Drafts.valid().copy(name = "  med-a  "))

        assertEquals("med-a", medications.stored.getValue(Drafts.NEW_ID).name)
    }

    @Test
    fun `a course belongs to the profile of its draft`() = runTest {
        save(Drafts.valid())

        assertEquals(Drafts.PROFILE_ID, medications.stored.getValue(Drafts.NEW_ID).profileId)
    }

    @Test
    fun `the write happens inside a transaction`() = runTest {
        save(Drafts.valid())

        assertEquals(1, transactor.calls)
    }

    // --- «По потребности» -----------------------------------------------------------------

    @Test
    fun `an as needed course produces no pending intake at all`() = runTest {
        val result = save(Drafts.validPrn())

        assertTrue(result is SaveResult.Saved)
        assertEquals(0, (result as SaveResult.Saved).createdIntakes)
        assertTrue(intakes.stored.isEmpty())
    }

    // --- Отказ ----------------------------------------------------------------------------

    @Test
    fun `a broken draft is refused and nothing is written`() = runTest {
        val result = save(Drafts.valid().copy(name = " "))

        assertEquals(SaveResult.Rejected(listOf(DraftViolation.NameEmpty)), result)
        assertTrue(medications.stored.isEmpty())
        assertTrue(intakes.stored.isEmpty())
        assertEquals(0, transactor.calls)
    }

    @Test
    fun `a refusal leaves a trace naming the broken rules and no values`() = runTest {
        save(Drafts.valid().copy(name = " ", stockLeft = BigDecimal("-1")))

        val record = log.records.single { it.event == OpEvent.MEDICATION_SAVE_REJECTED }

        assertEquals("NAME,STOCK", record.detail)
        assertNull(record.after)
    }

    // --- Сброс неприменимого при сохранении -------------------------------------------------

    @Test
    fun `a tablet shape chosen before switching to a spray does not reach the stored course`() = runTest {
        save(Drafts.valid().copy(shape = TabletShape.OVAL, form = MedicationForm.SPRAY))

        assertNull(medications.stored.getValue(Drafts.NEW_ID).shape)
    }

    @Test
    fun `switching to as needed drops the times that were filled in before`() = runTest {
        save(
            Drafts.valid().copy(
                scheduleMode = ScheduleMode.PRN,
                maxPerDay = 3,
                times = listOf(Drafts.MORNING, Drafts.EVENING),
            ),
        )

        assertEquals(emptyList<LocalTime>(), medications.stored.getValue(Drafts.NEW_ID).times)
    }

    // --- Правка ---------------------------------------------------------------------------

    @Test
    fun `editing a course keeps its identifier and replans its intakes`() = runTest {
        save(Drafts.valid())
        val before = intakes.of(Drafts.NEW_ID).map { it.scheduledAt.toLocalTime() }.distinct().sorted()

        val edited = MedicationDraft.of(medications.stored.getValue(Drafts.NEW_ID))
            .copy(times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        val result = save(edited)

        assertTrue(result is SaveResult.Saved)
        assertEquals(Drafts.NEW_ID, (result as SaveResult.Saved).medicationId)
        assertEquals(listOf(Drafts.MORNING, Drafts.EVENING), before)
        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            intakes.of(Drafts.NEW_ID).map { it.scheduledAt.toLocalTime() }.distinct().sorted(),
        )
    }

    @Test
    fun `editing logs the names of the changed fields and never their values`() = runTest {
        save(Drafts.valid())
        val stored = medications.stored.getValue(Drafts.NEW_ID)

        save(MedicationDraft.of(stored).copy(name = "med-b", doseAmount = BigDecimal("3")))

        val record = log.records.single { it.event == OpEvent.MEDICATION_UPDATED }
        assertEquals("name,dose_amount", record.after)
        // Доза — часть расписания по таблице НАП-11, поэтому правка помечается как
        // изменение расписания, а значения полей в журнал по-прежнему не попадают.
        assertEquals("schedule_changed", record.detail)
        assertFalse(log.records.any { it.after?.contains("med-b") == true })
    }

    @Test
    fun `changing the schedule is marked as such in the log`() = runTest {
        save(Drafts.valid())
        val stored = medications.stored.getValue(Drafts.NEW_ID)

        save(MedicationDraft.of(stored).copy(times = listOf(LocalTime.of(9, 0))))

        assertEquals("schedule_changed", log.records.single { it.event == OpEvent.MEDICATION_UPDATED }.detail)
    }

    // --- Причина перепланирования ------------------------------------------------------------

    @Test
    fun `creating a course plans with the added reason, editing the schedule with the saved one`() = runTest {
        save(Drafts.valid())
        val stored = medications.stored.getValue(Drafts.NEW_ID)
        save(MedicationDraft.of(stored).copy(times = listOf(LocalTime.of(9, 0))))

        assertEquals(
            listOf(
                Drafts.NEW_ID to PlanReason.MEDICATION_ADDED,
                Drafts.NEW_ID to PlanReason.MEDICATION_SAVED,
            ),
            planReasons,
        )
    }

    @Test
    fun `editing a name only does not replan since the schedule has not changed`() = runTest {
        save(Drafts.valid())
        val stored = medications.stored.getValue(Drafts.NEW_ID)

        save(MedicationDraft.of(stored).copy(name = "med-b"))

        assertEquals(listOf(Drafts.NEW_ID to PlanReason.MEDICATION_ADDED), planReasons)
    }

    @Test
    fun `a saved package size is stored and its change is logged by name only`() = runTest {
        save(Drafts.valid().copy(packageSize = BigDecimal("60")))
        assertEquals(BigDecimal("60"), medications.stored.getValue(Drafts.NEW_ID).packageSize)

        val stored = medications.stored.getValue(Drafts.NEW_ID)
        save(MedicationDraft.of(stored).copy(packageSize = BigDecimal("90")))

        val record = log.records.single { it.event == OpEvent.MEDICATION_UPDATED }
        assertEquals("package_size", record.after)
        // Размер упаковки не входит в расписание: состав приёмов он не меняет (НАП-11).
        assertNull(record.detail)
        assertEquals(BigDecimal("90"), medications.stored.getValue(Drafts.NEW_ID).packageSize)
    }

    @Test
    fun `editing a package size only does not replan`() = runTest {
        save(Drafts.valid())
        val stored = medications.stored.getValue(Drafts.NEW_ID)

        save(MedicationDraft.of(stored).copy(packageSize = BigDecimal("60")))

        assertEquals(listOf(Drafts.NEW_ID to PlanReason.MEDICATION_ADDED), planReasons)
    }

    @Test
    fun `a rejected save never touches the planner`() = runTest {
        save(Drafts.valid().copy(name = ""))

        assertTrue(planReasons.isEmpty())
        assertTrue(intakes.stored.isEmpty())
    }

    @Test
    fun `the planner runs after the course is written, never before`() = runTest {
        // Порядок «запись, потом перепланирование» принципиален (НФТ-6): планировщик,
        // увидевший хранилище до записи, снял бы приёмы курса, который ещё не появился.
        var seenAtCall: String? = null
        val ordered = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = medications,
            planner = MedicationPlanner { medicationId, reason ->
                seenAtCall = medications.stored[medicationId]?.name
                PlanResult(window = GenerationWindow.of(Drafts.TODAY, 7), reason = reason, medications = emptyList())
            },
            transactor = transactor,
            log = log,
            newId = { Drafts.NEW_ID },
        )

        ordered(Drafts.valid())

        assertEquals("med-a", seenAtCall)
    }

    @Test
    fun `an edited draft carries the profile of the stored course and cannot change it`() = runTest {
        save(Drafts.valid())

        val draft = MedicationDraft.of(medications.stored.getValue(Drafts.NEW_ID))

        assertEquals(Drafts.PROFILE_ID, draft.profileId)
        assertEquals(Drafts.NEW_ID, draft.id)
    }

    // --- Журнал создания --------------------------------------------------------------------

    @Test
    fun `creating a course leaves a trace with identifiers only`() = runTest {
        save(Drafts.valid())

        val record = log.records.single { it.event == OpEvent.MEDICATION_CREATED }

        assertEquals(Drafts.PROFILE_ID, record.profileId)
        assertEquals(Drafts.NEW_ID, record.medicationId)
        assertEquals(ScheduleMode.SCHEDULED.name, record.detail)
        assertNotNull(record.medicationId)
    }

    @Test
    fun `a one day course is saved as a course, not refused`() = runTest {
        val result = save(Drafts.valid().copy(endDate = Drafts.TODAY))

        assertTrue(result is SaveResult.Saved)
        assertEquals(Drafts.TODAY, medications.stored.getValue(Drafts.NEW_ID).endDate)
    }

    @Test
    fun `an open ended course keeps an empty end date`() = runTest {
        save(Drafts.valid().copy(endDate = null))

        assertNull(medications.stored.getValue(Drafts.NEW_ID).endDate)
    }

    @Test
    fun `a course started in the past still plans the whole window`() = runTest {
        save(Drafts.valid().copy(startDate = LocalDate.of(2026, 3, 12)))

        // Прошедшее время дня начала курса снимается только в сам день начала,
        // поэтому окно из семи суток порождает все четырнадцать приёмов.
        assertEquals(14, intakes.of(Drafts.NEW_ID).size)
    }

    /** Транзакция в памяти: считает обращения и выполняет тело как есть. */
    private class CountingTransactor : Transactor {

        var calls: Int = 0
            private set

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            calls++
            return block()
        }
    }
}
