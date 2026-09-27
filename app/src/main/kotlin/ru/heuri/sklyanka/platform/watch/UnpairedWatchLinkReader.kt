package ru.heuri.sklyanka.platform.watch

import ru.heuri.sklyanka.domain.watch.WatchLinkState
import ru.heuri.sklyanka.domain.watch.WatchLinkStateReader
import javax.inject.Inject

/**
 * Состояние связи с часами до появления интеграции Wear Engine (SKL-16, SKL-17).
 *
 * Отвечает «связка не настроена», и это правда: приложение действительно не умеет пока
 * ни искать часы, ни отправлять на них карточку ([ru.heuri.sklyanka.domain.reminder.port.NoWatchGateway]).
 * Единственная альтернатива — не показывать строку вовсе, и она хуже: человек, купивший
 * часы, должен видеть в настройках, что приложение с ними ещё не связано, а не гадать,
 * работает ли оно молча.
 *
 * Отдельная реализация, а не значение по умолчанию в экране: подставить настоящее чтение
 * потом — заменить одну строку сборки, а не найти все места, где значение подразумевалось.
 */
class UnpairedWatchLinkReader @Inject constructor() : WatchLinkStateReader {

    override suspend fun read(): WatchLinkState = WatchLinkState.NotConfigured
}
