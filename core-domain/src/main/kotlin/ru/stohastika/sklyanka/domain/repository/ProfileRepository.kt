package ru.stohastika.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /**
     * Сколько профилей заведено.
     *
     * Отдельный счётчик, а не `observeAll().size` у вызывающего: граница «сверх двух» и предел
     * пяти считаются по **числу заведённых** профилей (ПРФ-33, Р-84 пп. 1–3), и вопрос «сколько
     * их сейчас» задаётся в одном месте. Значение по умолчанию — пересчёт списка: фейкам тестов
     * этого достаточно, а база отвечает своим дешёвым `count()`.
     */
    suspend fun count(): Int = observeAll().first().size
}
