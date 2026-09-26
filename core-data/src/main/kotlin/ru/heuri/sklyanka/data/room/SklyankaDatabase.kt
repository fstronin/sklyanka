package ru.heuri.sklyanka.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.heuri.sklyanka.data.room.dao.IntakeDao
import ru.heuri.sklyanka.data.room.dao.MedicationDao
import ru.heuri.sklyanka.data.room.dao.OpLogDao
import ru.heuri.sklyanka.data.room.dao.ProfileDao
import ru.heuri.sklyanka.data.room.entity.IntakeEntity
import ru.heuri.sklyanka.data.room.entity.MedicationEntity
import ru.heuri.sklyanka.data.room.entity.OpLogEntity
import ru.heuri.sklyanka.data.room.entity.ProfileEntity

/**
 * База «Склянки».
 *
 * Единственное место, где живут данные человека: облака нет, резервной копии, кроме
 * файла переноса, нет (НФТ-13). Поэтому здесь нет и не появится разрушающей миграции —
 * потеря базы при обновлении равносильна потере приложения (МГ-2).
 *
 * Версия 2. Версия 1 — технический маркер скелета SKL-02, из которого первая миграция
 * этой задачи развернула настоящую схему. Схемы экспортируются в `core-data/schemas`
 * и входят в ревью (МГ-1).
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
    ],
    version = SklyankaDatabase.VERSION,
    exportSchema = true,
)
abstract class SklyankaDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun medicationDao(): MedicationDao

    abstract fun intakeDao(): IntakeDao

    abstract fun opLogDao(): OpLogDao

    companion object {
        const val VERSION = 2

        /**
         * Имя файла базы.
         *
         * Вынесено константой: его знают тест миграции и страховочная выгрузка перед
         * заменой данных при переносе (ПЕР-16).
         */
        const val NAME = "sklyanka.db"
    }
}
