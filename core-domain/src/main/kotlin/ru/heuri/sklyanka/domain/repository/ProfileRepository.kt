package ru.heuri.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.heuri.sklyanka.domain.model.Profile

/**
 * Профили.
 *
 * Единственная сущность, которую законно читать списком без фильтра: смешивание
 * запрещено для лекарств, приёмов и истории (ПРФ-4), а список профилей нужен
 * переключателю и настройкам.
 *
 * Метода удаления здесь нет намеренно: до закрытия ОВ-7 профиль не удаляется вовсе,
 * потому что судьба его истории не решена, а второй копии истории не существует.
 */
interface ProfileRepository {

    fun observeAll(): Flow<List<Profile>>

    suspend fun get(profileId: String): Profile?

    /** Профиль владельца приложения (INV-1). Null означает, что первый запуск ещё не отработал. */
    suspend fun getSelf(): Profile?

    suspend fun save(profile: Profile)
}
