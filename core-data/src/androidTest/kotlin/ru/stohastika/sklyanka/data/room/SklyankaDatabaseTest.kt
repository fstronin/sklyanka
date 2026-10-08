package ru.stohastika.sklyanka.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальный уровень (НФТ-45): Room поднимается на устройстве, не на JVM.
 * JUnit 4 здесь — штатно поддерживаемый платформой фреймворк (TDD §6.1).
 */
@RunWith(AndroidJUnit4::class)
class SklyankaDatabaseTest {

    private lateinit var database: SklyankaDatabase

    @Before
    fun openInMemoryDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SklyankaDatabase::class.java,
        ).build().withForeignKeysEnabled()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun inMemoryDatabaseOpens() {
        assertTrue(database.openHelper.writableDatabase.isOpen)
    }
}
