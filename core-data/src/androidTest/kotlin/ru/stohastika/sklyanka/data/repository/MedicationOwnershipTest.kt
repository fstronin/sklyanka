package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.InvariantViolationException
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Неизменяемость владельца лекарства на настоящей базе (SKL-107, ИНВ-3, ПР-10).
 *
 * `@Upsert` — единственный SQL продукта, пишущий `profile_id`: точечные обновления
 * (архив, удаление, остаток) колонку не трогают. Отказ обязан приходить до записи
 * и не трогать ни строку, ни историю её приёмов: смена владельца переложила бы
 * чужую историю между профилями.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MedicationOwnershipTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val medications by lazy { MedicationRepositoryImpl(db.medications, clock, log) }

    @Before
    fun fillProfilesAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
    }

    @Test
    fun savingAnExistingMedicationIntoAnotherProfileIsRefusedAndTheRowStaysPut() = runTest {
        assertRefused<InvariantViolationException> {
            medications.save(model(id = TestData.OWNER_MEDICATION_ID, profileId = TestData.RELATIVE_ID))
        }

        val stored = db.medications.getById(TestData.OWNER_MEDICATION_ID)
        assertEquals(
            "the refused takeover must not move the owner",
            TestData.OWNER_ID,
            stored?.profileId,
        )
    }

    @Test
    fun aFreshMedicationIsSavedIntoTheProfileItIsCreatedIn() = runTest {
        medications.save(model(id = FRESH_ID, profileId = TestData.RELATIVE_ID))

        assertNotNull(db.medications.getById(FRESH_ID))
        assertEquals(TestData.RELATIVE_ID, db.medications.getById(FRESH_ID)?.profileId)
    }

    @Test
    fun anEditInsideTheSameProfilePassesFreely() = runTest {
        medications.save(model(id = TestData.OWNER_MEDICATION_ID, profileId = TestData.OWNER_ID).copy(name = "med-b"))

        assertEquals("med-b", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.name)
    }

    private fun model(id: String, profileId: String) = Medication(
        id = id,
        profileId = profileId,
        name = "med-a",
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal("1"),
        doseUnit = "unit",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(8, 0)),
        startDate = LocalDate.of(2026, 9, 1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private companion object {
        val MOMENT: Instant = Instant.parse("2026-09-25T06:00:00Z")
        const val FRESH_ID = "66666666-6666-4666-8666-666666666666"
    }
}
