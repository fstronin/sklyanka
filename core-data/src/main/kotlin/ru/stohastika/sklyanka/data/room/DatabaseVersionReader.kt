package ru.stohastika.sklyanka.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Чтение `PRAGMA user_version` у файла базы — **без Room** (Р-44, `ADR-0012` п. 7).
 *
 * Затем и без Room: вопрос «открывать ли базу» задаётся до сборки базы, а сам
 * `RoomDatabase.Builder.build()` файла не касается — касается его
 * `withForeignKeysEnabled()`, и именно там старая сборка падает на новой базе. Значит
 * версия читается прямым соединением, а решение принимается до открытия.
 *
 * Соединение открывается на чтение-запись, а не строго на чтение: у базы в режиме WAL
 * read-only соединение может не суметь прочитать шапку, если рядом лежат `-wal` и `-shm`,
 * а файл здесь заведомо существует (иначе функция уже вернула `null`). Данные при этом
 * не меняются: выполняется один `PRAGMA` без записи.
 */
class DatabaseVersionReader(private val context: Context, private val databaseName: String = SklyankaDatabase.NAME) {

    /**
     * Версия схемы в файле; `null` — файла нет (первая установка).
     *
     * Ошибку чтения не глушим и не превращаем в «базу можно открывать»: соединение
     * с существующим файлом, который не удалось прочитать, — это не «первая установка»,
     * и решение о нём принимает вызывающий, а не тихий `catch`.
     */
    fun read(): Int? {
        val path = context.getDatabasePath(databaseName)
        if (!path.exists()) return null
        val database = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return database.use { open ->
            open.rawQuery(USER_VERSION, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(FIRST_COLUMN) else null
            }
        }
    }

    private companion object {
        /** Имя прагмы версии схемы: Room пишет её сам при создании и миграции. */
        const val USER_VERSION = "PRAGMA user_version"

        const val FIRST_COLUMN = 0
    }
}
