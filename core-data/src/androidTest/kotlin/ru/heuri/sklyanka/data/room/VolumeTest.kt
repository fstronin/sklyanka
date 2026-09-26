package ru.heuri.sklyanka.data.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.room.entity.IntakeEntity
import ru.heuri.sklyanka.data.room.entity.ProfileEntity
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

/**
 * База года приёма: объём, скорость выборки суток и цена текстовых идентификаторов.
 *
 * Наполнение — по НФТ-9: 4 профиля, 30 лекарств, 4 000 приёмов. То же наполнение
 * послужит тестом миграции без потерь, когда появится первая миграция, меняющая
 * таблицы с данными.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class VolumeTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Test
    fun aYearOfIntakesIsSelectedByDayWithinTheBudget() = runTest {
        fillYearOfIntakes()

        var day = emptyList<IntakeEntity>()
        val elapsed = measureTimeMillis {
            day = db.intakes.observeDay(profileId(0), DAY_START, NEXT_DAY_START).first()
        }

        assertTrue("the day selection must not be empty", day.isNotEmpty())
        assertTrue("selecting a day took $elapsed ms", elapsed < DAY_QUERY_BUDGET_MILLIS)
    }

    @Test
    fun aYearOfIntakesKeepsEveryRow() = runTest {
        fillYearOfIntakes()

        val total = (0 until PROFILES).sumOf { db.intakes.countInProfile(profileId(it)) }

        assertEquals(INTAKES, total)
    }

    /** 4 профиля, 30 лекарств, 4 000 приёмов — наполнение по НФТ-9. */
    private suspend fun fillYearOfIntakes() {
        repeat(PROFILES) { index ->
            db.profiles.insert(
                TestData.owner().copy(
                    id = profileId(index),
                    isSelf = index == 0,
                    selfSlot = if (index == 0) ProfileEntity.SELF_SLOT else null,
                ),
            )
        }
        repeat(MEDICATIONS) { index ->
            db.medications.upsert(
                TestData.medication(
                    id = medicationId(index),
                    profileId = profileId(index % PROFILES),
                    name = "med-$index",
                ),
            )
        }
        val rows = ArrayList<IntakeEntity>(INTAKES)
        repeat(INTAKES) { index ->
            rows += TestData.scheduledIntake(
                id = "intake-$index",
                medicationId = medicationId(index % MEDICATIONS),
                scheduledAt = FIRST_INTAKE.plusHours(index.toLong()),
            )
        }
        db.intakes.insertAll(rows)
    }

    private fun profileId(index: Int) = "profile-$index"

    private fun medicationId(index: Int) = "medication-$index"

    private companion object {
        const val PROFILES = 4
        const val MEDICATIONS = 30
        const val INTAKES = 4_000

        val FIRST_INTAKE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 8, 0)
        const val DAY_START = "2026-01-01T00:00"
        const val NEXT_DAY_START = "2026-01-02T00:00"

        /** НФТ-22: выборка суток укладывается в 150 мс. */
        const val DAY_QUERY_BUDGET_MILLIS = 150
    }
}
