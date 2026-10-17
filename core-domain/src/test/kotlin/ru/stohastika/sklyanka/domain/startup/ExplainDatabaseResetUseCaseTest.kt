package ru.stohastika.sklyanka.domain.startup

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Четыре случая объяснения сброса (Р-87 п. 2): чистая установка молчит, потеря
 * данных объясняется один раз, обычное открытие молчит, закрытое объяснение не
 * возвращается — до нового пересоздания при живой отметке.
 */
@DisplayName("Database recreation is explained once and only after a lived database")
class ExplainDatabaseResetUseCaseTest {

    private val start = Instant.parse("2026-10-17T06:00:00Z")
    private val clock = FixedTimeSource(start)

    /** Отметки жизни в памяти: те же два хранилища, что в продукте, одним объектом. */
    private class InMemoryDatabaseLife(private var recreated: Boolean = false, private var life: Boolean = false) :
        DatabaseLifeStore {

        var resetEventAt: Long? = null
            private set
        var acknowledgedAt: Long? = null
            private set

        fun recreate() {
            recreated = true
        }

        override fun consumeDatabaseRecreated(): Boolean {
            val value = recreated
            recreated = false
            return value
        }

        override suspend fun hadLife(): Boolean = life

        override suspend fun markLife() {
            life = true
        }

        override suspend fun resetEventAt(): Long? = resetEventAt

        override suspend fun recordResetEventAt(epochMillis: Long) {
            resetEventAt = epochMillis
        }

        override suspend fun resetEventAcknowledgedAt(): Long? = acknowledgedAt

        override suspend fun acknowledgeResetEventAt(epochMillis: Long) {
            acknowledgedAt = epochMillis
        }
    }

    private fun useCase(life: DatabaseLifeStore) = ExplainDatabaseResetUseCase(life, clock)

    @Test
    fun `a fresh install recreates the database without an explanation`() = runTest {
        val life = InMemoryDatabaseLife(recreated = true, life = false)

        assertFalse(useCase(life).explanationOnStartup())
        // После старта база отмечена живой: следующее пересоздание обязано объясниться.
        assertTrue(life.hadLife())
        assertEquals(null, life.resetEventAt)
    }

    @Test
    fun `recreation after a lived database explains the reset`() = runTest {
        val life = InMemoryDatabaseLife(recreated = true, life = true)

        assertTrue(useCase(life).explanationOnStartup())
        assertEquals(start.toEpochMilli(), life.resetEventAt)
    }

    @Test
    fun `an ordinary startup of a lived database stays silent`() = runTest {
        val life = InMemoryDatabaseLife(recreated = false, life = true)

        assertFalse(useCase(life).explanationOnStartup())
        assertEquals(null, life.resetEventAt)
    }

    @Test
    fun `a dismissed explanation does not return until the database is recreated again`() = runTest {
        val life = InMemoryDatabaseLife(recreated = true, life = true)
        val useCase = useCase(life)
        assertTrue(useCase.explanationOnStartup())

        // Закрытый баннер не возвращается: повторный старт того же процесса молчит.
        useCase.dismissExplanation()
        assertEquals(life.resetEventAt, life.acknowledgedAt)
        assertFalse(useCase.explanationOnStartup())

        // Новое пересоздание при живой отметке — новое событие, объяснение возвращается.
        clock.advanceTo(Instant.parse("2026-10-18T06:00:00Z"))
        life.recreate()
        assertTrue(useCase.explanationOnStartup())
        assertEquals(clock.now().toEpochMilli(), life.resetEventAt)
    }
}
