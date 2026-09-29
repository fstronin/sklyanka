package ru.stohastika.sklyanka.data.room.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.data.DataFixtures
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal

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
}
