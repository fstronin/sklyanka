package ru.stohastika.sklyanka.domain.stock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Предупреждение о покупке для экрана «Сегодня» — одно, самое срочное, в контексте
 * ровно одного профиля (ПР-10, ПР-13).
 *
 * Профиль — обязательный параметр, а не значение, подобранное внутри: плашка о чужом
 * лекарстве в своём «Сегодня» — та же ошибка смешения профилей, что и чужой приём
 * в списке.
 */
class StockWarningForProfileUseCase(
    private val medications: MedicationRepository,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    /**
     * Предупреждение профиля; `null` — предупреждать не о чем.
     *
     * Появление и снятие предупреждения попадают в журнал: жалоба «плашка висит,
     * хотя я пополнил» разбирается только по переходам, потому что само состояние
     * нигде не хранится и восстановить его задним числом не из чего (`TDD.md §8`).
     */
    operator fun invoke(profileId: String): Flow<StockWarning?> = flow {
        var previous: StockWarning? = null
        medications.observeActive(profileId)
            .map { StockWarning.mostUrgent(it.filter(::isRunning)) }
            .distinctUntilChanged()
            .collect { current ->
                logTransition(profileId, previous, current)
                previous = current
                emit(current)
            }
    }

    /**
     * Идёт ли курс сегодня.
     *
     * У закончившегося курса предупреждение о покупке смысла не имеет: покупать нечего
     * и незачем. Архивация такой курс уберёт (ПР-11), но между последним днём и
     * архивацией есть зазор, и плашка в нём появляться не должна. Сегодняшняя дата
     * берётся из подменяемого источника времени (НФТ-48), а не у системных часов.
     */
    private fun isRunning(medication: Medication): Boolean {
        val today = time.today()
        return medication.endDate?.isBefore(today) != true
    }

    private suspend fun logTransition(profileId: String, previous: StockWarning?, current: StockWarning?) {
        when {
            current == null && previous != null -> log.record(
                event = OpEvent.STOCK_WARNING_CLEARED,
                profileId = profileId,
                medicationId = previous.medicationId,
                before = previous.daysLeft.toString(),
            )

            current != null && current.medicationId != previous?.medicationId -> log.record(
                event = OpEvent.STOCK_WARNING_RAISED,
                profileId = profileId,
                medicationId = current.medicationId,
                before = current.thresholdDays.toString(),
                after = current.daysLeft.toString(),
            )

            else -> Unit
        }
    }
}
