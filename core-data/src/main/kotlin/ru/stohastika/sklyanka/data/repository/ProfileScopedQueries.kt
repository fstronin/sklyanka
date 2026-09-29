package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственное место, где активный профиль подставляется в выборку.
 *
 * Экранные слои не собирают запрос сами: метод репозитория требует профиль явным
 * параметром, а подстановка делается здесь — один раз и на виду (ПР-10, ПРФ-4).
 *
 * Смена профиля переключает поток целиком: старая подписка отменяется, новый запрос
 * выполняется уже в другом профиле. Промежуточного состояния, в котором видны приёмы
 * двух профилей, не существует — не потому, что его фильтруют, а потому, что второй
 * запрос не выполняется.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScopedQueries @Inject constructor(
    private val active: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
) {

    fun observeActiveMedications(): Flow<List<Medication>> =
        active.activeProfileId.flatMapLatest { medications.observeActive(it) }

    fun observeArchivedMedications(): Flow<List<Medication>> =
        active.activeProfileId.flatMapLatest { medications.observeArchived(it) }

    fun observeDay(date: LocalDate): Flow<List<Intake>> =
        active.activeProfileId.flatMapLatest { intakes.observeDay(it, date) }

    fun observePeriod(from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        active.activeProfileId.flatMapLatest { intakes.observePeriod(it, from, to) }
}
