package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.mapper.toDomain
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Контекст активного профиля для экранов: идентификатор из настроек, имя и цвет из базы.
 *
 * Один источник на всё приложение (`TDD.md §5.1`). Два независимых источника рано или
 * поздно разойдутся, и экран покажет индикацию одного профиля с данными другого — отказ
 * хуже отсутствия индикации, потому что человек ему верит.
 *
 * Имя и цвет читаются потоком, а не однократно: переименование профиля обязано доехать
 * до шапки и до баннера без перезапуска, иначе баннер называет профиль прежним именем
 * и говорит неправду о том, куда попадёт отметка.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveProfileContext @Inject constructor(
    private val active: ActiveProfileRepository,
    private val profiles: ProfileDao,
) : ProfileContextProvider {

    override val current: Flow<ProfileContext> = active.activeProfileId
        .flatMapLatest { profileId -> observe(profileId) }
        .filterNotNull()
        .distinctUntilChanged()

    override suspend fun snapshot(): ProfileContext? =
        profiles.getById(active.current())?.toDomain()?.let(ProfileContext::of)

    /**
     * Профиль по идентификатору — наблюдением за списком, а не разовым чтением.
     *
     * Отдельного наблюдения за одной строкой в DAO нет намеренно: профилей у человека
     * единицы (ПРФ-3), и второй запрос ради одной строки добавил бы место, где список
     * и строка расходятся.
     */
    private fun observe(profileId: String): Flow<ProfileContext?> = profiles.observeAll()
        .map { entities ->
            entities.firstOrNull { it.id == profileId }?.toDomain()?.let(ProfileContext::of)
        }
}
