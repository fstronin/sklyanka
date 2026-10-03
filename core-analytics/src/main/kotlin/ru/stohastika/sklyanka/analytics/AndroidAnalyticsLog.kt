package ru.stohastika.sklyanka.analytics

import android.util.Log
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Журнал аналитического контура поверх системного: отладочная сборка читает его
 * через logcat (НФТ-19, SKL-82). Интерфейс объявлен доменом — `core-domain`.
 */
@Singleton
class AndroidAnalyticsLog @Inject constructor() : AnalyticsLog {

    override fun activated() {
        Log.i(TAG, "analytics contour activated")
    }

    override fun deactivated() {
        Log.i(TAG, "analytics contour deactivated")
    }

    override fun unavailable(reason: String) {
        Log.i(TAG, "analytics contour unavailable: $reason")
    }

    override fun debugSuppressed() {
        Log.i(TAG, "analytics contour suppressed: debug build")
    }

    override fun sendFailed(reason: String) {
        Log.i(TAG, "analytics send failed: $reason")
    }

    override fun experimentsUpdated(count: Int) {
        Log.i(TAG, "experiments updated: $count")
    }

    override fun experimentsUnavailable(reason: String) {
        Log.i(TAG, "experiments unavailable: $reason")
    }

    private companion object {
        const val TAG = "SklyankaAnalytics"
    }
}
