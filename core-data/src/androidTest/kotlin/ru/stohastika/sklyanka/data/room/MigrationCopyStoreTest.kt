package ru.stohastika.sklyanka.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Копия базы перед миграцией на настоящих файлах (`ADR-0012` п. 3, SKL-94).
 *
 * Проверяется то, чего не видно из правила: копия действительно снята, действительно
 * заменяет прежнюю и действительно **полна** — в неё попадают кадры, лежавшие в журнале WAL.
 * Последнее и есть смысл `PRAGMA wal_checkpoint(FULL)`: без него копия молча потеряла бы
 * последние записи, то есть ровно те, ради которых она и снимается.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MigrationCopyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanUp() {
        deleteAll()
    }

    @After
    fun cleanDown() {
        deleteAll()
    }

    @Test
    fun theCopyCarriesTheVersionItWasTakenFromInItsName() {
        writeDatabase(version = 8, rows = listOf("intake-1"))

        val copy = MigrationCopyStore(context).take(fromVersion = 8)

        assertEquals("the copy is identified by the version it was taken from", "sklyanka.db.v8", copy.name)
        assertTrue("the copy lives outside the Android backup directory", copy.parentFile == copyDirectory())
        assertEquals("the copy repeats the database file", databaseFile().length(), copy.length())
    }

    @Test
    fun aNewCopyReplacesThePreviousOne() {
        writeDatabase(version = 8, rows = emptyList())
        val previous = File(copyDirectory().apply { mkdirs() }, MigrationCopyStore.nameOf(SklyankaDatabase.NAME, 7))
        previous.writeText("previous copy")

        MigrationCopyStore(context).take(fromVersion = 8)

        assertTrue("the previous copy must go: a copy lives until the next migration", !previous.exists())
        assertEquals("one copy stays, not two", 1, copyDirectory().listFiles()?.size ?: 0)
    }

    @Test
    fun theCopyIncludesTheJournalFramesStillInTheWal() {
        // Соединение держится открытым: иначе SQLite свернул бы журнал сам при закрытии,
        // и тест перестал бы проверять планку-точку, которую делает копия.
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile().apply { parentFile?.mkdirs() }, null)
        database.enableWriteAheadLogging()
        database.use {
            it.execSQL("CREATE TABLE IF NOT EXISTS `intake` (`id` TEXT NOT NULL)")
            it.execSQL("INSERT INTO `intake` (`id`) VALUES ('intake-1')")
            it.execSQL("PRAGMA user_version = 8")

            val copy = MigrationCopyStore(context).take(fromVersion = 8)

            SQLiteDatabase.openDatabase(copy.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { copied ->
                copied.rawQuery("SELECT COUNT(*) FROM `intake` WHERE `id` = 'intake-1'", null).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("the copy must carry the journal frames instead of losing them", 1, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun thereIsNoCopyBeforeAnyMigrationIsPrepared() {
        writeDatabase(version = 9, rows = emptyList())

        assertNull("no copy appears on its own", MigrationCopyStore(context).current())
    }

    private fun writeDatabase(version: Int, rows: List<String>) {
        databaseFile().parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile(), null).use { database ->
            database.execSQL("CREATE TABLE IF NOT EXISTS `intake` (`id` TEXT NOT NULL)")
            rows.forEach { database.execSQL("INSERT INTO `intake` (`id`) VALUES ('$it')") }
            database.execSQL("PRAGMA user_version = $version")
        }
    }

    private fun deleteAll() {
        databaseFile().delete()
        copyDirectory().deleteRecursively()
    }

    private fun databaseFile(): File = context.getDatabasePath(SklyankaDatabase.NAME)

    private fun copyDirectory(): File = File(context.noBackupFilesDir, MigrationCopyStore.DIRECTORY)
}
