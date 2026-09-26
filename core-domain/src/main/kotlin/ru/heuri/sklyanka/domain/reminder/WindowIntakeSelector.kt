package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import java.time.ZoneId

/**
 * Правило отбора приёмов окна — единственное место, где сходятся требования четырёх
 * областей (`TDD.md §5.2`).
 *
 * Приём попадает в план, если одновременно:
 * - момент срабатывания лежит в окне (НАП-7);
 * - приём ожидается: отмеченный и пропущенный о себе не напоминают (ПР-1);
 * - лекарство принимается по расписанию: «по потребности» планировать нечего (НАП-10);
 * - лекарство не в архиве (ПР-11);
 * - профиль свой либо у чужого включено дублирование напоминаний (ПРФ-6, НАП-12).
 *
 * Чистая функция без хранилища: пять условий проверяются тестом на JVM, а не на устройстве,
 * которого у сборки нет. Активный профиль в отборе не участвует вовсе — набор будильников
 * определяется свойствами профилей, а не тем, какой экран открыт (НАП-12).
 */
object WindowIntakeSelector {

    /**
     * Приёмы, о которых вообще положено напоминать: четыре условия, не зависящие от времени.
     *
     * Нужны отдельно от [select] ради снятия уже показанных карточек: приём, выбывший
     * из плана архивацией или истечением окна, обязан унести с собой и уведомление (НАП-34),
     * а окно планирования начинается «сейчас» и прошедший приём в него не попадает.
     */
    fun remindable(candidates: List<ReminderIntake>): List<ReminderIntake> = candidates.filter(::isRemindable)

    fun select(candidates: List<ReminderIntake>, window: ReminderWindow, zone: ZoneId): List<PlannedReminder> =
        candidates
            .asSequence()
            .filter(::isRemindable)
            .map { candidate -> candidate to candidate.scheduledAt.atZone(zone).toInstant() }
            .filter { (_, at) -> at in window }
            .map { (candidate, at) ->
                PlannedReminder(
                    intakeId = candidate.intakeId,
                    at = at,
                    scheduledAt = candidate.scheduledAt,
                    medicationId = candidate.medicationId,
                    profileId = candidate.profileId,
                )
            }
            // Порядок задан: ближайшее срабатывание — первое. По нему диагностика отвечает
            // на «что запланировано следующим» (НАП-37), а идентификатор добавлен вторым
            // ключом, чтобы набор был воспроизводим при совпадающем времени.
            .sortedWith(compareBy({ it.at }, { it.intakeId }))
            .toList()

    /** Признаки, не зависящие от времени: статус, режим, архив, профиль. */
    private fun isRemindable(candidate: ReminderIntake): Boolean = candidate.status == IntakeStatus.PENDING &&
        candidate.scheduleMode == ScheduleMode.SCHEDULED &&
        !candidate.medicationArchived &&
        (candidate.profileIsSelf || candidate.profileMirrorsReminders)
}
