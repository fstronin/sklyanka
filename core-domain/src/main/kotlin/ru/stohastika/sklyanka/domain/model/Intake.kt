package ru.stohastika.sklyanka.domain.model

import java.time.Instant
import java.time.LocalDateTime

/**
 * Один приём (docs/v2/10-behavior.md §4).
 *
 * Профиль приёма определяется через лекарство — собственного поля профиля здесь нет
 * намеренно: второй источник правды о принадлежности приёма означал бы чужой приём
 * в своём списке при первом же рассогласовании (ПР-10).
 */
@Suppress("LongParameterList")
data class Intake(
    /** Ключ идемпотентности отметки (ПР-2): телефон и часы адресуются одним и тем же id. */
    val id: String,
    val medicationId: String,
    /**
     * Назначенное время — локальные дата и время без пояса.
     *
     * Намерение в терминах стенных часов; смена пояса его не переписывает (НФТ-34).
     */
    val scheduledAt: LocalDateTime,
    val status: IntakeStatus,
    /** Момент отметки в UTC. Факт, по которому разрешается гонка часов и телефона (ПР-3). */
    val actedAt: Instant?,
    val source: IntakeSource?,
    /** Кто отметил, если профиль чужой (ПР-12). Основание для подписи «отметили вы». */
    val actorProfileId: String?,
) {
    /** Отметки ещё нет: время не настало либо окно не истекло. */
    val isPending: Boolean get() = status == IntakeStatus.PENDING
}
