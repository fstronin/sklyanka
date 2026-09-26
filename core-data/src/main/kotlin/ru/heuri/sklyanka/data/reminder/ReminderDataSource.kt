package ru.heuri.sklyanka.data.reminder

import ru.heuri.sklyanka.data.room.SklyankaConverters
import ru.heuri.sklyanka.data.room.dao.ReminderCandidateRow
import ru.heuri.sklyanka.data.room.dao.ReminderDao
import ru.heuri.sklyanka.domain.medication.doseUnit
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.reminder.ReminderCard
import ru.heuri.sklyanka.domain.reminder.ReminderCardItem
import ru.heuri.sklyanka.domain.reminder.ReminderIntake
import ru.heuri.sklyanka.domain.reminder.ReminderSlot
import ru.heuri.sklyanka.domain.reminder.port.ReminderCardSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Данные подсистемы напоминаний: кандидаты окна для планировщика и содержимое карточки.
 *
 * Один класс реализует два порта, и это не экономия файлов: у обоих портов один источник
 * данных, но разные права — планировщик видит только идентификаторы, карточка видит
 * название и дозировку. Права разделены типами ([ReminderIntake] против [ReminderCardItem]),
 * а не дисциплиной вызова, поэтому попасть названию лекарства в журнал неоткуда (НФТ-19).
 */
@Singleton
class ReminderDataSource @Inject constructor(
    private val dao: ReminderDao,
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
) : ReminderPlanSource,
    ReminderCardSource {

    override suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake> =
        dao.candidates(
            from = SklyankaConverters.dateTimeToText(from),
            toInclusive = SklyankaConverters.dateTimeToText(toInclusive),
        ).mapNotNull(::toDomain)

    override suspend fun intake(intakeId: String): ReminderIntake? = dao.candidate(intakeId)?.let(::toDomain)

    override suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake> = dao
        .slot(profileId = slot.profileId, scheduledAt = SklyankaConverters.dateTimeToText(slot.scheduledAt))
        .mapNotNull(::toDomain)
        .filter { it.status == IntakeStatus.PENDING }

    override suspend fun card(slot: ReminderSlot, mirroredToWatch: Boolean): ReminderCard? {
        val profile = profiles.get(slot.profileId) ?: return null
        val items = pendingInSlot(slot).mapNotNull { row ->
            medications.get(row.medicationId)?.let { medication ->
                ReminderCardItem(
                    intakeId = row.intakeId,
                    medicationId = medication.id,
                    medicationName = medication.name,
                    form = medication.form,
                    doseAmount = medication.doseAmount,
                    doseUnit = medication.form.doseUnit().code,
                    strength = medication.strength,
                    strengthUnit = medication.strengthUnit,
                )
            }
        }
        return ReminderCard(
            slot = slot,
            scheduledAt = slot.scheduledAt,
            items = items,
            mirroredToWatch = mirroredToWatch,
            profileIsSelf = profile.isSelf,
            profileName = profile.name,
        )
    }

    /**
     * Строка базы в проекцию домена.
     *
     * Незнакомое значение перечисления даёт null, а не исключение (МГ-6): запись,
     * пришедшая из файла переноса или от более новой сборки, не имеет права уронить
     * пересборку окна — иначе одна плохая строка отключает напоминания целиком.
     */
    private fun toDomain(row: ReminderCandidateRow): ReminderIntake? {
        val status = SklyankaConverters.textToEnum<IntakeStatus>(row.status)
        val mode = SklyankaConverters.textToEnum<ScheduleMode>(row.scheduleMode)
        val scheduledAt = SklyankaConverters.textToDateTimeOrNull(row.scheduledAt)
        return if (status == null || mode == null || scheduledAt == null) {
            null
        } else {
            ReminderIntake(
                intakeId = row.intakeId,
                medicationId = row.medicationId,
                profileId = row.profileId,
                scheduledAt = scheduledAt,
                status = status,
                scheduleMode = mode,
                medicationArchived = row.archivedAt != null,
                profileIsSelf = row.isSelf,
                profileMirrorsReminders = row.mirrorReminders,
            )
        }
    }
}
