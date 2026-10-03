package ru.stohastika.sklyanka.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.stohastika.sklyanka.data.room.dao.DeferredReminderDao
import ru.stohastika.sklyanka.data.room.dao.HistoryDao
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.dao.ReminderDao
import ru.stohastika.sklyanka.data.room.dao.ReminderLogDao
import ru.stohastika.sklyanka.data.room.entity.DeferredReminderEntity
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.data.room.entity.OpLogEntity
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity
import ru.stohastika.sklyanka.data.transfer.TransferDao

/**
 * База «Склянки».
 *
 * Единственное место, где живут данные человека: облака нет, резервной копии, кроме
 * файла переноса, нет (НФТ-13). Поэтому здесь нет и не появится разрушающей миграции —
 * потеря базы при обновлении равносильна потере приложения (МГ-2).
 *
 * Версия 6. Версия 1 — технический маркер скелета SKL-02, из которого первая миграция
 * развернула настоящую схему (версия 2); версия 3 добавила лекарству признак дублирования
 * напоминаний на часы (SKL-10, ЧАС-15); версия 4 — журнал срабатываний напоминаний
 * (SKL-13, НФТ-50); версия 5 — размер упаковки у лекарства (SKL-34, ОВ-45);
 * версия 6 — таблица отложенных срабатываний (SKL-35, ОВ-75); версия 7 — момент мягкого
 * удаления лекарства (SKL-99, ПМ-24). Схемы экспортируются
 * в `core-data/schemas` и входят в ревью (МГ-1).
 *
 * Журнал операций лежит в той же базе намеренно: запись в него обязана идти той же
 * транзакцией, что и сама операция.
 */
@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class,
        IntakeEntity::class,
        OpLogEntity::class,
        ReminderLogEntity::class,
        DeferredReminderEntity::class,
    ],
    version = SklyankaDatabase.VERSION,
    exportSchema = true,
)
abstract class SklyankaDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun medicationDao(): MedicationDao

    abstract fun intakeDao(): IntakeDao

    abstract fun opLogDao(): OpLogDao

    /** Журнал срабатываний напоминаний (SKL-13, НФТ-50). */
    abstract fun reminderLogDao(): ReminderLogDao

    /** Отложенные срабатывания, переживающие перезагрузку (SKL-35, ОВ-75). */
    abstract fun deferredReminderDao(): DeferredReminderDao

    /** Выборки планировщика напоминаний (SKL-13, НАП-12). */
    abstract fun reminderDao(): ReminderDao

    /** Выборки экрана истории (SKL-18): единственное законное чтение лекарств без фильтра архива. */
    abstract fun historyDao(): HistoryDao

    /**
     * Выборки переноса данных (SKL-19, ПЕР-15, ПЕР-16).
     *
     * Отдельный DAO, а не новые методы у существующих: там запроса «отдай всё» нет вовсе
     * (ПР-10), и перенос — единственное законное исключение, которое обязано быть видно
     * списком. Схему базы он не меняет: новых сущностей и колонок у него нет.
     */
    abstract fun transferDao(): TransferDao

    companion object {
        const val VERSION = 7

        /**
         * Имя файла базы.
         *
         * Вынесено константой: его знают тест миграции и страховочная выгрузка перед
         * заменой данных при переносе (ПЕР-16).
         */
        const val NAME = "sklyanka.db"
    }
}
