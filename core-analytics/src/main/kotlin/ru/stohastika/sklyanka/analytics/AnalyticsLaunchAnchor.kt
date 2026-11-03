package ru.stohastika.sklyanka.analytics

import android.app.Activity

/**
 * Якорь запуска для поставщика (TDD §14, риск 2).
 *
 * Согласие может быть дано позже `Application.onCreate`, поэтому SDK инициализируется
 * не на старте процесса. Чтобы первая сессия и установка не потерялись, активность
 * сообщает о себе явно (`trackLaunchManually`). Носитель порта — тот же адаптер:
 * активности не нужно знать ни имени SDK, ни его методов, только [attach].
 */
interface AnalyticsLaunchAnchor {

    /** Привязать запуск к активности. До согласия (до `start()`) не делает ничего. */
    fun attach(activity: Activity)
}
