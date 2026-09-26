package ru.heuri.sklyanka.reminder

import ru.heuri.sklyanka.domain.reminder.RescheduleOutcome
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.scheduler.IntakePlanningTrigger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка входа событий перепланирования (НАП-11).
 *
 * Держит порядок, зафиксированный в документации [IntakePlanningTrigger]: **сначала
 * генерация окна приёмов, потом постановка будильников**. Обратный порядок ставит будильник
 * на `intake_id`, которого ещё нет, и оставляет осиротевшие будильники после правки
 * расписания (`docs/tasks/SKL-06/TDD.md §7`).
 *
 * Отдельный класс, а не расширение [IntakePlanningTrigger]: генерация приёмов — своя
 * подсистема со своими причинами и своим журналом, и сцеплять их в одном классе значит
 * заставлять каждую из них знать о причинах другой.
 */
@Singleton
class ReminderPlanningTrigger @Inject constructor(
    private val intakePlanning: IntakePlanningTrigger,
    private val reschedule: RescheduleRemindersUseCase,
) {

    /** Полный обход: старт, перезагрузка, обновление, смена пояса, перевод часов, якорь. */
    suspend fun planAll(reason: RescheduleReason): RescheduleOutcome {
        ReminderPlanReasons.intakeReason(reason)?.let { intakePlanning.planAll(it) }
        return reschedule(reason)
    }

    /**
     * Узкая область — одно лекарство: сохранение, архивация, возврат из архива.
     *
     * Набор будильников пересобирается целиком всё равно (НАП-9): частичное обновление
     * добавляет класс ошибок «забыли снять». Узкой здесь остаётся только генерация приёмов.
     */
    suspend fun planMedication(medicationId: String, reason: RescheduleReason): RescheduleOutcome {
        ReminderPlanReasons.intakeReason(reason)?.let { intakePlanning.planMedication(medicationId, it) }
        return reschedule(reason)
    }
}

/**
 * Причина генерации приёмов, соответствующая причине перепланирования будильников.
 *
 * `null` означает «приёмы генерировать не нужно»: срабатывание будильника и действия
 * с профилями состав приёмов не меняют, а лишний проход по всем профилям в приёмнике
 * стоит времени, которого у приёмника нет (НАП-13).
 *
 * Вынесено из класса, чтобы соответствие проверялось тестом на JVM без поднятия
 * планировщика: забытая причина не ломает ничего сразу — окно приёмов просто перестаёт
 * пополняться, и напоминания кончаются через неделю.
 */
internal object ReminderPlanReasons {

    fun intakeReason(reason: RescheduleReason): PlanReason? = when (reason) {
        RescheduleReason.APP_START -> PlanReason.APP_START
        RescheduleReason.BOOT -> PlanReason.BOOT
        RescheduleReason.PACKAGE_REPLACED -> PlanReason.APP_START
        RescheduleReason.TIMEZONE_CHANGED -> PlanReason.TIMEZONE_CHANGED
        RescheduleReason.TIME_CHANGED -> PlanReason.TIME_CHANGED
        RescheduleReason.SCHEDULE_EDITED, RescheduleReason.MEDICATION_ADDED -> PlanReason.MEDICATION_SAVED
        RescheduleReason.MEDICATION_ARCHIVED -> PlanReason.MEDICATION_ARCHIVED
        RescheduleReason.MEDICATION_UNARCHIVED -> PlanReason.MEDICATION_RESTORED
        RescheduleReason.ANCHOR -> PlanReason.ANCHOR_ALARM
        RescheduleReason.MANUAL -> PlanReason.MANUAL_CHECK
        RescheduleReason.DATA_IMPORTED -> PlanReason.DATA_IMPORTED
        RescheduleReason.ALARM_FIRED, RescheduleReason.PROFILE_SET_CHANGED -> null
    }
}
