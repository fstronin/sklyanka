package ru.heuri.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Лекарство с курсом и расписанием.
 *
 * Все поля — примитивы хранения: перечисления строками (МГ-6), дробные величины
 * текстом (иначе двоичная дробь уводит остаток в «-0,0000001» после сотни списаний
 * по 0,5 таблетки), назначенное время локальным текстом, момент архивации — числом
 * в UTC. Преобразование в доменные типы делает маппер, а не Room: конвертер Room
 * не умеет вернуть NULL для ненулевой колонки, а именно это нужно, чтобы незнакомое
 * значение перечисления не роняло чтение (МГ-6).
 *
 * Внешний ключ — RESTRICT: каскад означал бы, что одно нажатие стирает историю
 * профиля, а второй копии истории, кроме файла переноса, не существует (НФТ-13).
 */
@Entity(
    tableName = "medication",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["profile_id", "archived_at"])],
)
@Suppress("LongParameterList")
data class MedicationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "form") val form: String,
    @ColumnInfo(name = "shape") val shape: String?,
    @ColumnInfo(name = "dose_amount") val doseAmount: String,
    @ColumnInfo(name = "dose_unit") val doseUnit: String,
    @ColumnInfo(name = "strength") val strength: String?,
    @ColumnInfo(name = "strength_unit") val strengthUnit: String?,
    @ColumnInfo(name = "food_relation") val foodRelation: String,
    @ColumnInfo(name = "schedule_mode") val scheduleMode: String,
    @ColumnInfo(name = "max_per_day") val maxPerDay: Int?,
    /** Отсортированная строка `HH:mm,HH:mm`; пустая строка у курса «по потребности». */
    @ColumnInfo(name = "times") val times: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    /** NULL означает бессрочный курс (ПР-6), а не «дату забыли указать». */
    @ColumnInfo(name = "end_date") val endDate: String?,
    @ColumnInfo(name = "stock_left") val stockLeft: String?,
    @ColumnInfo(name = "stock_threshold_days") val stockThresholdDays: Int?,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "archive_reason") val archiveReason: String?,
)
