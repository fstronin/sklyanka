package ru.stohastika.sklyanka.data.analytics

import javax.inject.Qualifier

/**
 * Квалификатор отдельного файла настроек согласия.
 *
 * Согласие — не настройка приложения: у него свой файл `sklyanka_analytics_consent`,
 * который не читает перенос данных (ПЕР-37). Общий `DataStore<Preferences>` остаётся
 * у настроек, поэтому два хранилища одного типа различаются этим квалификатором.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalyticsConsentPreferences
