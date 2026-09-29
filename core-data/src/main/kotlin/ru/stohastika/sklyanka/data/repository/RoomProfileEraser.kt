package ru.stohastika.sklyanka.data.repository

import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.profile.ProfileEraser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Стирание профиля поверх Room (SKL-36, ОВ-7).
 *
 * **Одна транзакция на всё**: половина удалённого профиля — это лекарства без профиля
 * или приёмы без лекарства, то есть нарушенная целостность; откат возвращает состояние
 * целиком.
 *
 * Порядок обязателен и задан внешними ключами с RESTRICT: приёмы → лекарства → профиль.
 * Обратный порядок упёрся бы в ограничение и откатил транзакцию — ровно то, чего
 * от него ждут (`ReplaceAllDataTest` закрепляет тот же порядок для замены данных).
 * Отложенные срабатывания уходят каскадом вместе с приёмами.
 *
 * Профиль владельца и уже исчезнувший профиль не трогаются вовсе: проверка стоит
 * **до** удаления детей, иначе у владельца пропали бы данные, а строка осталась, —
 * и условие `is_self = 0` в SQL остаётся последней границей того же правила.
 */
@Singleton
class RoomProfileEraser @Inject constructor(
    private val profiles: ProfileDao,
    private val medications: MedicationDao,
    private val intakes: IntakeDao,
    private val transactor: Transactor,
) : ProfileEraser {

    override suspend fun erase(profileId: String) {
        transactor.inTransaction {
            val profile = profiles.getById(profileId) ?: return@inTransaction
            if (profile.isSelf) return@inTransaction
            intakes.deleteInProfile(profileId)
            medications.deleteInProfile(profileId)
            profiles.deleteById(profileId)
        }
    }
}
