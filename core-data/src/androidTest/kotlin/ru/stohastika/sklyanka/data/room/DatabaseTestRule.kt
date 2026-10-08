package ru.stohastika.sklyanka.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * База в памяти для инструментальных тестов.
 *
 * Внешние ключи включает `ForeignKeyEnforcement` — тот же явный код, что стоит
 * в продуктовом графе: половина проверяемых здесь инвариантов (ИНВ-12, ИНВ-18)
 * держится ими, а не кодом, и проверяться они должны на том же открытии базы.
 */
class DatabaseTestRule : ExternalResource() {

    lateinit var database: SklyankaDatabase
        private set

    val profiles get() = database.profileDao()
    val medications get() = database.medicationDao()
    val intakes get() = database.intakeDao()
    val opLog get() = database.opLogDao()

    override fun before() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SklyankaDatabase::class.java,
        ).build().withForeignKeysEnabled()
    }

    override fun after() {
        database.close()
    }
}
