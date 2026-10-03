package ru.stohastika.sklyanka.data.experiment

import javax.inject.Qualifier

/**
 * Квалификатор отдельного файла кэша вариантов A/B.
 *
 * Снимок вариантов — не настройка приложения: у него свой файл
 * `sklyanka_experiment_cache`, который не читает перенос данных (ПЕР-37). Общий
 * `DataStore<Preferences>` остаётся у настроек, поэтому два хранилища одного типа
 * различаются этим квалификатором.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentCachePreferences
