package ru.stohastika.sklyanka.data.room

/**
 * Дешёвые проверки целостности базы (`ADR-0012` п. 4).
 *
 * Проверяются структура файла и ссылки: `PRAGMA quick_check` и `PRAGMA foreign_key_check`.
 * Проверка **ничего не чинит и не блокирует**: автоматический ремонт запрещён (МГ-6),
 * а напоминания важнее проверки (П-1). Находка — это факт для журнала, и только.
 */
class DatabaseIntegrityCheck(private val database: SklyankaDatabase) {

    fun run(): IntegrityReport = IntegrityReport(
        quickCheck = quickCheck(),
        brokenForeignKeys = brokenForeignKeys(),
    )

    /**
     * `quick_check` — структура файла без полного перечитывания страниц.
     *
     * Исправная база отвечает ровно одной строкой `ok`; любая другая картина (сообщение,
     * несколько строк, пустой ответ) — находка. Сообщение SQLite говорит о таблицах и
     * страницах, не о людях: медицинских данных в нём нет (НФТ-19).
     */
    private fun quickCheck(): String {
        val messages = mutableListOf<String>()
        database.openHelper.writableDatabase.query(QUICK_CHECK).use { cursor ->
            while (cursor.moveToNext()) messages += cursor.getString(FIRST_COLUMN) ?: UNKNOWN
        }
        return if (messages == listOf(OK)) OK else messages.joinToString(SEPARATOR).ifEmpty { NO_RESULT }
    }

    /** `foreign_key_check` отдаёт строку на каждую висячую ссылку; пусто — ссылки целы. */
    private fun brokenForeignKeys(): Int =
        database.openHelper.writableDatabase.query(FOREIGN_KEY_CHECK).use { it.count }

    private companion object {
        const val QUICK_CHECK = "PRAGMA quick_check"
        const val FOREIGN_KEY_CHECK = "PRAGMA foreign_key_check"
        const val FIRST_COLUMN = 0
        const val SEPARATOR = "|"
        const val NO_RESULT = "no result"
        const val UNKNOWN = "unknown"

        /** Ответ исправной базы: единственная строка `ok`. */
        const val OK = "ok"
    }
}

/** Исход проверки: сообщение `quick_check` и число нарушенных ссылок. */
data class IntegrityReport(val quickCheck: String, val brokenForeignKeys: Int) {

    val intact: Boolean get() = quickCheck == OK && brokenForeignKeys == 0

    /**
     * Что записать в журнал.
     *
     * Формат — служебные коды и числа: таблицы и страницы SQLite, не медицинские значения
     * (НФТ-19). Строка читается и человеком, и разбором: `quickCheck=ok;foreignKeys=0`.
     */
    fun detail(): String = "quickCheck=$quickCheck;foreignKeys=$brokenForeignKeys"

    private companion object {
        const val OK = "ok"
    }
}
