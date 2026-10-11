package ru.stohastika.sklyanka.screen.support

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.withForeignKeysEnabled
import java.io.File

/**
 * Хранилище похода (SKL-140, TDD §5.2, §9).
 *
 * Экранные проверки идут на базе в памяти: им нужен один шаг одного экрана. Походу память
 * не годится: «человек закрыл приложение и вернулся», «признак просмотренного онбординга
 * пережил смерть процесса» и «файл переноса лежит на диске» проверяются только на настоящем
 * файле. Поэтому хранилище выбирает аргумент инструментовки, а не имя класса: один и тот же
 * APK служит обоим наборам, и граф собирается одинаково.
 *
 * Прогон класса похода без аргумента — отказ, а не тихая деградация в память: иначе набор
 * выглядел бы зелёным, не проверив ни одного своего обещания про выживание.
 */
internal object DeviceJourneyStorage {

    /** Аргумент инструментовки: `journeyStorage=file` — поход идёт на настоящем файле базы. */
    const val ARGUMENT = "journeyStorage"

    /** Значение аргумента, включающее файловое хранилище. */
    const val FILE = "file"

    /** Требование похода: без файлового хранилища поход не начинается. */
    fun requireFile() {
        if (isFileRun()) return
        throw AssertionError(
            "поход обязан идти на файловом хранилище: запускайте группу journey скриптом " +
                "tools/emulator/run-journeys.sh (нужен аргумент $ARGUMENT=$FILE), " +
                "иначе выживание состояния не проверяется",
        )
    }

    /**
     * База графа: файл для похода, память для экранной проверки.
     *
     * Файл открывается тем же `SklyankaDatabase`, что и продукт: своей схемы у походов нет,
     * и проверки `quick_check`/`foreign_key_check` (ADR-0012) касаются её так же.
     */
    fun open(context: Context): SklyankaDatabase = if (isFileRun()) {
        Room.databaseBuilder(context, SklyankaDatabase::class.java, SklyankaDatabase.NAME)
            .build()
            .withForeignKeysEnabled()
    } else {
        Room.inMemoryDatabaseBuilder(context, SklyankaDatabase::class.java)
            .build()
            .withForeignKeysEnabled()
    }

    /**
     * Стереть файл базы перед походом вместе с журналами: поход не наследует данные
     * предыдущего. Вызывается до сборки графа — открытая база держала бы прежний файл.
     */
    fun wipe(context: Context) {
        val database = context.getDatabasePath(SklyankaDatabase.NAME)
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).forEach { it.delete() }
    }

    private fun isFileRun(): Boolean = InstrumentationRegistry.getArguments().getString(ARGUMENT).orEmpty() == FILE
}
