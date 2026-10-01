package ru.stohastika.sklyanka.feature.reminder

import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import java.time.Instant

/**
 * Самопроверка доставки как зависимость экрана (SKL-86).
 *
 * Экран не знает доменных сценариев по отдельности: запуск, закрытие по входу, отмена
 * и подтверждение результата — одна точка входа, которую `app` собирает из пяти
 * сценариев (`ADR-0007`). Интерфейс, а не класс, ровно как `ReminderCheckNow`: стенды
 * приёмки подставляют сюда ту же связку настоящих сценариев, что и продукт.
 */
interface DeliveryCheckController {

    /** Назначить тестовое срабатывание; возвращает состояние «идёт». */
    suspend fun start(): DeliveryCheckState

    /** Закрыть истёкшую проверку при входе; состояние до срока не трогает. */
    suspend fun finish(): DeliveryCheckState

    /** Снять тестовое срабатывание по просьбе человека. */
    suspend fun cancel()

    /** Запомнить, что результат проверки с моментом [dueAt] человеку объявлен. */
    suspend fun acknowledge(dueAt: Instant)
}
