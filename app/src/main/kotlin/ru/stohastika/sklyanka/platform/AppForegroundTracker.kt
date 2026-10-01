package ru.stohastika.sklyanka.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import ru.stohastika.sklyanka.domain.reminder.port.AppForeground
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Передний план приложения счётом возобновлённых активностей (SKL-86, ОВ-108).
 *
 * Нужен одному вопросу: состоялась ли самопроверка. Если в момент тестового срабатывания
 * приложение видно человеку, показ не считается доставкой — фоновый путь при открытом
 * приложении не проверялся, и «не дошло» было бы ложью (НФТ-8).
 *
 * Счётчик, а не флаг: активностей в приложении несколько (главная, напоминание, проверка
 * напоминаний), и переход между ними — это `paused` одной и `resumed` другой. Флаг
 * в этот момент сказал бы «закрыто», и проверка не состоялась бы на ровном месте.
 *
 * Заблокированный телефон открытым приложением не считается: система не возобновляет
 * активности под блокировкой. Процесс, поднятый приёмником, тоже «закрыт» — возобновлённых
 * активностей у него нет вовсе, и это ровно то состояние, в котором проверка и проходит.
 */
@Singleton
class AppForegroundTracker @Inject constructor() :
    AppForeground,
    Application.ActivityLifecycleCallbacks {

    private var resumedActivities: Int = 0

    override fun isForeground(): Boolean = resumedActivities > 0

    override fun onActivityResumed(activity: Activity) {
        resumedActivities++
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivities > 0) resumedActivities--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
