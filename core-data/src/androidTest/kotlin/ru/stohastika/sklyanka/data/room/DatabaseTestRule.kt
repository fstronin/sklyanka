package ru.stohastika.sklyanka.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * База в памяти для инструментальных тестов.
 *
 * Внешние ключи Room включает сам, и это важно: половина проверяемых здесь инвариантов
 * (INV-12, INV-18) держится именно ими, а не кодом.
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
        ).build()
    }

    override fun after() {
        database.close()
    }
}
