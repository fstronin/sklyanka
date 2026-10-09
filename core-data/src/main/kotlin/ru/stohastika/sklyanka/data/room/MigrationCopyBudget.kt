package ru.stohastika.sklyanka.data.room

import android.content.Context
import java.io.File

/**
 * Бюджет копии базы перед миграцией (`ADR-0012` п. 3): сколько места нужно и сколько есть.
 *
 * Место проверяется **до** миграции, а не во время неё: копия снимается до открытия схемы,
 * и если места не хватает, миграция не выполняется вовсе. Число недостающих мегабайт
 * показывается человеку — потому оно и считается здесь, а не выясняется исключением
 * посреди копирования.
 *
 * [requiredBytes] — сумма длин файла базы и его спутников WAL. Спутники входят в счёт
 * намеренно: после `wal_checkpoint(FULL)` содержимое журнала уже в файле базы, но сам файл
 * журнала остаётся на диске, и копия без запаса на него могла бы не поместиться — а отказ
 * «мало места» безопаснее, чем нехватка посреди копии.
 *
 * Арифметика — чистые функции от двух чисел: проверяется на JVM, без Android.
 */
data class MigrationCopyBudget(val requiredBytes: Long, val freeBytes: Long) {

    /** Сколько байт не хватает; ноль, если места достаточно. */
    val missingBytes: Long get() = (requiredBytes - freeBytes).coerceAtLeast(0)

    /**
     * Сколько мегабайт назвать человеку.
     *
     * Округление вверх: «не хватает 3 МБ» при нехватке 2,1 МБ — правда о том, что освободить
     * нужно не меньше, а назвать меньше значило бы послать человека освобождать место,
     * которого всё равно не хватит.
     */
    val missingMegabytes: Long get() = (missingBytes + MEGABYTE - 1) / MEGABYTE

    /** Хватает ли места на копию. Ровно в размер — хватает. */
    val fits: Boolean get() = freeBytes >= requiredBytes

    companion object {
        private const val MEGABYTE = 1024L * 1024L

        /** Спутники базы в режиме WAL: без них копия одного файла неполна или не влезает. */
        private val WAL_SATELLITES = listOf("-wal", "-shm")

        /**
         * Факты системы: размер базы со спутниками и свободное место там, где ляжет копия.
         *
         * Копия живёт в `noBackupFilesDir` (`ADR-0012` п. 3), поэтому и свободное место
         * спрашивается у него, а не у общего хранилища: места на другом томе копии не дают.
         */
        fun of(context: Context, databaseName: String = SklyankaDatabase.NAME): MigrationCopyBudget {
            val database = context.getDatabasePath(databaseName).absolutePath
            val required = WAL_SATELLITES.fold(File(database).length()) { size, suffix ->
                size + File(database + suffix).length()
            }
            return MigrationCopyBudget(requiredBytes = required, freeBytes = context.noBackupFilesDir.usableSpace)
        }

        /**
         * Бюджет — только когда миграция действительно предстоит.
         *
         * Иначе место спрашивалось бы на каждом старте и в каждом приёмнике будильника
         * ради решения, которого не будет: копировать базу той же версии нечего.
         */
        fun onlyWhenPending(
            context: Context,
            storedVersion: Int?,
            expectedVersion: Int = SklyankaDatabase.VERSION,
            databaseName: String = SklyankaDatabase.NAME,
        ): MigrationCopyBudget? = if (storedVersion != null && storedVersion < expectedVersion) {
            of(context, databaseName)
        } else {
            null
        }
    }
}
