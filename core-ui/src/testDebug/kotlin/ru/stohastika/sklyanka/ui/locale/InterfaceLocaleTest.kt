package ru.stohastika.sklyanka.ui.locale

import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Выбор языка интерфейса (SKL-62, НФТ-43а, ПМ-14).
 *
 * Проверяется не наличие строк, а правило выбора: объявленный язык телефона сохраняется
 * целиком (`es-MX` → `es`), незнакомый даёт русский, а порядок предпочтений человека
 * не переворачивается. Ошибка здесь вернула бы дефект SKL-27 — русские строки
 * с чужими правилами склонения — или показала бы иностранцу русский интерфейс.
 */
@RunWith(AndroidJUnit4::class)
class InterfaceLocaleTest {

    @Test
    fun `a declared language of the device is kept`() {
        assertEquals(locale("es"), InterfaceLocale.resolve(listOf("es-ES")))
    }

    @Test
    fun `a regional variant finds the declared language`() {
        assertEquals(locale("en-US"), InterfaceLocale.resolve(listOf("en-GB")))
        assertEquals(locale("kk"), InterfaceLocale.resolve(listOf("kk-KZ")))
    }

    @Test
    fun `traditional chinese gets the simplified translation`() {
        assertEquals(locale("zh-CN"), InterfaceLocale.resolve(listOf("zh-TW")))
    }

    @Test
    fun `an unknown language falls back to russian`() {
        assertEquals(InterfaceLocale.FALLBACK, InterfaceLocale.resolve(listOf("fr-FR")))
        assertEquals(InterfaceLocale.FALLBACK, InterfaceLocale.resolve(listOf("nl-NL", "uk-UA")))
    }

    @Test
    fun `the first declared language of the device preference wins`() {
        assertEquals(locale("de"), InterfaceLocale.resolve(listOf("fr-FR", "de-AT", "en-US")))
    }

    @Test
    fun `the declared set is the one of the product decision`() {
        assertEquals(
            listOf("ru", "en-US", "es", "de", "zh-CN", "kk"),
            InterfaceLocale.SUPPORTED.map { it.toLanguageTag() },
        )
    }

    private fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

    private fun InterfaceLocale.resolve(tags: List<String>): Locale =
        resolve(LocaleList(*tags.map { Locale.forLanguageTag(it) }.toTypedArray()))
}
