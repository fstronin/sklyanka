package ru.stohastika.sklyanka.domain.analytics

import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.VariantKey

/**
 * Экспонирование A/B-эксперимента (ПМ-26; SKL-83 TDD §13).
 *
 * Свой каталог задачи: событие лежит отдельным файлом, поэтому приёмочный счёт тринадцати
 * базовых и трёх зарезервированных событий SKL-82 не меняется. Свойства — только закрытые
 * перечисления каталога экспериментов; ни идентификаторов, ни времён, ни свободного текста
 * сюда передать нельзя. Отправляется гейтом [ru.stohastika.sklyanka.domain.experiment.
 * ConsentedExperiments] при первом чтении не-base варианта за процесс.
 */
data class ExperimentExposed(val experiment: ExperimentId, val variant: VariantKey) : AnalyticsEvent
