package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Instant

/**
 * Просьба отметить приём — то, что приходит с экрана, из карточки напоминания или с часов.
 *
 * [actedAt] пуст, когда отмечают здесь и сейчас: телефон ставит момент своими часами.
 * Часы присылают момент нажатия — свой, а не момент доставки события (ЧАС-23), иначе
 * «первая по времени» теряет смысл при задержке транспорта.
 */
data class MarkRequest(
    val intakeId: String,
    val result: MarkResult,
    val source: IntakeSource,
    val actedAt: Instant? = null,
)

/**
 * Единственная точка отметки приёма — для экрана «Сегодня», карточки напоминания
 * и колбэка Wear Engine.
 *
 * Единственная не для красоты: два пути записи означали бы два способа обойти проверку
 * статуса, а значит двойное списание остатка в гонке «часы против телефона» (ЧАС-24).
 * Сама проверка и запись выполняются хранилищем в одной транзакции; здесь — только то,
 * что для этого нужно знать заранее: кто отметил и когда.
 */
class MarkIntakeUseCase(
    private val intakes: IntakeRepository,
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
    private val time: TimeSource,
    /**
     * Последствия отметки за пределами данных: снятие напоминания и отзыв карточки часов
     * (SKL-13, НАП-30, ЧАС-16). По умолчанию отсутствуют — жизненный цикл приёма
     * о подсистеме доставки не знает.
     */
    private val sideEffects: MarkSideEffects = MarkSideEffects.None,
) {

    suspend operator fun invoke(request: MarkRequest): MarkOutcome {
        require(request.source != IntakeSource.AUTO) {
            "AUTO belongs to the sweep over expired intakes, not to a human mark"
        }
        // Приём и его лекарство читаются до транзакции: от них нужен только профиль
        // владельца лекарства, и от исхода отметки он не зависит (ПР-12). Решение же,
        // что делать с записью, принимается внутри транзакции — по её собственному чтению.
        val intake = intakes.get(request.intakeId)
        val medication = intake?.let { medications.get(it.medicationId) }
        if (medication == null) return MarkOutcome.NotFound
        val outcome = intakes.markAtomically(
            MarkCommand(
                intakeId = request.intakeId,
                result = request.result,
                source = request.source,
                actedAt = request.actedAt ?: time.now(),
                actorProfileId = actorFor(medication.profileId),
            ),
        )
        // Условие — «приём больше не ожидается», а не «отметка применена»: проигравшая
        // отметка с часов тоже обязана закрыть карточку на телефоне, иначе человек видит
        // напоминание о приёме, который уже отмечен (НАП-32, ЧАС-22).
        if (outcome.intake?.isPending == false) sideEffects.onIntakeMarked(request.intakeId)
        return outcome
    }

    /**
     * Кто отметил (ПР-12, ПРФ-14).
     *
     * Заполняется, только когда приём принадлежит чужому профилю: подпись «отметили вы»
     * в своей истории бессмысленна и зашумляет экран. Значение — профиль владельца
     * телефона, и оно заведомо не совпадает с профилем лекарства (ИНВ-18).
     *
     * Отметка с часов идёт по тому же правилу: она попадает в профиль приёма и активный
     * профиль на телефоне не трогает (ПРФ-22).
     */
    private suspend fun actorFor(medicationProfileId: String): String? {
        val self = profiles.getSelf() ?: return null
        return self.id.takeIf { it != medicationProfileId }
    }
}
