package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.mapper.toDomain
import ru.stohastika.sklyanka.data.room.mapper.toEntity
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Профили.
 *
 * Попытка завести второй профиль владельца отклоняется уникальным индексом по
 * колонке-слоту: отказ приходит из базы, а не из проверки в коде, и потому переживает
 * и параллельную запись, и загрузку файла переноса в обход домена (INV-1).
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(private val dao: ProfileDao) : ProfileRepository {

    override fun observeAll(): Flow<List<Profile>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun get(profileId: String): Profile? = dao.getById(profileId)?.toDomain()

    override suspend fun getSelf(): Profile? = dao.getSelf()?.toDomain()

    override suspend fun save(profile: Profile) {
        Invariants.requireValid(Invariants.violations(profile))
        val entity = profile.toEntity()
        if (dao.getById(profile.id) == null) dao.insert(entity) else dao.update(entity)
    }
}
