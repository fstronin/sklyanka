package ru.stohastika.sklyanka.data.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus

/**
 * Гонка двух источников отметки (НФТ-10, критерий 11).
 *
 * Разрешается не блокировкой, а условием внутри самого UPDATE: первый выигрывает,
 * второй видит ноль изменённых строк. Тысяча повторов на двух потоках — чтобы
 * совпадение по времени случилось хотя бы раз.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ConcurrencyTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Test
    fun simultaneousMarkingFromTwoSourcesLeavesExactlyOneMarking() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())

        var wonTwice = 0
        repeat(REPEATS) { attempt ->
            val intakeId = "$INTAKE_PREFIX$attempt"
            db.intakes.insertAll(
                listOf(
                    TestData.scheduledIntake(
                        id = intakeId,
                        scheduledAt = TestData.MORNING.plusMinutes(attempt.toLong()),
                    ),
                ),
            )

            val results = withContext(Dispatchers.IO) {
                listOf(IntakeSource.PHONE, IntakeSource.AUTO)
                    .map { source ->
                        async {
                            db.intakes.markIfPending(
                                intakeId = intakeId,
                                status = IntakeStatus.TAKEN.name,
                                actedAt = MARK_AT,
                                source = source.name,
                                actorProfileId = null,
                            )
                        }
                    }
                    .awaitAll()
            }
            if (results.count { it == 1 } != 1) wonTwice++
        }

        assertEquals("every race must have exactly one winner", 0, wonTwice)
    }

    private companion object {
        const val REPEATS = 1_000
        const val INTAKE_PREFIX = "aaaaaaaa-0000-4000-8000-race"
        const val MARK_AT = 1_790_000_000_000L
    }
}
