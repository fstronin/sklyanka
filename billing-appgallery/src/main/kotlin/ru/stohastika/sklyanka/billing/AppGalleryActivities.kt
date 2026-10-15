package ru.stohastika.sklyanka.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Активность на переднем плане для обращений к магазину (SKL-122).
 *
 * Клиент магазина требует активность: покупка открывает окно подтверждения, а «Управление
 * подпиской» — экран подписок магазина, и `startActivity` у результата принимает только
 * активность. Активность берётся у системы колбэками жизненного цикла, а не передаётся
 * экраном через домен: домен о активности не знает вовсе (`ADR-0007`, `ADR-0010` п. 2),
 * и порт магазина обязан работать с одним `Context`.
 *
 * Ссылка слабая: держать активность сильной ссылкой в синглтоне значит удержать её окно
 * после закрытия. Регистрация — при первом обращении, а не в конструкторе: контур
 * поднимается графом Hilt при запуске приложения, и работа в конструкторе синглтона
 * задерживала бы старт ради магазина.
 *
 * Счётчика возобновлённых активностей здесь нет намеренно: вопрос один — «есть ли активность,
 * которой можно показать окно магазина», и последняя возобновлённая на него отвечает.
 */
@Singleton
class AppGalleryActivities @Inject constructor(@ApplicationContext private val context: Context) :
    Application.ActivityLifecycleCallbacks {

    private var resumedActivity: WeakReference<Activity>? = null

    /**
     * Регистрация ровно один раз.
     *
     * `@Volatile`, а не обычное поле: `resumed()` зовут из разных потоков (шторка, проверка
     * при запуске), и без видимости записи два потока зарегистрировали бы колбэки дважды —
     * на устройстве это удвоенный счёт возобновлённых активностей.
     */
    @Volatile
    private var registered: Boolean = false

    /** Активность, которой можно показать окно магазина; `null` — приложение на заднем плане. */
    fun resumed(): Activity? {
        ensureRegistered()
        return resumedActivity?.get()
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun ensureRegistered() {
        if (registered) return
        registered = true
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
    }
}
