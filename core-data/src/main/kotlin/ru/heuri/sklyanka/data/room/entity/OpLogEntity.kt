package ru.heuri.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись журнала критичных операций.
 *
 * Живёт в той же базе, что и данные, чтобы запись в журнал шла той же транзакцией,
 * что и сама операция: журнал, который можно записать при откатившейся операции, врёт,
 * а врущий журнал хуже отсутствующего.
 *
 * Внешних ключей здесь нет намеренно — запись обязана пережить то, о чём она говорит,
 * иначе разбирать инцидент будет не по чему. Идентификатор — автоинкремент, а не UUID:
 * журнал не переносится, не сливается и нуждается в порядке.
 */
@Entity(
    tableName = "op_log",
    indices = [Index(value = ["at"])],
)
@Suppress("LongParameterList")
data class OpLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "at") val at: Long,
    @ColumnInfo(name = "event") val event: String,
    @ColumnInfo(name = "profile_id") val profileId: String?,
    @ColumnInfo(name = "medication_id") val medicationId: String?,
    @ColumnInfo(name = "intake_id") val intakeId: String?,
    @ColumnInfo(name = "value_before") val valueBefore: String?,
    @ColumnInfo(name = "value_after") val valueAfter: String?,
    @ColumnInfo(name = "source") val source: String?,
    /** Только служебные коды (`already_marked`, `full_disk`), не текст для человека. */
    @ColumnInfo(name = "detail") val detail: String?,
)
