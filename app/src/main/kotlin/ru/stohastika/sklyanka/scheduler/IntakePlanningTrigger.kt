package ru.stohastika.sklyanka.scheduler

import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.schedule.PlanScope
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
 * расписания (`docs/archive/tasks/SKL-06/TDD.md §7`).
 */
@Singleton
class IntakePlanningTrigger @Inject constructor(
    private val planIntakes: PlanIntakesUseCase,
    private val sweepMissed: SweepMissedIntakesUseCase,
) {

    /**
     * Полный обход: старт приложения, перезагрузка, смена пояса, перевод часов, якорь.
     *
     * Сначала проход по просроченным приёмам, потом генерация (НАП-34). Порядок не
     * косметический: вчерашние приёмы обязаны стать пропущенными **до** того, как
     * экран нарисует список дня, иначе человек увидит их ожидающимися и отметит
     * задним числом то, чего не было.
     */
    suspend fun planAll(reason: PlanReason): PlanResult {
        sweepMissed(sweepReason(reason))
        return planIntakes(PlanScope.AllProfiles, reason)
    }

    /**
     * Перевод просроченных приёмов без генерации — для вызова, у которого нет своей
     * причины перепланировать: будильник границы суток (SKL-13).
     */
    suspend fun sweepMissedIntakes(reason: SweepReason) = sweepMissed(reason)

    /** Старт приложения отличается от остальных причин только в журнале (`TDD.md §9`). */
    private fun sweepReason(reason: PlanReason): SweepReason =
        if (reason == PlanReason.APP_START) SweepReason.APP_START else SweepReason.REPLAN

    /**
     * Узкая область — одно лекарство: сохранение, архивация, возврат из архива.
     *
     * Пересчитывать все профили при каждом нажатии «Сохранить» и дороже, и шумнее
     * в журнале: по нему потом разбирают, куда делся вечерний приём.
     */
    suspend fun planMedication(medicationId: String, reason: PlanReason): PlanResult {
        sweepMissed(SweepReason.REPLAN)
        return planIntakes(PlanScope.Medication(medicationId), reason)
    }
}
