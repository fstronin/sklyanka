package ru.stohastika.sklyanka.domain.transfer

import java.util.UUID

/**
 * Источник новых идентификаторов для слияния (ПЕР-9).
 *
 * Интерфейс, а не вызов `UUID.randomUUID()` по месту: перевыдача идентификаторов —
 * поведение, которое обязано проверяться тестом «идентификатор файла не навязан
 * устройству», а со случайным значением внутри этот тест доказывает только то,
 * что значения разные.
 */
interface TransferIdSource {
    fun newId(): String
}

/** Рабочая реализация. `java.util` домену не запрещён — запрещены Android, Room и Dagger (НФТ-46). */
object UuidIdSource : TransferIdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}
