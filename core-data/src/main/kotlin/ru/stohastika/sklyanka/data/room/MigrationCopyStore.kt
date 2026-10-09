package ru.stohastika.sklyanka.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Копия базы перед миграцией (`ADR-0012` п. 3).
 *
 * Копия **не резервная**: резервной копии, кроме файла переноса, не существует (НФТ-13).
 * Это последняя линия обороны перед необратимым переходом схемы: миграции идут только
 * вперёд (`ADR-0012` п. 1), и вернуться к предыдущей сборке можно лишь вместе с базой,
 * которую она знала. Поэтому копия живёт **до следующей миграции** — вторая рядом
 * с первой съела бы место ровно тогда, когда оно нужнее всего.
 *
 * Каталог — `noBackupFilesDir`: вне резервного копирования Android (НФТ-20) и вне файла
 * переноса (НФТ-13). Собственного имени «бэкап» у копии нет ни в каталоге, ни в файле.
 */
class MigrationCopyStore(private val context: Context, private val databaseName: String = SklyankaDatabase.NAME) {

    /**
     * Снять копию базы версии [fromVersion], убрав прежнюю.
     *
     * [fromVersion] попадает в имя файла: копия опознаётся по версии, которую она держит,
     * и человек (или следующая миграция) не путает её с текущей базой.
     */
    fun take(fromVersion: Int): File {
        checkpoint()
        val directory = directory().apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val copy = File(directory, nameOf(databaseName, fromVersion))
        databaseFile().copyTo(copy, overwrite = true)
        return copy
    }

    /** Единственная существующая копия, если она есть: копия живёт до следующей миграции. */
    fun current(): File? = directory().listFiles()?.firstOrNull()

    /**
     * `PRAGMA wal_checkpoint(FULL)` перед копированием.
     *
     * База живёт в режиме WAL, и файл `-wal` держит кадры, которых в файле базы ещё нет.
     * Полная планка-точка переносит их в файл базы, и копия **одного файла** становится
     * полной: без неё копия молча потеряла бы последние записи — ровно те, ради которых
     * она и снимается.
     */
    private fun checkpoint() {
        val path = databaseFile()
        if (!path.exists()) return
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { open ->
            open.rawQuery(CHECKPOINT, null).use { cursor -> cursor.moveToFirst() }
        }
    }

    private fun databaseFile(): File = context.getDatabasePath(databaseName)

    private fun directory(): File = File(context.noBackupFilesDir, DIRECTORY)

    companion object {
        /** Каталог копий внутри `noBackupFilesDir`; имя — по делу, а не «backup». */
        const val DIRECTORY = "migration-copy"

        /** Имя копии: имя базы и версия «от», которую копия держит. */
        fun nameOf(databaseName: String, fromVersion: Int): String = "$databaseName.v$fromVersion"

        private const val CHECKPOINT = "PRAGMA wal_checkpoint(FULL)"
    }
}
