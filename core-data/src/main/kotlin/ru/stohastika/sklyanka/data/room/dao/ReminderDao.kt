package ru.stohastika.sklyanka.data.room.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * Строка кандидата на напоминание: приём вместе с признаками лекарства и профиля.
 *
 * Названия лекарства здесь нет: эта строка уходит планировщику и в журнал, а там
 * медицинских данных быть не должно по построению (НФТ-19). Содержимое карточки читается
 * отдельно, обычным чтением лекарства.
 */
data class ReminderCandidateRow(
    @ColumnInfo(name = "intake_id") val intakeId: String,
    @ColumnInfo(name = "medication_id") val medicationId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "schedule_mode") val scheduleMode: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
    @ColumnInfo(name = "mirror_reminders") val mirrorReminders: Boolean,
)

/**
 * Выборки планировщика напоминаний.
 *
 * Единственное место в приложении, где приёмы читаются **без фильтра по профилю**, и это
 * осознанно: набор будильников не зависит от того, какой профиль открыт на экране, он
 * складывается из приёмов своего профиля и профилей с включённым дублированием
 * (НАП-12, ПРФ-6). Экранные выборки этим правом не пользуются — у них его нет в интерфейсе
 * (ПР-10).
 *
 * Условия отбора (статус, режим расписания, архив, дублирование) в SQL не проверяются
 * намеренно: правило живёт в домене и проверяется на JVM. Запрос отдаёт кандидатов окна,
 * а их в окне — единицы и десятки.
 */
@Dao
interface ReminderDao {

    @Query(
        """
        SELECT i.id AS intake_id, i.medication_id AS medication_id, m.profile_id AS profile_id,
               i.scheduled_at AS scheduled_at, i.status AS status, m.schedule_mode AS schedule_mode,
               m.archived_at AS archived_at, p.is_self AS is_self, p.mirror_reminders AS mirror_reminders
        FROM intake i
        JOIN medication m ON m.id = i.medication_id
        JOIN profile p ON p.id = m.profile_id
        WHERE i.scheduled_at >= :from AND i.scheduled_at <= :toInclusive
        ORDER BY i.scheduled_at
        """,
    )
    suspend fun candidates(from: String, toInclusive: String): List<ReminderCandidateRow>

    @Query(
        """
        SELECT i.id AS intake_id, i.medication_id AS medication_id, m.profile_id AS profile_id,
               i.scheduled_at AS scheduled_at, i.status AS status, m.schedule_mode AS schedule_mode,
               m.archived_at AS archived_at, p.is_self AS is_self, p.mirror_reminders AS mirror_reminders
        FROM intake i
        JOIN medication m ON m.id = i.medication_id
        JOIN profile p ON p.id = m.profile_id
        WHERE i.id = :intakeId
        """,
    )
    suspend fun candidate(intakeId: String): ReminderCandidateRow?

    /** Приёмы одного слота карточки: один профиль, одно назначенное время (НАП-36). */
    @Query(
        """
        SELECT i.id AS intake_id, i.medication_id AS medication_id, m.profile_id AS profile_id,
               i.scheduled_at AS scheduled_at, i.status AS status, m.schedule_mode AS schedule_mode,
               m.archived_at AS archived_at, p.is_self AS is_self, p.mirror_reminders AS mirror_reminders
        FROM intake i
        JOIN medication m ON m.id = i.medication_id
        JOIN profile p ON p.id = m.profile_id
        WHERE m.profile_id = :profileId AND i.scheduled_at = :scheduledAt
        ORDER BY i.medication_id
        """,
    )
    suspend fun slot(profileId: String, scheduledAt: String): List<ReminderCandidateRow>
}
