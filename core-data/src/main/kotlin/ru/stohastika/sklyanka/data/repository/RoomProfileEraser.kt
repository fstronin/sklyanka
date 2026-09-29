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
 * Профиль владельца не удаляется и на уровне запроса (`is_self = 0`): сценарий домена
 * проверяет то же правило явно, а база остаётся последней границей.
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
            intakes.deleteInProfile(profileId)
            medications.deleteInProfile(profileId)
            profiles.deleteById(profileId)
        }
    }
}
