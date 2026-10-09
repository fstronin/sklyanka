package ru.stohastika.sklyanka.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Чтение версии базы на настоящем файле (Р-44, `ADR-0012` п. 7, SKL-128).
 *
 * Прогон требует устройства или эмулятора: читается настоящий файл базы настоящим
 * соединением. В контейнере сборки тест компилируется, но не выполняется, как и соседние
 * инструментальные тесты.
 *
 * Проверяется то, чего нельзя проверить правилом: что `PRAGMA user_version` действительно
 * читается у файла и что само чтение версию не меняет — иначе отказ от работы сам двигал бы
 * базу вперёд.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseVersionReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val reader = DatabaseVersionReader(context, NAME)

    @After
    fun deleteTheFile() {
        context.getDatabasePath(NAME).delete()
    }

    @Test
    fun aMissingFileIsAFirstInstall() {
        assertNull("there is no file yet", reader.read())
        assertEquals(DatabaseState.Ready, databaseState(reader.read()))
    }

    @Test
    fun theVersionOfAnOlderFileIsReadBack() {
        writeVersion(SklyankaDatabase.VERSION - 1)

        assertEquals(SklyankaDatabase.VERSION - 1, reader.read())
        assertEquals(DatabaseState.Ready, databaseState(reader.read()))
    }

    @Test
    fun theVersionOfTheShippedSchemaIsReadBack() {
        writeVersion(SklyankaDatabase.VERSION)

        assertEquals(DatabaseState.Ready, databaseState(reader.read()))
    }

    @Test
    fun aNewerFileIsARefusal() {
        writeVersion(SklyankaDatabase.VERSION + 1)

        assertEquals(SklyankaDatabase.VERSION + 1, reader.read())
        assertEquals(DatabaseState.Newer, databaseState(reader.read()))
    }

    @Test
    fun readingDoesNotMoveTheVersionForward() {
        writeVersion(SklyankaDatabase.VERSION + 1)

        repeat(READS) { reader.read() }

        assertEquals("the check must not touch the file", SklyankaDatabase.VERSION + 1, reader.read())
    }

    private fun writeVersion(version: Int) {
        val path = context.getDatabasePath(NAME)
        path.parentFile?.mkdirs()
        path.delete()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL("PRAGMA user_version = $version")
        }
    }

    private companion object {
        const val NAME = "version-reader-test.db"
        const val READS = 3
    }
}
