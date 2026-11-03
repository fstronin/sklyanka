package ru.stohastika.sklyanka.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import com.my.tracker.MyTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Единственное место модуля, где названы классы SDK провайдера (TDD §2.3).
 *
 * Имена методов — из документации поставщика. Всё, что выше, работает с [MyTrackerSdk],
 * поэтому смена версии или провайдера не выходит за пределы этого файла.
 *
 * `bufferingPeriod = 1` и `flush()` после каждой пачки — не настройка ради настройки:
 * у SDK нет метода полного выключения, и «стоп» на нашей стороне перестаёт быть правдой,
 * если у поставщика на руках лежит накопленный батч (TDD §14, риск 1).
 */
class AndroidMyTrackerSdk @Inject constructor(@ApplicationContext context: Context) : MyTrackerSdk {

    // Application нужен SDK: инициализация привязана к процессу, а не к экрану.
    private val application: Application = context.applicationContext as Application

    override fun init(apiKey: String) {
        MyTracker.initTracker(apiKey, application)
    }

    override fun configure(bufferingPeriodSeconds: Int, trackingLaunchEnabled: Boolean, debug: Boolean) {
        MyTracker.setDebugMode(debug)
        MyTracker.getTrackerConfig().let { config ->
            config.setBufferingPeriod(bufferingPeriodSeconds)
            config.setTrackingLaunchEnabled(trackingLaunchEnabled)
            // Авто-отслеживание покупок гасится осознанно: события покупок вне каталога
            // этой задачи (Р-94), а клиент Google Play Billing из сборки снят.
            config.setAutotrackingPurchaseEnabled(false)
        }
    }

    override fun trackEvent(name: String, properties: Map<String, String>) {
        MyTracker.trackEvent(name, properties)
    }

    override fun trackLaunchManually(activity: Activity) {
        MyTracker.trackLaunchManually(activity)
    }

    override fun flush() {
        MyTracker.flush()
    }
}
