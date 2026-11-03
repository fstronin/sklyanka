package ru.stohastika.sklyanka.analytics

import android.app.Activity

/**
 * Тонкий интерфейс над статическими методами SDK провайдера (TDD §2.3).
 *
 * Существует ровно для того, чтобы решение «можно/нельзя», батчи и глотание отказов
 * проверялись unit-тестом: настоящий SDK требует процесса Android, а логика транспорта —
 * нет. Единственная реализация с `com.my.tracker.*` — [AndroidMyTrackerSdk]; остальной
 * модуль об SDK не знает, поэтому обновление версии поставщика меняет один класс.
 */
interface MyTrackerSdk {

    /** Инициализация SDK. Вызывается только из [MyTrackerTransport.start] (АН-4). */
    fun init(apiKey: String)

    /** Параметры передачи: период буферизации, авто-запуск сессии и режим отладки. */
    fun configure(bufferingPeriodSeconds: Int, trackingLaunchEnabled: Boolean, debug: Boolean)

    /** Одно событие каталога: имя и закрытые значения свойств (АН-3). */
    fun trackEvent(name: String, properties: Map<String, String>)

    /**
     * Привязка первой сессии к активности.
     *
     * Нужна потому, что согласие может прийти позже `Application.onCreate`: без якоря
     * поставщик не увидит установку и первую сессию (TDD §14, риск 2).
     */
    fun trackLaunchManually(activity: Activity)

    /** Отправить накопленное немедленно: после пачки у поставщика не остаётся запаса. */
    fun flush()
}
