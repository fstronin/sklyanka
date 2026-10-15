package ru.stohastika.sklyanka.data.room.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.data.DataFixtures
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import java.math.BigDecimal
import java.time.DayOfWeek

/**
 * Пара «модель → строка → модель».
 *
 * Два слоя моделей существуют не ради чистоты: сущность несёт компромиссы хранения,
 * доменная модель — смысл. Цена разделения — мапперы, и она оплачивается этим тестом.
 */
class MapperTest {

    @Test
    fun profileSurvivesTheRoundTrip() {
        val profile = DataFixtures.ownerProfile()

        assertEquals(profile, profile.toEntity().toDomain())
    }

    @Test
    fun theOwnerProfileOccupiesTheSelfSlot() {
        assertEquals(ProfileEntity.SELF_SLOT, DataFixtures.ownerProfile().toEntity().selfSlot)
    }

    @Test
    fun anotherProfileLeavesTheSelfSlotEmpty() {
        assertNull(DataFixtures.otherProfile().toEntity().selfSlot)
    }

    @Test
    fun scheduledMedicationSurvivesTheRoundTrip() {
        val medication = DataFixtures.scheduledMedication()

        val decoded = medication.toEntity().decode()

        assertEquals(Decoded.Value(medication), decoded)
    }

    @Test
    fun anOpenEndedCourseKeepsItsEmptyEndDate() {
        val medication = DataFixtures.scheduledMedication().copy(endDate = null)

        val entity = medication.toEntity()

        assertNull(entity.endDate)
        assertEquals(Decoded.Value(medication), entity.decode())
    }

    @Test
    fun thePackageSizeSurvivesTheRoundTrip() {
        val medication = DataFixtures.scheduledMedication().copy(packageSize = BigDecimal("60"))

        val entity = medication.toEntity()

        assertEquals("60", entity.packageSize)
        assertEquals(Decoded.Value(medication), entity.decode())
    }

    /** Старая строка без размера упаковки остаётся «размер не задан», а не нулём. */
    @Test
    fun anUnknownPackageSizeStaysUnknown() {
        val medication = DataFixtures.scheduledMedication().copy(packageSize = null)

        val entity = medication.toEntity()

        assertNull(entity.packageSize)
        assertEquals(Decoded.Value(medication), entity.decode())
    }

    @Test
    fun prnMedicationStoresNoTimes() {
        val medication = DataFixtures.prnMedication()

        val entity = medication.toEntity()

        assertEquals("", entity.times)
        assertEquals(Decoded.Value(medication), entity.decode())
    }

    @Test
    fun anUnknownFormStopsTheRowWithoutThrowing() {
        val entity = DataFixtures.scheduledMedication().toEntity().copy(form = "POWDER")

        assertEquals(Decoded.UnknownEnum("form"), entity.decode())
    }

    @Test
    fun anUnknownShapeStopsTheRowWithoutThrowing() {
        val entity = DataFixtures.scheduledMedication().toEntity().copy(shape = "HEXAGONAL")

        assertEquals(Decoded.UnknownEnum("shape"), entity.decode())
    }

    @Test
    fun aKnownValueKeepsItsMeaningWhenTheEnumGrows() {
        // Значение хранится именем константы, а не номером: добавление новой формы
        // в середину перечисления не превращает капсулу в спрей (МГ-6).
        val entity = DataFixtures.capsuleMedication().toEntity()

        assertEquals("CAPSULE", entity.form)
        assertTrue(entity.decode() is Decoded.Value)
    }

    @Test
    fun aScheduledIntakeOccupiesItsSlot() {
        val intake = DataFixtures.pendingIntake()

        val entity = intake.toEntity(ScheduleMode.SCHEDULED)

        assertEquals(IntakeEntity.SCHEDULE_SLOT, entity.scheduleSlot)
        assertEquals(Decoded.Value(intake), entity.decode())
    }

    @Test
    fun aPrnIntakeOccupiesNoSlot() {
        val intake = DataFixtures.takenIntake()

        val entity = intake.toEntity(ScheduleMode.PRN)

        assertNull(entity.scheduleSlot)
        assertEquals(Decoded.Value(intake), entity.decode())
    }

    @Test
    fun anUnknownStatusStopsTheRowWithoutThrowing() {
        val entity = DataFixtures.pendingIntake().toEntity(ScheduleMode.SCHEDULED).copy(status = "POSTPONED")

        assertEquals(Decoded.UnknownEnum("status"), entity.decode())
    }

    @Test
    fun anUnknownSourceStopsTheRowWithoutThrowing() {
        val entity = DataFixtures.takenIntake().toEntity(ScheduleMode.SCHEDULED).copy(source = "TABLET_PC")

        assertEquals(Decoded.UnknownEnum("source"), entity.decode())
    }

    // --- Вид расписания и снимок дозы (SKL-117, ПР-15, ПР-19) ------------------------------

    @Test
    fun everyScheduleKindSurvivesTheRoundTrip() {
        everyScheduleKind().forEach { rule ->
            val medication = DataFixtures.scheduledMedication().copy(schedule = rule)

            val entity = medication.toEntity()

            assertTrue("rule $rule", entity.decode() is Decoded.Value)
            assertEquals("rule $rule", Decoded.Value(medication), entity.decode())
        }
    }

    /** Старая строка без колонки вида читается как ежедневное расписание. */
    @Test
    fun aRowWithoutAScheduleRuleReadsAsADailyCourse() {
        val medication = DataFixtures.scheduledMedication()
        val entity = medication.toEntity().copy(scheduleRule = null)

        assertNull(entity.scheduleRule)
        assertEquals(Decoded.Value(medication.copy(schedule = ScheduleRule.Daily)), entity.decode())
    }

    /** Строка, написанная более новой сборкой, не роняет чтение курса. */
    @Test
    fun anUnknownScheduleRuleTextReadsAsADailyCourse() {
        val entity = DataFixtures.scheduledMedication().toEntity().copy(scheduleRule = "hourly:3")

        assertEquals(
            Decoded.Value(DataFixtures.scheduledMedication().copy(schedule = ScheduleRule.Daily)),
            entity.decode(),
        )
    }

    @Test
    fun aStagedIntakeKeepsItsDoseSnapshot() {
        val intake = DataFixtures.pendingIntake().copy(doseAmount = BigDecimal("0.5"))

        val entity = intake.toEntity(ScheduleMode.SCHEDULED)

        assertEquals("0.5", entity.doseAmount)
        assertEquals(Decoded.Value(intake), entity.decode())
    }

    /** Приём обычного курса снимка не несёт: доза берётся у лекарства. */
    @Test
    fun anIntakeWithoutASnapshotStaysWithoutOne() {
        val intake = DataFixtures.pendingIntake()

        val entity = intake.toEntity(ScheduleMode.SCHEDULED)

        assertNull(entity.doseAmount)
        assertEquals(Decoded.Value(intake), entity.decode())
    }

    private companion object {
        @JvmStatic
        fun everyScheduleKind(): List<ScheduleRule> = listOf(
            ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY)),
            ScheduleRule.Weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            ScheduleRule.EveryNDays(days = 2),
            ScheduleRule.Cycles(onDays = 21, offDays = 7),
            ScheduleRule.Staged(listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 24))),
        )
    }
}
