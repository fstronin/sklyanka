package ru.stohastika.sklyanka.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Отложенное срабатывание напоминания (SKL-35, ОВ-75).
 *
 * Отдельная таблица, а не поля `intake`: откладывание — состояние доставки, а не
 * медицинская запись. Запись живёт от нажатия «Отложить» (или решения о настойчивом
 * повторе, НАП-25) до следующего показа и умирает вместе с ним.
 *
 * Первичный ключ — идентификатор приёма: отложенное срабатывание у приёма ровно одно,
 * повторная запись заменяет предыдущую. Это идемпотентность на уровне схемы: два
 * восстановления не могут дать две записи, а значит и два будильника.
 *
 * [fireAt] — момент следующего показа, epoch millis UTC: точка на абсолютной шкале
 * («через 15 минут» от нажатия), смена пояса её не пересчитывает (НФТ-34). [attempt] —
 * номер предстоящего показа: 0 после ручного откладывания, 1… — настойчивый повтор;
 * без него предел повторов не пережил бы перезагрузку (ОВ-27).
 *
 * `ON DELETE CASCADE`: если приём исчезает (пересоздание будущих при правке расписания,
 * замена данных при переносе), откладывать больше нечего, и запись уходит вместе с ним.
 * `RESTRICT` справочных связей здесь запретил бы удалить приём с откладыванием.
 */
@Entity(
    tableName = "deferred_reminder",
    foreignKeys = [
        ForeignKey(
            entity = IntakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["intake_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
)
data class DeferredReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "intake_id") val intakeId: String,
    /** Момент следующего показа, epoch millis UTC. */
    @ColumnInfo(name = "fire_at") val fireAt: Long,
    /** Номер предстоящего показа: 0 — после откладывания, 1… — настойчивый повтор. */
    @ColumnInfo(name = "attempt") val attempt: Int,
)
