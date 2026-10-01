package ru.stohastika.sklyanka.reminder

import ru.stohastika.sklyanka.domain.reminder.AcknowledgeDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.CancelDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.FinishDeliveryCheckOnEntryUseCase
import ru.stohastika.sklyanka.domain.reminder.StartDeliveryCheckUseCase
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckController
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связка самопроверки доставки для экрана (SKL-86, `ADR-0007`).
 *
 * Экран не знает сценариев по отдельности: запуск, закрытие по входу, отмена и
 * подтверждение — одна зависимость, собранная здесь. `feature-reminder` не зависит
 * от `app`, поэтому точка сборки живёт в `app`, как у «Проверить сейчас».
 */
@Singleton
class DeliveryCheckScenarios @Inject constructor(
    private val startCheck: StartDeliveryCheckUseCase,
    private val finishCheck: FinishDeliveryCheckOnEntryUseCase,
    private val cancelCheck: CancelDeliveryCheckUseCase,
    private val acknowledgeCheck: AcknowledgeDeliveryCheckUseCase,
) : DeliveryCheckController {

    override suspend fun start(): DeliveryCheckState = startCheck()

    override suspend fun finish(): DeliveryCheckState = finishCheck()

    override suspend fun cancel() = cancelCheck()

    override suspend fun acknowledge(dueAt: Instant) = acknowledgeCheck(dueAt)
}
