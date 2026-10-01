package ru.stohastika.sklyanka.reminder

import ru.stohastika.sklyanka.domain.reminder.RescheduleOutcome
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.scheduler.IntakePlanningTrigger
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
 * Здесь же обратное направление — причина перепланирования по причине генерации.
 * Оно нужно порту [MedicationPlanner] (`MedicationCardModule`): сценарии лекарств
 * знают только свои причины генерации, а очередь будильников обязана получить
 * свою причину с той же гранулярностью (НАП-11): «добавлено лекарство» и «изменено
 * расписание» в журнале напоминаний различимы так же, как и в журнале генерации.
 *
 * Вынесено из класса, чтобы соответствие проверялось тестом на JVM без поднятия
 * планировщика: забытая причина не ломает ничего сразу — окно приёмов просто перестаёт
 * пополняться, и напоминания кончаются через неделю.
 */
internal object ReminderPlanReasons {

    /**
     * Соответствие «причина перепланирования → нужна ли генерация приёмов».
     *
     * Сложность — это перечисление причин таблицы НАП-11, а не ветвление логики:
     * каждая новая причина проходит через тест полноты, а не через ревью ветки.
     */
    @Suppress("CyclomaticComplexMethod")
    fun intakeReason(reason: RescheduleReason): PlanReason? = when (reason) {
        RescheduleReason.APP_START -> PlanReason.APP_START
        RescheduleReason.BOOT -> PlanReason.BOOT
        // Разблокировка — та же генерация, что после перезагрузки: устройство могло быть
        // выключено дольше горизонта, приёмы надо догенерировать (SKL-87).
        RescheduleReason.USER_UNLOCKED -> PlanReason.BOOT
        // До разблокировки работает только платформенный путь по снимку; полной пересборки нет.
        RescheduleReason.LOCKED_BOOT -> null
        RescheduleReason.PACKAGE_REPLACED -> PlanReason.APP_START
        RescheduleReason.TIMEZONE_CHANGED -> PlanReason.TIMEZONE_CHANGED
        RescheduleReason.TIME_CHANGED -> PlanReason.TIME_CHANGED
        RescheduleReason.SCHEDULE_EDITED -> PlanReason.MEDICATION_SAVED
        RescheduleReason.MEDICATION_ADDED -> PlanReason.MEDICATION_ADDED
        RescheduleReason.MEDICATION_ARCHIVED -> PlanReason.MEDICATION_ARCHIVED
        RescheduleReason.MEDICATION_UNARCHIVED -> PlanReason.MEDICATION_RESTORED
        RescheduleReason.ANCHOR -> PlanReason.ANCHOR_ALARM
        RescheduleReason.MANUAL -> PlanReason.MANUAL_CHECK
        RescheduleReason.DATA_IMPORTED -> PlanReason.DATA_IMPORTED
        // Выдача права на точные будильники план приёмов не меняет: генерация не нужна,
        // восстанавливается только очередь будильников (SKL-85, НАП-16).
        RescheduleReason.EXACT_ALARM_GRANTED -> null
        RescheduleReason.ALARM_FIRED, RescheduleReason.PROFILE_SET_CHANGED -> null
    }

    /**
     * Причина перепланирования будильников по причине генерации приёмов.
     *
     * Через [MedicationPlanner] проходят только причины изменения одного лекарства;
     * всё остальное — ошибка программиста, и отказ здесь обязателен: молчаливая
     * подмена причины переписала бы журнал напоминаний, по которому разбирают,
     * почему напоминание не пришло (НАП-14).
     */
    fun rescheduleReason(reason: PlanReason): RescheduleReason = when (reason) {
        PlanReason.MEDICATION_ADDED -> RescheduleReason.MEDICATION_ADDED
        PlanReason.MEDICATION_SAVED -> RescheduleReason.SCHEDULE_EDITED
        PlanReason.MEDICATION_ARCHIVED -> RescheduleReason.MEDICATION_ARCHIVED
        PlanReason.MEDICATION_RESTORED -> RescheduleReason.MEDICATION_UNARCHIVED
        else -> error("Generation for reason ${reason.code} does not change one medication: no reminder reason for it")
    }
}
