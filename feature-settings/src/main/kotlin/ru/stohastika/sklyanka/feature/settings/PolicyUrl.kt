package ru.stohastika.sklyanka.feature.settings

import android.content.Context

/**
 * Адрес политики конфиденциальности на языке приложения (Р-91).
 *
 * Сайт публикует русскую, английскую и китайскую редакции. Русский интерфейс получает
 * русскую страницу, английский — английскую, китайский — китайскую. Любой другой язык
 * (немецкий, испанский, казахский и язык без своей локали приложения) получает английскую:
 * она понятнее русского, а политика всё равно говорит, что юридическую силу имеет
 * русская редакция.
 *
 * Адрес выбирается здесь, а не квалификатором ресурса, потому что запасной вариант
 * «всё остальное — английский» нельзя выразить одним `values-*`: русский — язык
 * по умолчанию, и `values-ru` заставил бы переводить все остальные строки заново.
 */
fun policyUrl(context: Context): String {
    val language = context.resources.configuration.locales[0].language
    return when (language) {
        "ru" -> context.getString(R.string.settings_policy_url_ru)
        "zh" -> context.getString(R.string.settings_policy_url_zh)
        else -> context.getString(R.string.settings_policy_url_en)
    }
}
