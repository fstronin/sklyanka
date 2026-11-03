package ru.stohastika.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Событие аналитики, ожидающее отправки (SKL-165, АН-4).
 *
 * Очередь живёт в базе, а не в памяти экрана: событие обязано пережить смерть процесса,
 * иначе «накопилось и уйдёт при следующем запуске» было бы неправдой.
 *
 * Внешних ключей здесь нет намеренно, как и в [OpLogEntity]: связать событие с лекарством,
 * приёмом или профилем нельзя не «по договорённости», а по схеме — в таблице нет ни
 * `profile_id`, ни `medication_id`, ни `intake_id`. Очистка профиля и перенос данных
 * поэтому не имеют к очереди никакого дела.
 *
 * `properties` — закрытый набор пар `ключ=значение` через `&`: значения берутся
 * из перечислений домена, свободной строке с медицинскими данными здесь взяться неоткуда.
 * Идентификатор — автоинкремент: нужен порядок записей, а не глобальная уникальность.
 *
 * Индекс по `occurred_at` — под выборку пачкой и вытеснение по сроку.
 */
@Entity(
    tableName = "analytics_event",
    indices = [Index(value = ["occurred_at"])],
)
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "properties") val properties: String,
    /** `product` либо `consent`: согласие решает, что из очереди вообще можно отправлять. */
    @ColumnInfo(name = "scope") val scope: String,
    /** Epoch millis UTC — только порядок отправки и срок хранения. */
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)
