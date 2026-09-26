package ru.heuri.sklyanka.scheduler

import ru.heuri.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanResult
import ru.heuri.sklyanka.domain.schedule.PlanScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Точка запуска генерации приёмов из событий приложения и системы.
 *
 * Здесь только вход и его контракт: подписка на `BOOT_COMPLETED`, смену пояса и перевод
 * часов, а также якорный будильник — дело SKL-13 (НАП-11). Порядок, который эта точка
 * обязана удерживать, когда SKL-13 появится: **сначала генерация окна приёмов, потом
 * постановка будильников** на первые 48 часов. Обратный порядок ставит будильник на
 * `intake_id`, которого ещё нет, и оставляет осиротевшие будильники после правки
 * расписания (`docs/tasks/SKL-06/TDD.md §7`).
 */
@Singleton
class IntakePlanningTrigger @Inject constructor(private val planIntakes: PlanIntakesUseCase) {

    /** Полный обход: старт приложения, перезагрузка, смена пояса, перевод часов, якорь. */
    suspend fun planAll(reason: PlanReason): PlanResult = planIntakes(PlanScope.AllProfiles, reason)

    /**
     * Узкая область — одно лекарство: сохранение, архивация, возврат из архива.
     *
     * Пересчитывать все профили при каждом нажатии «Сохранить» и дороже, и шумнее
     * в журнале: по нему потом разбирают, куда делся вечерний приём.
     */
    suspend fun planMedication(medicationId: String, reason: PlanReason): PlanResult =
        planIntakes(PlanScope.Medication(medicationId), reason)
}
