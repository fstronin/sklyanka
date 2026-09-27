package ru.heuri.sklyanka.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.log.RoomOperationLog
import ru.heuri.sklyanka.data.repository.HistoryRepositoryImpl
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.domain.history.HistoryWindow
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Выборка истории на настоящей базе: профиль, архив и границы периода (SKL-18).
 *
 * Остаётся проверкой устройства намеренно (`docs/tasks/SKL-24/TDD.md §5.2`): утверждения
 * здесь — про поведение настоящего движка базы, а не про код вокруг него. Сравнение
 * текстовых границ диапазона, `MIN` по индексу и соединение с таблицей лекарств на
 * имитации SQLite означали бы не то, что написано.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class HistoryQueriesTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(Instant.parse("2026-09-30T07:00:00Z"), ZoneId.of("Europe/Moscow"))
    private lateinit var history: HistoryRepositoryImpl

    @Before
    fun fillTwoProfiles() = runTest {
        history = HistoryRepositoryImpl(
            intakes = db.intakes,
            history = db.database.historyDao(),
            log = RoomOperationLog(db.opLog, clock),
        )

        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
        db.medications.upsert(
            TestData.medication(
                id = ARCHIVED_MEDICATION_ID,
                name = "med-archived",
                archivedAt = clock.now().toEpochMilli(),
                archiveReason = ArchiveReason.COURSE_FINISHED,
            ),
        )
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(
                    id = "own-september",
                    scheduledAt = LocalDateTime.of(2026, 9, 15, 8, 0),
                    status = IntakeStatus.TAKEN,
                    actedAt = 0L,
                    source = "PHONE",
                ),
                TestData.scheduledIntake(
                    id = "own-august",
                    scheduledAt = LocalDateTime.of(2026, 8, 31, 23, 30),
                    status = IntakeStatus.MISSED,
                ),
                TestData.scheduledIntake(
                    id = "own-october",
                    scheduledAt = LocalDateTime.of(2026, 10, 1, 0, 5),
                ),
                TestData.scheduledIntake(
                    id = "own-archived-course",
                    medicationId = ARCHIVED_MEDICATION_ID,
                    scheduledAt = LocalDateTime.of(2026, 9, 10, 8, 0),
                    status = IntakeStatus.TAKEN,
                    actedAt = 0L,
                    source = "PHONE",
                ),
                TestData.scheduledIntake(
                    id = "foreign-september",
                    medicationId = TestData.RELATIVE_MEDICATION_ID,
                    scheduledAt = LocalDateTime.of(2026, 9, 16, 8, 0),
                    status = IntakeStatus.TAKEN,
                    actedAt = 0L,
                    source = "PHONE",
                ),
            ),
        )
    }

    /** ПР-10: приёмы чужого профиля в выборку не попадают ни одной записью. */
    @Test
    fun theHistoryOfOneProfileNeverCarriesTheIntakesOfAnother() = runTest {
        val slice = history.observe(TestData.OWNER_ID, SEPTEMBER).first()

        assertEquals(listOf("own-archived-course", "own-september"), slice.intakes.map { it.id }.sorted())
        assertTrue(slice.medications.none { it.profileId == TestData.RELATIVE_ID })
    }

    /** ПР-11: архивированный курс входит в историю — ради этого архив и придуман. */
    @Test
    fun theSelectionIncludesArchivedCourses() = runTest {
        val slice = history.observe(TestData.OWNER_ID, SEPTEMBER).first()

        assertTrue(slice.medications.any { it.id == ARCHIVED_MEDICATION_ID && it.isArchived })
        assertTrue(slice.intakes.any { it.medicationId == ARCHIVED_MEDICATION_ID })
    }

    /**
     * Границы периода: приём 31 августа в 23:30 и приём 1 октября в 00:05 в сентябрь
     * не попадают.
     *
     * Это проверка верхней исключительной границы: она равна началу следующих суток,
     * а не «концу дня», иначе приём в 23:59:30 выпал бы из своего месяца.
     */
    @Test
    fun theNeighbouringMonthsStayOutOfTheWindow() = runTest {
        val slice = history.observe(TestData.OWNER_ID, SEPTEMBER).first()

        assertTrue(slice.intakes.none { it.id == "own-august" || it.id == "own-october" })
    }

    /** Дата первой записи истории профиля считается базой, а не обходом всей таблицы. */
    @Test
    fun theHistoryStartsAtTheEarliestRecordOfTheProfile() = runTest {
        val slice = history.observe(TestData.OWNER_ID, SEPTEMBER).first()

        assertEquals(LocalDate.of(2026, 8, 31), slice.historyStartsAt)
    }

    /** У профиля без единой записи истории нет — и дни календаря остаются нейтральными. */
    @Test
    fun aProfileWithoutRecordsHasNoHistoryStart() = runTest {
        db.intakes.deletePending(listOf("foreign-september"))
        val slice = history.observe("profile-without-records", SEPTEMBER).first()

        assertEquals(null, slice.historyStartsAt)
        assertTrue(slice.intakes.isEmpty())
        assertTrue(slice.medications.isEmpty())
    }

    private companion object {
        const val ARCHIVED_MEDICATION_ID = "66666666-6666-4666-8666-666666666666"
        val SEPTEMBER: HistoryWindow = HistoryWindow.of(YearMonth.of(2026, 9))
    }
}
