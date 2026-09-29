package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.domain.profile.ProfileLossesSource
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Числа потерь профиля поверх обычных выборок (SKL-36).
 *
 * Отдельного счёта нет: лекарства считает тот же поток, что строит список (активные
 * и архивные), записи истории — соединение приёмов с лекарствами профиля. Второй
 * источник «сколько их» разошёлся бы с экраном, и числа потерь перестали бы обещать
 * то, что список показывает.
 */
@Singleton
class RoomProfileLossesSource @Inject constructor(
    private val medications: MedicationRepository,
    private val intakes: IntakeDao,
) : ProfileLossesSource {

    override fun observe(profileId: String): Flow<ProfileLosses> = combine(
        medications.observeActive(profileId),
        medications.observeArchived(profileId),
        intakes.observeCountInProfile(profileId),
    ) { active, archived, history ->
        ProfileLosses(
            medications = active.size + archived.size,
            archived = archived.size,
            intakes = history,
        )
    }
}
