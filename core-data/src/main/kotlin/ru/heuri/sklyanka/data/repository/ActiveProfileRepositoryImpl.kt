package ru.heuri.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import ru.heuri.sklyanka.data.prefs.ActiveProfileDataStore
import ru.heuri.sklyanka.data.room.dao.ProfileDao
import ru.heuri.sklyanka.data.room.entity.ProfileEntity
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.profile.SwitchReason
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
 *
 * Восстановление инварианта не бывает тихим (ПРФ-31): оно оставляет след в журнале, иначе
 * смена профиля «сама собой» неразбираема, а для человека она выглядит как потерянные
 * данные.
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
        switchTo(profileId, SwitchReason.USER_SETTINGS)
    }

    override suspend fun switchTo(profileId: String, reason: SwitchReason) {
        val before = store.read()
        store.write(profileId)
        log.record(
            event = OpEvent.PROFILE_SWITCHED,
            profileId = profileId,
            before = before,
            after = profileId,
            detail = reason.code,
        )
    }

    /**
     * Сохранённое значение, приведённое к инварианту.
     *
     * Чтение из потока тоже проходит через приведение: профиль мог исчезнуть, пока экран
     * был открыт, и поток обязан переключиться на владельца сам, а не показывать данные
     * профиля, которого в базе больше нет.
     */
    private suspend fun resolve(stored: String?): String? {
        val existing = stored?.let { profiles.getById(it) }
        val owner = if (existing == null) profiles.getSelf() else null
        if (existing == null && owner != null && stored != null) restore(stored, owner)
        return (existing ?: owner)?.id
    }

    /**
     * Принудительная замена активного профиля на профиль владельца.
     *
     * Значение перезаписывается, а не только подменяется при чтении: иначе след
     * восстановления писался бы при каждом чтении, а сохранённое значение продолжало бы
     * указывать в пустоту до следующего осознанного переключения. После записи следующее
     * чтение находит инвариант выполненным и в журнал ничего не добавляет.
     */
    private suspend fun restore(stored: String, owner: ProfileEntity) {
        store.write(owner.id)
        log.record(
            event = OpEvent.PROFILE_ACTIVE_RESTORED,
            profileId = owner.id,
            before = stored,
            after = owner.id,
            detail = SwitchReason.INVARIANT_RESTORED.code,
        )
    }
}
