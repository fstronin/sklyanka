package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.Instant

/**
 * Чем человек завершил приём.
 *
 * Отдельное перечисление, а не [IntakeStatus]: отметить приём «ожидающимся» или
 * «пропущенным без отметки» человек не может — первое не действие, второе ставит
 * само приложение. Тип, у которого нет невозможного значения, дешевле проверки.
 */
enum class MarkResult(val status: IntakeStatus) {
    TAKEN(IntakeStatus.TAKEN),
    SKIPPED(IntakeStatus.SKIPPED),
}

/**
 * Полностью разрешённая отметка, готовая к записи: профиль того, кто отметил, уже
 * вычислен, момент действия уже известен.
 *
 * Ключ операции — [intakeId], а не время получения события (ЧАС-23): телефон и часы
 * адресуются одним и тем же приёмом, и именно поэтому повторная отметка ничего не
 * создаёт.
 */
data class MarkCommand(
    val intakeId: String,
    val result: MarkResult,
    /** Откуда пришла отметка: `PHONE` или `WATCH`. `AUTO` ставит только проход по просроченным. */
    val source: IntakeSource,
    /**
     * Момент **действия** человека, а не доставки события (ПР-3, ЧАС-22).
     *
     * Нажатие на запястье старше нажатия в телефоне, даже если дошло на минуту позже;
     * гонка разрешается по этому полю, поэтому оно приходит снаружи, а не берётся
     * из часов в момент записи.
     */
    val actedAt: Instant,
    /** Профиль того, кто отметил, если приём чужой (ПР-12, ИНВ-18); иначе пусто. */
    val actorProfileId: String?,
)
