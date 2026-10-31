package ru.stohastika.sklyanka.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Адрес политики следует языку приложения, а не остаётся русским для всех (Р-91).
 *
 * Сайт публикует RU, EN и ZH; для языка без перевода — английская редакция.
 * Проверка не открывает браузер: она держит именно выбор адреса, а живое открытие
 * проверено на устройстве отдельно.
 */
@RunWith(AndroidJUnit4::class)
class PolicyUrlLocaleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Russian opens the canonical page`() {
        assertEquals(RU, url("ru-RU"))
    }

    @Test
    fun `English opens the English page`() {
        assertEquals(EN, url("en-US"))
    }

    @Test
    fun `Chinese opens the Chinese page`() {
        assertEquals(ZH, url("zh-CN"))
    }

    @Test
    fun `a language without a policy falls back to English`() {
        assertEquals(EN, url("de-DE"))
        assertEquals(EN, url("es-ES"))
        assertEquals(EN, url("kk-KZ"))
        assertEquals(EN, url("fr-FR"))
    }

    private fun url(tag: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return policyUrl(context.createConfigurationContext(configuration))
    }

    private companion object {
        const val RU = "https://stohastika.ru/ru/projects/sklyanka/privacy"
        const val EN = "https://stohastika.ru/en/projects/sklyanka/privacy"
        const val ZH = "https://stohastika.ru/zh/projects/sklyanka/privacy"
    }
}
