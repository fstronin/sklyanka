package ru.stohastika.sklyanka.feature.widgets.bindings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first

/**
 * Привязка экземпляра виджета к лекарству (SKL-118, `TDD.md §3`).
 *
 * У «по лекарству» выбор человека живёт у отдельного экземпляра плиты: два виджета
 * на домашнем экране могут показывать два разных лекарства. Хранится это в постоянных
 * настройках самого продукта — ключ `widget_bindings`, — а не в домене: домен про
 * виджеты не знает вовсе, и привязка — свойство поверхности, а не приёма.
 *
 * Ключ экземпляра — идентификатор виджета от платформы: он переживает перезапуск
 * процесса и не меняется, пока плита стоит на домашнем экране.
 */
class WidgetBindingStore(context: Context) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile(FILE_NAME)
    }

    /** Какое лекарство показывает этот экземпляр; `null` — привязки нет или плита не настроена. */
    suspend fun medicationId(widgetId: Int): String? = bindings()[widgetId]

    /** Запомнить выбор человека. Вызов — часть завершения настройки плиты (§5.12). */
    suspend fun bind(widgetId: Int, medicationId: String) {
        store.edit { preferences ->
            preferences[KEY] = withBinding(bindings(preferences), widgetId, medicationId)
        }
    }

    /** Плита снята с домашнего экрана: привязка больше никому не нужна. */
    suspend fun forget(widgetId: Int) {
        store.edit { preferences ->
            preferences[KEY] = withBinding(bindings(preferences), widgetId, null)
        }
    }

    private suspend fun bindings(): Map<Int, String> = bindings(store.data.first())

    private fun bindings(preferences: Preferences): Map<Int, String> = preferences[KEY]
        .orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            val widgetId = parts.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            val medicationId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            widgetId to medicationId
        }
        .toMap()

    private fun withBinding(current: Map<Int, String>, widgetId: Int, medicationId: String?): String {
        val next = if (medicationId == null) current - widgetId else current + (widgetId to medicationId)
        return next.entries.joinToString("\n") { (id, medication) -> "$id$SEPARATOR$medication" }
    }

    private companion object {
        /**
         * Файл постоянных настроек виджетов.
         *
         * Отдельный от настроек приложения: привязка — свойство поверхности, и её пропажа
         * не имеет права утащить за собой активный профиль.
         */
        const val FILE_NAME = "widget_bindings"

        val KEY = stringPreferencesKey("widget_bindings")

        const val SEPARATOR = "="
    }
}
