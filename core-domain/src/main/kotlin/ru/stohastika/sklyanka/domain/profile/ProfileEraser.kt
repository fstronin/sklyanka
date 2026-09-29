package ru.stohastika.sklyanka.domain.profile

/**
 * Стирание профиля и всего, что ему принадлежит, одной транзакцией (ОВ-7, SKL-36).
 *
 * Порт, а не метод [ru.stohastika.sklyanka.domain.repository.ProfileRepository]:
 * удаление захватывает три таблицы, а репозиторий профиля описывает одну сущность.
 * Порядок «приёмы → лекарства → профиль» задан внешними ключами с RESTRICT
 * (`core-data/.../entity/MedicationEntity.kt`, `IntakeEntity.kt`), отложенные
 * срабатывания уходят каскадом вместе с приёмами.
 */
fun interface ProfileEraser {

    /**
     * Стирает данные профиля и сам профиль.
     *
     * Профиль владельца не удаляется и на уровне SQL (`is_self = 0`): домен проверяет
     * то же правило явно, чтобы исход был назван, а база держит его последней границей.
     */
    suspend fun erase(profileId: String)
}
