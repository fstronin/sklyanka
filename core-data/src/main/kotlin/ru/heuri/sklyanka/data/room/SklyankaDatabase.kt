package ru.heuri.sklyanka.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * База «Склянки».
 *
 * Сущностей пока нет: схема (профили, лекарства, приёмы, журнал) приходит в SKL-04.
 * Здесь проверяется только работоспособность обвязки — что база поднимается в памяти.
 *
 * Разрушающие миграции запрещены правилом DestructiveRoomMigration (НФТ-9):
 * история приёмов — то, ради чего приложение существует.
 */
@Database(entities = [], version = 1, exportSchema = false)
abstract class SklyankaDatabase : RoomDatabase()
