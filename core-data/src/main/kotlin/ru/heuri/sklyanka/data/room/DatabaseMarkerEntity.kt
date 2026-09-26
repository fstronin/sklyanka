package ru.heuri.sklyanka.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Техническая запись, на которой держится база до появления схемы.
 *
 * Room отказывается генерировать базу без единственной сущности, а схема
 * «Склянки» — профили, лекарства, приёмы, журнал — приходит в SKL-04.
 * Продуктового смысла у этой таблицы нет и быть не должно: её задача —
 * дать инструментальному тесту что поднимать. В SKL-04 она уходит
 * первой же миграцией.
 */
@Entity(tableName = "database_marker")
data class DatabaseMarkerEntity(@PrimaryKey val id: Int = 1)
