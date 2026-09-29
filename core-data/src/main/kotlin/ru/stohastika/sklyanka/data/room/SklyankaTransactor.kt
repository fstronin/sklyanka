package ru.stohastika.sklyanka.data.room

import androidx.room.withTransaction
import ru.stohastika.sklyanka.domain.Transactor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Транзакционная точка входа поверх Room.
 *
 * Ею пользуются две операции, которые нельзя выполнить наполовину: отметка приёма
 * со списанием остатка (НФТ-11) и замена всех данных при загрузке файла переноса
 * (ПЕР-27). Отказ на любом шаге откатывает всё — не остаётся ни отметки без списания,
 * ни половины загруженного файла.
 */
@Singleton
class SklyankaTransactor @Inject constructor(private val database: SklyankaDatabase) : Transactor {

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
