package ru.stohastika.sklyanka.data.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Признак «база пересоздана при этом открытии» (МГ-8, Р-87 п. 2).
 *
 * Колбэк Room `onCreate` срабатывает, когда файла базы не было: первый запуск
 * чистой установки — и пересоздание после того, как платформа стёрла повреждённый
 * файл. Признак живёт в памяти процесса и читается один раз ([consumeRecreated]):
 * отличить чистую установку от потери данных по нему одному нельзя — для этого
 * у домена есть отметка жизни базы вне файла (`DatabaseLifeStore`).
 *
 * Синглтон графа, а не поля провайдера: провайдер базы собирается один раз, но
 * признак нужен и в отказных состояниях базы, где файл не открывается, — там
 * колбэк просто не срабатывает, и признак остаётся снятым.
 */
@Singleton
class DatabaseRecreation @Inject constructor() {

    private val recreated = AtomicBoolean(false)

    /** Колбэк для строителя базы: ставится в `DataModule.provideDatabase`. */
    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            recreated.set(true)
        }
    }

    /** Снять признак: `true` ровно один раз на процесс, в котором база пересоздана. */
    fun consumeRecreated(): Boolean = recreated.getAndSet(false)
}
