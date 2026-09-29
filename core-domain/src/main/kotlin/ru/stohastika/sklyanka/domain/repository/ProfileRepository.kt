package ru.stohastika.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.model.Profile

/**
 * Профили.
 *
 * Единственная сущность, которую законно читать списком без фильтра: смешивание
 * запрещено для лекарств, приёмов и истории (ПРФ-4), а список профилей нужен
 * переключателю и настройкам.
 *
 * Метода удаления здесь нет: стирание профиля захватывает лекарства, приёмы и историю
 * и живёт отдельным портом `ProfileEraser` со сценарием `DeleteProfileUseCase`
 * (ОВ-7, SKL-36). Репозиторий остаётся описанием одной сущности.
 */
interface ProfileRepository {

    fun observeAll(): Flow<List<Profile>>

    suspend fun get(profileId: String): Profile?

    /** Профиль владельца приложения (INV-1). Null означает, что первый запуск ещё не отработал. */
    suspend fun getSelf(): Profile?

    suspend fun save(profile: Profile)
}
