package ru.stohastika.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Приём.
 *
 * Поля профиля здесь нет: профиль определяется через лекарство. Денормализация
 * сэкономила бы один JOIN и создала бы второй источник правды о принадлежности
 * приёма — то есть ровно тот класс ошибок, который запрещает ПР-10.
 *
 * [scheduleSlot] — служебная колонка, которой нет в модели данных: `S` у приёма по
 * расписанию и NULL у приёма по потребности. Уникальный индекс поверх тройки
 * (лекарство, назначенное время, слот) даёт уникальность только приёмам по расписанию
 * (INV-13), оставляя законными два приёма по потребности в одну минуту (INV-14).
 * Частичный индекс SQLite сделал бы то же самое, но Room о нём не знает и отказался бы
 * открыть базу при сверке схемы.
 *
 * [scheduledAt] — текст `YYYY-MM-DDTHH:mm`: лексикографический порядок совпадает
 * с хронологическим, сутки выбираются диапазоном через индекс, а смена часового пояса
 * не трогает ни одной строки (НФТ-34).
 */
@Entity(
    tableName = "intake",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["actor_profile_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["medication_id", "scheduled_at", "schedule_slot"], unique = true),
        Index(value = ["scheduled_at", "status"]),
        Index(value = ["actor_profile_id"]),
    ],
)
@Suppress("LongParameterList")
data class IntakeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "medication_id") val medicationId: String,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String,
    @ColumnInfo(name = "schedule_slot") val scheduleSlot: String?,
    @ColumnInfo(name = "status") val status: String,
    /** Момент отметки в UTC; NULL у ожидающего приёма (INV-10). */
    @ColumnInfo(name = "acted_at") val actedAt: Long?,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "actor_profile_id") val actorProfileId: String?,
    /**
     * Момент снятия записи в UTC; NULL — запись продукту видна (Р-46, SKL-127).
     *
     * Статус при снятии не меняется: снятие отвечает на вопрос «существует ли запись
     * для продукта», а статус — «что решил человек». Индекса по колонке нет: снятые
     * не выбираются никогда, и составной индекс обслуживал бы выборку без строк.
     */
    @ColumnInfo(name = "revoked_at") val revokedAt: Long?,
) {
    companion object {
        /** Значение слота у приёма по расписанию. */
        const val SCHEDULE_SLOT = "S"
    }
}
