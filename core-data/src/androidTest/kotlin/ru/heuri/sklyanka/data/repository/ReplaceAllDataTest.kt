package ru.heuri.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.SklyankaTransactor
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.data.room.entity.OpLogEntity
import ru.heuri.sklyanka.domain.log.OpEvent

/**
 * Транзакционная точка входа для переноса данных (ПЕР-27, критерий 14).
 *
 * Замена всех данных применяется целиком или не применяется вовсе. Порядок удаления —
 * приёмы, лекарства, профили — зафиксирован тестом, а не комментарием: при обратном
 * порядке внешние ключи с RESTRICT останавливают операцию, и это ровно то, чего от них
 * ждут.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class ReplaceAllDataTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val transactor by lazy { SklyankaTransactor(db.database) }

    @Before
    fun fillInitialState() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
    }

    @Test
    fun anInterruptedReplacementLeavesTheOriginalState() = runTest {
        assertThrows(IllegalStateException::class.java) {
            runTest {
                transactor.inTransaction {
                    db.intakes.deleteAll()
                    db.medications.deleteAll()
                    db.profiles.deleteAll()
                    error("the transfer file ended in the middle")
                }
            }
        }

        assertEquals(1, db.profiles.count())
        assertEquals(1, db.medications.countInProfile(TestData.OWNER_ID))
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun aCompletedReplacementEmptiesTheDataInTheDeclaredOrder() = runTest {
        transactor.inTransaction {
            db.intakes.deleteAll()
            db.medications.deleteAll()
            db.profiles.deleteAll()
        }

        assertEquals(0, db.profiles.count())
    }

    @Test
    fun theOperationLogSurvivesAReplacement() = runTest {
        db.opLog.insert(
            OpLogEntity(
                at = MOMENT,
                event = OpEvent.TRANSFER_APPLIED.name,
                profileId = TestData.OWNER_ID,
                medicationId = null,
                intakeId = null,
                valueBefore = null,
                valueAfter = null,
                source = null,
                detail = null,
            ),
        )

        transactor.inTransaction {
            db.intakes.deleteAll()
            db.medications.deleteAll()
            db.profiles.deleteAll()
        }

        assertEquals(1, db.opLog.recent(RECENT_LIMIT).size)
    }

    private companion object {
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val RECENT_LIMIT = 50
        const val MOMENT = 1_790_000_000_000L
    }
}
