package ru.heuri.sklyanka.domain.profile

import java.util.UUID

/**
 * Источник идентификаторов новых профилей.
 *
 * Интерфейс, а не вызов `UUID.randomUUID()` по месту: иначе проверка «созданный профиль
 * получает свой идентификатор и не затирает существующий» доказывала бы только то,
 * что случайные значения разные.
 */
interface ProfileIdSource {
    fun newId(): String
}

/** Рабочая реализация. `java.util` домену не запрещён — запрещены Android, Room и Dagger (НФТ-46). */
object UuidProfileIds : ProfileIdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}
