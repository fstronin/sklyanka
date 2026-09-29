package ru.stohastika.sklyanka.data.room.mapper

import ru.stohastika.sklyanka.data.room.entity.ProfileEntity
import ru.stohastika.sklyanka.domain.model.Profile

/**
 * Профиль: единственная сущность без перечислений, поэтому чтение не может не удаться.
 *
 * Колонка-слот заполняется при записи и игнорируется при чтении: в доменной модели её нет
 * и быть не должно — это механизм уникальности, а не свойство профиля.
 */
fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    name = name,
    color = color,
    isSelf = isSelf,
    mirrorReminders = mirrorReminders,
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    color = color,
    isSelf = isSelf,
    mirrorReminders = mirrorReminders,
    selfSlot = if (isSelf) ProfileEntity.SELF_SLOT else null,
)
