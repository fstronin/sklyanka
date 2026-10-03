package ru.stohastika.sklyanka.analytics

import com.huawei.agconnect.remoteconfig.AGConnectConfig
import com.huawei.agconnect.remoteconfig.ConfigValues
import com.huawei.hmf.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.ExperimentVendor
import ru.stohastika.sklyanka.domain.experiment.VariantKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AGC Remote Configuration — единственный класс продукта с импортом
 * `com.huawei.agconnect.remoteconfig` (SKL-83, TDD §10.2).
 *
 * Назначение вариантов приходит значениями Remote Configuration: A/B Testing на стороне
 * AGC сам решает, какая аудитория получает эксперимент, а приложение только читает
 * параметры. Прямое API `AGConnectABTesting` не вызывается — это локальная подмена
 * для предпросмотра.
 *
 * `fetch(0)` — интервал кэша в секундах: ноль означает «обновить при каждом запуске»,
 * а не ждать двенадцать часов по умолчанию. Верхнюю границу ожидания держит доменный
 * гейт (`ConsentedExperiments`), поэтому зависший сервис не блокирует сценарии.
 *
 * Отсутствие HMS Core, приложения AGC или `agconnect-services.json` не исключение
 * сценария: исключение уходит в гейт, варианты остаются базовыми, приложение работает
 * (НФТ-39, ПМ-20 п. 6).
 */
@Singleton
class HuaweiExperimentVendor @Inject constructor() : ExperimentVendor {

    override suspend fun fetchVariants(): Map<ExperimentId, VariantKey> {
        val config = AGConnectConfig.getInstance()
        await(config.fetch(FORCED_REFRESH_SECONDS))
        return ExperimentId.entries.mapNotNull { experiment ->
            val raw = config.getValueAsString(ExperimentCodes.parameterName(experiment))
                ?: return@mapNotNull null
            ExperimentCodes.variantOf(experiment, raw)?.let { experiment to it }
        }.toMap()
    }

    private suspend fun await(task: Task<ConfigValues>) {
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { completed ->
                if (!continuation.isActive) return@addOnCompleteListener
                val failure = completed.exception
                if (completed.isSuccessful && failure == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        failure ?: IllegalStateException("remote config fetch failed"),
                    )
                }
            }
        }
    }

    private companion object {

        /** Ноль секунд кэша: свежий вариант при каждом запуске, а не раз в 12 часов. */
        const val FORCED_REFRESH_SECONDS = 0L
    }
}
