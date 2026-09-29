package ru.stohastika.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal

/**
 * Лекарства и курсы.
 *
 * `profileId` — обязательный параметр, а не значение, подобранное внутри реализации:
 * метод без профиля выглядит одинаково правильно и в верном, и в ошибочном вызове,
 * а метод с профилем заставляет вызывающего предъявить контекст (ПР-10, ПРФ-4).
 */
interface MedicationRepository {

    fun observeActive(profileId: String): Flow<List<Medication>>

    fun observeArchived(profileId: String): Flow<List<Medication>>

    suspend fun get(medicationId: String): Medication?

    suspend fun save(medication: Medication)

    /**
     * Мягкое удаление: заполняет момент и причину архивации вместе (INV-9).
     *
     * @return true, если курс был активен и архивация действительно произошла.
     *   Повторная архивация — не ошибка, но и не событие: в журнал уходит только первая.
     */
    suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean

    /** Записывает пересчитанный остаток. Арифметика — в домене, в SQL её нет вовсе. */
    suspend fun setStock(medicationId: String, stockLeft: BigDecimal?)
}
