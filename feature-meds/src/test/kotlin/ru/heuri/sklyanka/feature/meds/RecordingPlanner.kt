package ru.heuri.sklyanka.feature.meds

import ru.heuri.sklyanka.domain.schedule.GenerationWindow
import ru.heuri.sklyanka.domain.schedule.MedicationPlan
import ru.heuri.sklyanka.domain.schedule.MedicationPlanner
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanResult

/**
 * Перепланирование в памяти.
 *
 * Карточке и архиву от планировщика нужен только факт вызова и числа для журнала: сама
 * генерация проверена на JVM в `core-domain`, и повторять её здесь — проверять чужой код.
 */
internal class RecordingPlanner(private val created: Int = 0, private val deleted: Int = 0) : MedicationPlanner {

    val calls: MutableList<Pair<String, PlanReason>> = mutableListOf()

    override suspend fun plan(medicationId: String, reason: PlanReason): PlanResult {
        calls += medicationId to reason
        return PlanResult(
            window = GenerationWindow.of(MedsTestData.TODAY, GenerationWindow.MIN_DEPTH_DAYS),
            reason = reason,
            medications = listOf(
                MedicationPlan(
                    profileId = MedsTestData.PROFILE_ID,
                    medicationId = medicationId,
                    created = created,
                    deleted = deleted,
                    unchanged = 0,
                ),
            ),
        )
    }
}
