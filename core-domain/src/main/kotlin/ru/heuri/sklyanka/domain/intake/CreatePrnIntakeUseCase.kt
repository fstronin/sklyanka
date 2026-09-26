package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime

/**
 * Создание приёма по потребности: запись рождается уже отмеченной.
 *
 * Назначенное время равно моменту отметки (ПР-4, ИНВ-14): записи «ожидается» для приёма
 * по потребности не существует вовсе — напоминать о ней некому и нечего.
 */
data class PrnCommand(
    val medicationId: String,
    val actedAt: Instant,
    /** Назначенное время — оно же местное время отметки, с точностью до минуты (ИНВ-14). */
    val scheduledAt: LocalDateTime,
    val source: IntakeSource,
    val actorProfileId: String?,
)

/** Чем кончилось нажатие «Принять» для лекарства по потребности. */
sealed interface PrnOutcome {

    data class Created(val intake: Intake) : PrnOutcome

    /**
     * Суточный лимит уже выбран (ИНВ-17): новой записи нет, число приёмов за сутки
     * не выросло, остаток не тронут.
     */
    data class RejectedDailyLimit(val limit: Int, val countToday: Int) : PrnOutcome

    data object UnknownMedication : PrnOutcome

    /** Лекарство принимается по расписанию: его приёмы порождает планировщик (SKL-06). */
    data object NotOnDemand : PrnOutcome
}

/**
 * Суточный лимит приёмов по потребности — одно правило на домен и на хранилище.
 *
 * Вынесено функцией, потому что проверять лимит обязаны оба: домен — чтобы правило
 * было проверяемо на JVM, хранилище — чтобы проверка и вставка попали в одну транзакцию
 * (два быстрых нажатия подряд иначе дают четвёртый приём при лимите три).
 */
object PrnDailyLimit {

    /** Пустой предел означает «без ограничения» (ПР-7): ноль там запрещён инвариантом. */
    fun reached(maxPerDay: Int?, countToday: Int): Boolean = maxPerDay != null && countToday >= maxPerDay
}

/**
 * Нажатие «Принять» для лекарства по потребности.
 *
 * Сутки лимита — календарные, по местной полуночи устройства (НФТ-37), а не «минус
 * 24 часа»: в сутках с переводом времени их 23 или 25, и лимит, посчитанный вычитанием,
 * то запрещает законный приём, то пропускает лишний.
 */
class CreatePrnIntakeUseCase(
    private val intakes: IntakeRepository,
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
    private val time: TimeSource,
) {

    suspend operator fun invoke(medicationId: String, source: IntakeSource, actedAt: Instant? = null): PrnOutcome {
        require(source != IntakeSource.AUTO) { "An on-demand intake is created by a person, not by the app" }
        val medication = medications.get(medicationId)
        val moment = actedAt ?: time.now()
        return when {
            medication == null -> PrnOutcome.UnknownMedication
            medication.scheduleMode != ScheduleMode.PRN -> PrnOutcome.NotOnDemand
            else -> intakes.createPrnAtomically(
                PrnCommand(
                    medicationId = medicationId,
                    actedAt = moment,
                    // Секунды отбрасываются: назначенное время хранится с точностью до минуты,
                    // и без обрезки ИНВ-14 («scheduled_at == acted_at») ломался бы на секундах.
                    scheduledAt = moment.atZone(time.zone()).toLocalDateTime().withSecond(0).withNano(0),
                    source = source,
                    actorProfileId = actorFor(medication.profileId),
                ),
            )
        }
    }

    /** То же правило, что и у обычной отметки (ПР-12): чужой профиль подписывается владельцем. */
    private suspend fun actorFor(medicationProfileId: String): String? {
        val self = profiles.getSelf() ?: return null
        return self.id.takeIf { it != medicationProfileId }
    }
}
