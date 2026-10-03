package ru.stohastika.sklyanka.data.experiment

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.experiment.ExperimentCacheStore
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.VariantKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш вариантов A/B в отдельном файле `sklyanka_experiment_cache` (SKL-83, TDD §9).
 *
 * Отдельный файл, а не общие ключи настроек: снимок не участвует в переносе данных
 * (ПЕР-37) и не сбрасывается заменой содержимого. Незнакомый ключ или значение
 * читаются как «варианта нет» — безопасное умолчание важнее догадки; значение, которого
 * эксперимент не допускает, тоже отбрасывается (ПМ-26: значения закрыты).
 */
@Singleton
class ExperimentCacheDataStore @Inject constructor(
    @ExperimentCachePreferences private val preferences: DataStore<Preferences>,
) : ExperimentCacheStore {

    override suspend fun read(): Map<ExperimentId, VariantKey> = variantsOf(preferences.data.first())

    override suspend fun write(variants: Map<ExperimentId, VariantKey>) {
        preferences.edit { stored ->
            // Снимок авторитетен: снятый эксперимент не остаётся в кэше.
            ExperimentId.entries.forEach { stored.remove(keyOf(it)) }
            variants.forEach { (experiment, variant) -> stored[keyOf(experiment)] = variant.name.lowercase() }
        }
    }

    private fun variantsOf(stored: Preferences): Map<ExperimentId, VariantKey> =
        ExperimentId.entries.mapNotNull { experiment ->
            val raw = stored[keyOf(experiment)] ?: return@mapNotNull null
            val variant = VariantKey.entries
                .firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?.takeIf { it in experiment.variants }
                ?: return@mapNotNull null
            experiment to variant
        }.toMap()

    companion object {

        /** Ключ одного эксперимента: имя выводится из каталога, руками не пишется. */
        fun keyOf(experiment: ExperimentId): Preferences.Key<String> =
            stringPreferencesKey("experiment_" + experiment.name.lowercase())

        /** Все ключи файла: опора теста «перенос не пишет сюда» и проверки состава. */
        val KEYS: Set<String> = ExperimentId.entries.map { keyOf(it).name }.toSet()
    }
}
