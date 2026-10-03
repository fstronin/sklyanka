package ru.stohastika.sklyanka.analytics

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.huawei.hms.analytics.HiAnalytics
import com.huawei.hms.analytics.HiAnalyticsInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Настоящий SDK вендора — единственный класс продукта с импортом `com.huawei.hms.analytics`
 * (TDD §5.3). Всё остальное общается с аналитикой через порт домена.
 *
 * SDK обязан инициализироваться на главном потоке: иначе `HiAnalytics.getInstance`
 * возвращает null и пишет в свой журнал «IE-001». Поэтому активация и события идут
 * через главный `Handler`, а активация дожидается выполнения — событие, ушедшее
 * в неактивированный SDK, теряется молча.
 *
 * Отсутствие HMS Core или конфигурации вендора не исключение сценария: сбой активации
 * логируется и дальше контур просто молчит (НФТ-39).
 */
@Singleton
class HuaweiAnalyticsSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: AnalyticsLog,
) : AnalyticsSink {

    private val main = Handler(Looper.getMainLooper())

    override suspend fun activate() = onMain { it.setAnalyticsEnabled(true) }

    override suspend fun deactivate() = onMain { it.setAnalyticsEnabled(false) }

    override fun send(event: AnalyticsEvent) {
        main.post {
            runCatching {
                val vendor = event.toVendorEvent()
                instance().onEvent(vendor.name, vendor.params)
            }.onFailure { log.sendFailed(it::class.simpleName.orEmpty()) }
        }
    }

    private suspend fun onMain(action: (HiAnalyticsInstance) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            main.post {
                runCatching {
                    action(instance())
                }.onFailure { log.unavailable(it::class.simpleName.orEmpty()) }
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /** `getInstance` требует главного потока и разблокированного пользователя, иначе возвращает null. */
    private fun instance(): HiAnalyticsInstance =
        requireNotNull(HiAnalytics.getInstance(context)) { "HiAnalytics instance is unavailable" }
}
