package ru.heuri.sklyanka.domain.reminder.port

import ru.heuri.sklyanka.domain.reminder.ReminderCard

/** Чем закончилась отправка карточки на часы. Уходит в журнал срабатываний (НФТ-50). */
enum class WatchSendResult(val code: String) {
    SENT("sent"),
    FAILED("failed"),
    SKIPPED("skipped"),

    /** Часы не настроены — штатное состояние, а не ошибка сценария (ЧАС-14). */
    NOT_CONFIGURED("not_configured"),
}

/**
 * Карточка на часах (ЧАС-13, ЧАС-16).
 *
 * Здесь только порт: Wear Engine, шаблон карточки и её отзыв — SKL-16. Порт заведён сейчас,
 * потому что точка вызова принадлежит показу напоминания, и дописывать её потом означало бы
 * трогать путь доставки напоминания второй раз.
 */
interface WatchGateway {

    suspend fun send(card: ReminderCard): WatchSendResult

    suspend fun revoke(intakeId: String)
}

/**
 * Заглушка до SKL-16: часы не настроены.
 *
 * Отдельная реализация, а не `null`-проверка по месту: недоступность часов не является
 * ошибкой сценария, приём отмечается на телефоне (ЧАС-14), и путь доставки не должен
 * знать, существует ли интеграция.
 */
object NoWatchGateway : WatchGateway {

    override suspend fun send(card: ReminderCard): WatchSendResult = WatchSendResult.NOT_CONFIGURED

    override suspend fun revoke(intakeId: String) = Unit
}
