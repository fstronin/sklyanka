package ru.heuri.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Профиль.
 *
 * [selfSlot] — служебная колонка, которой нет в модели данных. Она несёт значение
 * [SELF_SLOT] у профиля владельца и NULL у всех остальных, а уникальный индекс поверх
 * неё держит «владелец ровно один» (INV-1) средствами базы: в SQLite NULL не равен
 * ничему, включая другой NULL, поэтому ограничение действует только на одну строку.
 * Вторая половина инварианта — «не менее одного» — обеспечивается первым запуском.
 */
@Entity(
    tableName = "profile",
    indices = [Index(value = ["self_slot"], unique = true)],
)
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "color") val color: Int,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
    @ColumnInfo(name = "mirror_reminders") val mirrorReminders: Boolean,
    @ColumnInfo(name = "self_slot") val selfSlot: String?,
) {
    companion object {
        /** Единственное допустимое значение слота владельца. */
        const val SELF_SLOT = "self"
    }
}
