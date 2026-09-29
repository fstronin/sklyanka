package ru.stohastika.sklyanka.data.repository

import ru.stohastika.sklyanka.data.prefs.ActiveProfileDataStore
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.mapper.toDomain
import ru.stohastika.sklyanka.data.room.mapper.toEntity
import ru.stohastika.sklyanka.domain.model.Profile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Первый запуск: профиль владельца существует до всякого онбординга (ПРФ-25).
 *
 * Данные не могут оказаться без владельца, поэтому проверка делается при каждом старте,
 * а не только при создании базы: базу мог создать перенос данных или миграция.
 *
 * Имя профиля приходит снаружи — из ресурсов приложения. Русского текста в слое данных
 * нет вообще (НФТ-42), и это не формальность: имя по умолчанию человек увидит на экране.
 */
@Singleton
class ProfileBootstrap @Inject constructor(
    private val profiles: ProfileDao,
    private val activeProfile: ActiveProfileDataStore,
) {

    /**
     * @param defaultName имя профиля владельца по умолчанию (строковый ресурс приложения)
     * @param color цвет профиля из палитры дизайн-системы
     * @return профиль владельца — уже существовавший или только что созданный
     */
    suspend fun ensureOwnerProfile(defaultName: String, color: Int): Profile {
        val existing = profiles.getSelf()
        val owner = existing?.toDomain() ?: createOwner(defaultName, color)
        val stored = activeProfile.read()
        // Пустое значение или ссылка в несуществующий профиль — оба случая означают
        // одно: открыться не в чем. Запасной вариант всегда профиль владельца.
        if (stored == null || profiles.getById(stored) == null) {
            activeProfile.write(owner.id)
        }
        return owner
    }

    private suspend fun createOwner(defaultName: String, color: Int): Profile {
        val owner = Profile(
            id = UUID.randomUUID().toString(),
            name = defaultName,
            color = color,
            isSelf = true,
            mirrorReminders = false,
        )
        profiles.insert(owner.toEntity())
        return owner
    }
}
