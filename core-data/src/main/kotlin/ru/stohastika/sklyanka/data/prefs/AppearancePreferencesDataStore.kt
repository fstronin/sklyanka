package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор оформления — в постоянных настройках приложения (Р-23, Р-31, §5.17).
 *
 * Хранилище то же, что у остальных настроек устройства (`DataModule.providePreferences`),
 * и по той же причине: тема и иконка — свойство не данных человека, а устройства.
 * В Room они не едут намеренно: файл переноса не должен нести оформление (ПЕР-37),
 * а строка в базе означала бы, что однажды её положат и в выгрузку.
 *
 * Хранится **ключ** варианта (латинское слово), а не имя: имена тем и вариантов иконки
 * переводятся (§10), а запись обязана читаться одинаково на всех шести языках.
 *
 * Незнакомая или испорченная запись читается как базовая — «Олива» и «Ледяная»:
 * оформление не данные человека, и запуск из-за него ломаться не должен. Сломать
 * запись может файл переноса или более новая сборка (МГ-6).
 */
@Singleton
class AppearancePreferencesDataStore @Inject constructor(private val store: DataStore<Preferences>) :
    AppearancePreferences {

    override val accentTheme: Flow<AccentTheme> = store.data.map { AccentTheme.ofKey(it[ACCENT_THEME]) }

    override val iconVariant: Flow<IconVariant> = store.data.map { IconVariant.ofKey(it[ICON_VARIANT]) }

    override suspend fun setAccentTheme(theme: AccentTheme) {
        store.edit { it[ACCENT_THEME] = theme.key }
    }

    override suspend fun setIconVariant(variant: IconVariant) {
        store.edit { it[ICON_VARIANT] = variant.key }
    }

    private companion object {
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val ICON_VARIANT = stringPreferencesKey("icon_variant")
    }
}
