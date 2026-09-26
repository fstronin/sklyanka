package ru.heuri.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import ru.heuri.sklyanka.data.prefs.ActiveProfileDataStore
import ru.heuri.sklyanka.data.room.dao.ProfileDao
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Контекст активного профиля.
 *
 * Сохранённое значение проверяется на существование профиля: после загрузки файла
 * переноса в режиме «заменить всё» записанный идентификатор может указывать в пустоту,
 * и приложению нельзя открыться в несуществующем профиле. Запасной вариант всегда один
 * и тот же — профиль владельца (ПРФ-25).
 */
@Singleton
class ActiveProfileRepositoryImpl @Inject constructor(
    private val store: ActiveProfileDataStore,
    private val profiles: ProfileDao,
    private val log: OperationLog,
) : ActiveProfileRepository {

    override val activeProfileId: Flow<String> =
        store.storedProfileId.map { resolve(it) }.filterNotNull()

    override suspend fun current(): String = checkNotNull(resolve(store.read())) {
        "No profile exists yet: the first run has not created the owner profile"
    }

    override suspend fun switchTo(profileId: String) {
        val before = store.read()
        store.write(profileId)
        log.record(
            event = OpEvent.PROFILE_SWITCHED,
            profileId = profileId,
            before = before,
            after = profileId,
        )
    }

    private suspend fun resolve(stored: String?): String? =
        stored?.takeIf { profiles.getById(it) != null } ?: profiles.getSelf()?.id
}
