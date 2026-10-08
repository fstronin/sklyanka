package ru.stohastika.sklyanka.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused

/**
 * Явное включение внешних ключей (ИНВ-12, SKL-107).
 *
 * Различение механизма сделано на сыром соединении без Room (один connection, файл):
 * без включения висячая ссылка пишется, тот же вызов
 * `setForeignKeyConstraintsEnabled`, что стоит в `withForeignKeysEnabled`, обращает
 * соединение — отказ по внешнему ключу. Тем же тестом закреплено, почему включение
 * нельзя было посадить в `RoomDatabase.Callback`: `PRAGMA` из `onCreate` внутри
 * транзакции создания не действует (висячая ссылка проходит). Боевая схема — файловая
 * база, собранная `Room.databaseBuilder` с продуктовой точкой включения, — отвергает
 * висячую ссылку; стендовая in-memory база открывает соединение с поднятой прагмой.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class ForeignKeyEnforcementTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun dropDatabases() {
        (RAW_NAMES + listOf(PRODUCT_DB)).forEach { name -> context.deleteDatabase(name) }
    }

    @Test
    fun theWiredDatabaseOpensWithForeignKeysOn() {
        assertEquals(1, foreignKeysFlag(db.database.openHelper.writableDatabase))
    }

    @Test
    fun aRawConnectionWithoutEnableAcceptsADanglingReference() {
        openRaw(RAW_OFF).writableDatabase.use { database ->
            assertEquals("the raw connection opens with the flag off", 0, foreignKeysFlag(database))
            database.execSQL(INSERT_DANGLING_CHILD)
            assertEquals(1, count(database))
        }
    }

    @Test
    fun theSameEnableCallRefusesADanglingReference() {
        openRaw(RAW_ON).writableDatabase.use { database ->
            database.setForeignKeyConstraintsEnabled(true)
            assertEquals("the call must raise the flag itself", 1, foreignKeysFlag(database))
            assertThrows(SQLiteConstraintException::class.java) { database.execSQL(INSERT_DANGLING_CHILD) }
        }
    }

    @Test
    fun aPragmaInsideTheCreateTransactionIsNotEffective() {
        // Пин для обоснования выбора точки: в `onCreate` платформенный помощник держит
        // транзакцию создания, а `PRAGMA foreign_keys` внутри транзакции — no-op.
        openRaw(RAW_PRAGMA, onCreateExtra = { it.execSQL(PRAGMA_FOREIGN_KEYS) }).writableDatabase.use { database ->
            assertEquals("the pragma inside the create transaction must have no effect", 0, foreignKeysFlag(database))
            database.execSQL(INSERT_DANGLING_CHILD)
            assertEquals(1, count(database))
        }
    }

    @Test
    fun theProductFileConnectionRefusesADanglingReference() {
        val database = Room.databaseBuilder(context, SklyankaDatabase::class.java, PRODUCT_DB)
            .build()
            .withForeignKeysEnabled()
        try {
            runBlocking {
                assertRefused<SQLiteConstraintException> {
                    database.medicationDao().upsert(TestData.medication(profileId = MISSING_ID))
                }
            }
        } finally {
            database.close()
        }
    }

    private fun openRaw(name: String, onCreateExtra: (SupportSQLiteDatabase) -> Unit = {}): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(SCHEMA_VERSION) {
            override fun onCreate(database: SupportSQLiteDatabase) {
                onCreateExtra(database)
                database.execSQL(CREATE_PARENT)
                database.execSQL(CREATE_CHILD)
            }

            override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory()
            .create(SupportSQLiteOpenHelper.Configuration(context, name, callback))
    }

    private fun foreignKeysFlag(database: SupportSQLiteDatabase): Int =
        database.query(READ_FOREIGN_KEYS).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun count(database: SupportSQLiteDatabase): Int =
        database.query("SELECT COUNT(*) FROM child").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val RAW_OFF = "skl107-raw-off.db"
        const val RAW_ON = "skl107-raw-on.db"
        const val RAW_PRAGMA = "skl107-raw-pragma.db"
        val RAW_NAMES = listOf(RAW_OFF, RAW_ON, RAW_PRAGMA)
        const val PRODUCT_DB = "skl107-product-file.db"
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"

        const val READ_FOREIGN_KEYS = "PRAGMA foreign_keys"
        const val PRAGMA_FOREIGN_KEYS = "PRAGMA foreign_keys = ON"
        const val CREATE_PARENT = "CREATE TABLE parent (id TEXT PRIMARY KEY NOT NULL)"
        const val CREATE_CHILD =
            "CREATE TABLE child (id TEXT PRIMARY KEY NOT NULL, " +
                "parent_id TEXT NOT NULL REFERENCES parent (id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
        const val INSERT_DANGLING_CHILD = "INSERT INTO child (id, parent_id) VALUES ('orphan', 'no-such-parent')"
    }
}
