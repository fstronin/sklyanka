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
     *   Удалённое лекарство не архивируется (ИНВ-21) — запись не меняется, возвращается false.
     */
    suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean

    /**
     * Мягкое удаление ошибочно заведённой записи (ПМ-24, ПР-14).
     *
     * Условно по `deleted_at IS NULL` и отсутствию отметок `TAKEN` (ИНВ-22): границу
     * держит сама запись, а не только экран — между открытием карточки и подтверждением
     * отметку мог поставить другой путь. Поля архива очищаются одной записью (ИНВ-21),
     * строки `Intake` не трогаются.
     *
     * @return true, если строка изменена; повторное удаление и гонка с отметкой — false.
     */
    suspend fun delete(medicationId: String): Boolean

    /** Возврат удаления в коротком окне отмены: очистка `deleted_at`. true — поле было заполнено. */
    suspend fun undelete(medicationId: String): Boolean

    /** Записывает пересчитанный остаток. Арифметика — в домене, в SQL её нет вовсе. */
    suspend fun setStock(medicationId: String, stockLeft: BigDecimal?)
}
