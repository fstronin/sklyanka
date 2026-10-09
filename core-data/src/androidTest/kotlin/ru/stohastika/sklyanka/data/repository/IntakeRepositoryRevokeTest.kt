package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Снятие записи «по потребности» (Р-46, SKL-127): признак вместо удаления, доза возвращается,
 * а снятая запись перестаёт быть видимой продукту.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется, как и соседние инструментальные тесты. Невидимость проверяется
 * здесь потому, что её держит SQL выборок: в доменном прогоне приёмки повторено лишь
 * условие, а сам запрос — вот этот.
 */
@RunWith(AndroidJUnit4::class)
class IntakeRepositoryRevokeTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy {
        IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log)
    }

    @Before
    fun fillProfileAndOnDemandMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(
            TestData.medication(id = TestData.PRN_MEDICATION_ID, scheduleMode = ScheduleMode.PRN),
        )
        db.intakes.insertAll(listOf(TestData.prnIntake(id = INTAKE_ID, scheduledAt = MOMENT_IN_LOCAL)))
    }

    @Test
    fun revokingMarksTheRecordAndRestoresTheDoseOnce() = runTest {
        val outcome = intakes.undoAtomically(command(INTAKE_ID))

        assertEquals(UndoOutcome.Applied(undone = 1), outcome)
        val stored = db.intakes.getById(INTAKE_ID)
        // Статус не подменяется: снятие не переписывает решение человека (Р-46).
        assertEquals(IntakeStatus.TAKEN.name, stored?.status)
        assertEquals(SklyankaConverters.instantToMillis(MOMENT), stored?.revokedAt)
        assertEquals("30", db.medications.getById(TestData.PRN_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theSecondRevocationOfTheSameRecordReturnsNothing() = runTest {
        intakes.undoAtomically(command(INTAKE_ID))

        val second = intakes.undoAtomically(command(INTAKE_ID))

        assertEquals(UndoOutcome.Applied(undone = 0), second)
        assertEquals("30", db.medications.getById(TestData.PRN_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun aRevokedRecordIsInvisibleToTheDayThePeriodAndThePrnLimit() = runTest {
        intakes.undoAtomically(command(INTAKE_ID))

        val day = intakes.observeDay(TestData.OWNER_ID, MOMENT_IN_LOCAL.toLocalDate()).first()
        val period = intakes.observePeriod(
            TestData.OWNER_ID,
            MOMENT_IN_LOCAL.toLocalDate(),
            MOMENT_IN_LOCAL.toLocalDate(),
        ).first()
        val counted = db.intakes.countOnDay(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.PRN_MEDICATION_ID,
            dayStart = SklyankaConverters.dateTimeToText(MOMENT_IN_LOCAL.toLocalDate().atStartOfDay()),
            nextDayStart = SklyankaConverters.dateTimeToText(
                MOMENT_IN_LOCAL.toLocalDate().plusDays(1).atStartOfDay(),
            ),
        )

        assertTrue("the day must not show the revoked record: $day", day.isEmpty())
        assertTrue("the period must not show the revoked record: $period", period.isEmpty())
        assertEquals("the daily limit counts only visible records", 0, counted)
    }

    @Test
    fun aRevokedRecordIsNoMarkForTheDeletionRule() = runTest {
        intakes.undoAtomically(command(INTAKE_ID))

        val changed = db.medications.delete(TestData.PRN_MEDICATION_ID, MOMENT.toEpochMilli())

        // ИНВ-22 говорит про отметки «принято»: снятой записи для продукта нет (Р-46),
        // поэтому условное удаление проходит.
        assertEquals("the conditional delete must pass", 1, changed)
        val deleted = db.medications.getById(TestData.PRN_MEDICATION_ID)
        assertTrue("the medication must carry the moment of deletion", deleted?.deletedAt != null)
    }

    @Test
    fun theLogNamesTheRevocationWithIdentifiersAndNoMedicalData() = runTest {
        intakes.undoAtomically(command(INTAKE_ID))

        val record = log.recent(RECENT_LIMIT).last { it.event == OpEvent.INTAKE_REVOKED }
        assertEquals(INTAKE_ID, record.intakeId)
        assertEquals(TestData.PRN_MEDICATION_ID, record.medicationId)
        assertEquals(IntakeStatus.TAKEN.name, record.before)
        assertEquals(IntakeStatus.TAKEN.name, record.after)
        assertEquals(IntakeSource.PHONE.name, record.source)
        val written = listOfNotNull(
            record.profileId,
            record.medicationId,
            record.intakeId,
            record.before,
            record.after,
            record.source,
            record.detail,
        )
        assertTrue("the log must not carry medication names", written.none { it.contains("med-a") })
        assertTrue("the log must not carry profile names", written.none { it.contains("owner") })
    }

    private fun command(vararg intakeIds: String) = UndoCommand(
        intakeIds = intakeIds.toList(),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    private companion object {
        const val INTAKE_ID = "bbbbbbbb-0000-4000-8000-000000000001"
        const val RECENT_LIMIT = 50
        val MOMENT: Instant = Instant.parse("2026-09-25T05:05:00Z")
        val MOMENT_IN_LOCAL: LocalDateTime = LocalDate.of(2026, 9, 25).atTime(13, 20)
    }
}
