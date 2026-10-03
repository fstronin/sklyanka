package ru.stohastika.sklyanka.analytics

import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.VariantKey

/**
 * Техническая конвенция AGC Remote Configuration (SKL-83, TDD §10.1).
 *
 * Имя параметра выводится из имени эксперимента, значение — из имени варианта; консоль
 * AGC обязана совпасть с этой конвенцией (проверяется чек-листом устройства). Чистые
 * функции: тестируются на JVM без Android и без вендора. Незнакомое или недопустимое
 * значение — `null`, то есть базовый вариант, а не догадка.
 */
internal object ExperimentCodes {

    /** Имя параметра эксперимента: `experiment_today_empty_state`. */
    fun parameterName(experiment: ExperimentId): String = "experiment_" + experiment.name.lowercase()

    /** Вариант из значения консоли: `base`/`a`/…; значение вне каталога — `null`. */
    fun variantOf(experiment: ExperimentId, raw: String): VariantKey? = VariantKey.entries
        .firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?.takeIf { it in experiment.variants }
}
