package ru.heuri.sklyanka.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.heuri.sklyanka.data.room.dao.IntakeDao
import ru.heuri.sklyanka.data.room.dao.MedicationDao
import ru.heuri.sklyanka.data.room.dao.OpLogDao
import ru.heuri.sklyanka.data.room.dao.ProfileDao
import ru.heuri.sklyanka.data.room.dao.ReminderDao
import ru.heuri.sklyanka.data.room.dao.ReminderLogDao
import ru.heuri.sklyanka.data.room.entity.IntakeEntity
import ru.heuri.sklyanka.data.room.entity.MedicationEntity
import ru.heuri.sklyanka.data.room.entity.OpLogEntity
import ru.heuri.sklyanka.data.room.entity.ProfileEntity
import ru.heuri.sklyanka.data.room.entity.ReminderLogEntity
import ru.heuri.sklyanka.data.transfer.TransferDao

/**
 * База «Склянки».
 *
 * Единственное место, где живут данные человека: облака нет, резервной копии, кроме
 * файла переноса, нет (НФТ-13). Поэтому здесь нет и не появится разрушающей миграции —
 * потеря базы при обновлении равносильна потере приложения (МГ-2).
 *
 * Версия 4. Версия 1 — технический маркер скелета SKL-02, из которого первая миграция
 * развернула настоящую схему (версия 2); версия 3 добавила лекарству признак дублирования
 * напоминаний на часы (SKL-10, ЧАС-15); версия 4 — журнал срабатываний напоминаний
 * (SKL-13, НФТ-50). Схемы экспортируются в `core-data/schemas` и входят в ревью (МГ-1).
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

    /** Выборки планировщика напоминаний (SKL-13, НАП-12). */
    abstract fun reminderDao(): ReminderDao

    /**
     * Выборки переноса данных (SKL-19, ПЕР-15, ПЕР-16).
     *
     * Отдельный DAO, а не новые методы у существующих: там запроса «отдай всё» нет вовсе
     * (ПР-10), и перенос — единственное законное исключение, которое обязано быть видно
     * списком. Схему базы он не меняет: новых сущностей и колонок у него нет.
     */
    abstract fun transferDao(): TransferDao

    companion object {
        const val VERSION = 4

        /**
         * Имя файла базы.
         *
         * Вынесено константой: его знают тест миграции и страховочная выгрузка перед
         * заменой данных при переносе (ПЕР-16).
         */
        const val NAME = "sklyanka.db"
    }
}
