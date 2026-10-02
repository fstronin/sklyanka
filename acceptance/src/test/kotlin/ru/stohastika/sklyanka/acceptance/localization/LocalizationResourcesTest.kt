package ru.stohastika.sklyanka.acceptance.localization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Полнота переводов (НФТ-42/43/44, ПМ-14): пропущенная строка — падение, а не русское
 * слово на казахском экране.
 *
 * Проверка читает **исходники ресурсов**, а не собранный артефакт: она должна называть
 * файл и ключ, которых не хватает, а не «где-то потерялся перевод». Ключи сравниваются
 * с русским набором `values/`; машиночитаемые `translatable="false"` в переводы
 * не входят и проверкой пропускаются.
 *
 * Правила счёта языка заданы таблицей, а не выведены из ICU: модуль приёмки — чистый
 * JVM (ADR-0007), а проверяется не ICU, а наличие форм, которые она сможет выбрать.
 */
class LocalizationResourcesTest {

    @Test
    fun `every declared language translates every base string of every module`() {
        modules().forEach { module ->
            val base = resourcesOf(module, BASE_QUALIFIER)
            val baseStrings = base.strings.filterValues { it.translatable }
            declaredLanguages().filterNot { it == RUSSIAN }.forEach { language ->
                val localized = resourcesOf(module, qualifierOf(language))
                assertTrue(
                    localized.strings.isNotEmpty(),
                    "$module has no ${qualifierOf(language)}/strings.xml: the language is declared supported",
                )
                baseStrings.forEach { (name, resource) ->
                    val translated = localized.strings[name]
                    assertTrue(
                        translated != null,
                        "missing translation: $module ${qualifierOf(language)} $name (${resource.value.take(PREVIEW)})",
                    )
                    assertTrue(
                        translated!!.value.isNotBlank(),
                        "empty translation: $module ${qualifierOf(language)} $name",
                    )
                    assertEquals(
                        placeholdersOf(resource.value),
                        placeholdersOf(translated.value),
                        "placeholders differ: $module ${qualifierOf(language)} $name",
                    )
                }
            }
        }
    }

    @Test
    fun `every declared language keeps the plural forms and placeholders of the base`() {
        modules().forEach { module ->
            val base = resourcesOf(module, BASE_QUALIFIER)
            declaredLanguages().filterNot { it == RUSSIAN }.forEach { language ->
                assertPluralsOf(module, language, base)
            }
        }
    }

    private fun assertPluralsOf(module: String, language: String, base: Resources) {
        val qualifier = qualifierOf(language)
        val localized = resourcesOf(module, qualifier)
        base.plurals.forEach { (name, quantities) ->
            val translated = localized.plurals[name]
            assertTrue(translated != null, "missing plurals: $module $qualifier $name")
            assertTrue(
                translated!!.containsKey(OTHER),
                "plurals without «other»: $module $qualifier $name",
            )
            requiredQuantities(language).forEach { quantity ->
                assertTrue(
                    translated.containsKey(quantity),
                    "plurals «$name» of $language has no «$quantity» form: $module $qualifier",
                )
            }
            assertEquals(
                quantities.values.flatMap(::placeholdersOf).distinct().sorted(),
                translated.values.flatMap(::placeholdersOf).distinct().sorted(),
                "placeholders differ: $module $qualifier $name",
            )
        }
    }

    @Test
    fun `non-russian translations contain no russian text`() {
        modules().forEach { module ->
            CYRILLIC_FREE_LANGUAGES.forEach { language ->
                val qualifier = qualifierOf(language)
                val localized = resourcesOf(module, qualifier)
                (localized.strings.mapValues { it.value.value } + localized.plurals.mapValues { it.value.values })
                    .forEach { (name, value) ->
                        val text = value.toString()
                        assertTrue(
                            !CYRILLIC.containsMatchIn(text),
                            "russian text in $language: $module $qualifier $name = «${text.take(PREVIEW)}»",
                        )
                    }
            }
        }
    }

    @Test
    fun `the declared languages are the same for the system, the build and the code`() {
        val declared = declaredLanguages()
        assertEquals(
            declared,
            localesConfig(),
            "locales_config.xml must declare exactly ProjectDefaults.INTERFACE_LOCALES",
        )
        val interfaceLocale = File(RepositoryLayout.root, INTERFACE_LOCALE_SOURCE).readText()
        val supported = SUPPORTED_LOCALE.findAll(interfaceLocale).map { it.groupValues[1] }.toSet()
        assertEquals(
            declared,
            supported,
            "InterfaceLocale.SUPPORTED must declare exactly ProjectDefaults.INTERFACE_LOCALES",
        )
        val plugin = File(RepositoryLayout.root, APPLICATION_PLUGIN_SOURCE).readText()
        assertTrue(
            plugin.contains("ProjectDefaults.INTERFACE_LOCALES"),
            "resourceConfigurations must take the declared languages from ProjectDefaults",
        )
    }

    @Test
    fun `the base resources stay russian and no russian qualifier is added`() {
        assertTrue(
            File(RepositoryLayout.root, "$APP_MODULE/src/main/res/values/strings.xml").isFile,
            "the base values/ set must stay in place: it is the fallback language",
        )
        assertTrue(
            modules().none { File(it, "src/main/res/$RUSSIAN_QUALIFIER").exists() },
            "russian must stay in values/: a values-ru set would drift from the fallback",
        )
    }

    private fun modules(): List<String> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.filter { File(it, "src/main/res/$BASE_QUALIFIER").isDirectory }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()

    private fun resourcesOf(module: String, qualifier: String): Resources {
        val directory = File(RepositoryLayout.root, "$module/src/main/res/$qualifier")
        val strings = mutableMapOf<String, StringResource>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        directory.listFiles()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.sortedBy { it.name }
            ?.forEach { file -> readInto(file, strings, plurals) }
        return Resources(strings, plurals)
    }

    private fun readInto(
        file: File,
        strings: MutableMap<String, StringResource>,
        plurals: MutableMap<String, Map<String, String>>,
    ) {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        for (index in 0 until root.childNodes.length) {
            val element = root.childNodes.item(index) as? Element ?: continue
            when (element.tagName) {
                STRING_TAG -> {
                    val name = element.getAttribute(NAME_ATTRIBUTE)
                    if (name.isNotEmpty()) {
                        strings[name] = StringResource(
                            value = element.textContent.trim(),
                            translatable = element.getAttribute(TRANSLATABLE_ATTRIBUTE) != "false",
                        )
                    }
                }
                PLURALS_TAG -> {
                    val name = element.getAttribute(NAME_ATTRIBUTE)
                    val items = element.getElementsByTagName(ITEM_TAG)
                    plurals[name] = (0 until items.length).associate { itemIndex ->
                        val item = items.item(itemIndex) as Element
                        item.getAttribute(QUANTITY_ATTRIBUTE) to item.textContent.trim()
                    }
                }
            }
        }
    }

    private fun declaredLanguages(): Set<String> {
        val defaults = File(RepositoryLayout.root, PROJECT_DEFAULTS_SOURCE).readText()
        val declaration = DECLARED_LOCALES.find(defaults)?.groupValues?.get(1)
            ?: error("INTERFACE_LOCALES is not declared in $PROJECT_DEFAULTS_SOURCE")
        return declaration.split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }.toSet()
    }

    private fun localesConfig(): Set<String> {
        val file = File(RepositoryLayout.root, "$APP_MODULE/src/main/res/xml/locales_config.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val locales = root.getElementsByTagName(LOCALE_TAG)
        return (0 until locales.length).mapNotNull { index ->
            val element = locales.item(index) as Element
            (0 until element.attributes.length)
                .map { element.attributes.item(it) }
                .firstOrNull { it.nodeName == ANDROID_NAME_ATTRIBUTE }
                ?.nodeValue
        }.toSet()
    }

    /** Android-квалификатор каталога для тега BCP-47: `en-US` → `values-en-rUS`. */
    private fun qualifierOf(language: String): String {
        val parts = language.split('-')
        val region = parts.getOrNull(1)
        return if (region == null) "values-${parts[0]}" else "values-${parts[0]}-r$region"
    }

    private fun requiredQuantities(language: String): Set<String> = when (language) {
        RUSSIAN -> setOf(ONE, FEW, MANY, OTHER)
        "en-US", "es", "de" -> setOf(ONE, OTHER)
        "kk" -> setOf(ONE, OTHER)
        "zh-CN" -> setOf(OTHER)
        else -> setOf(OTHER)
    }

    private fun placeholdersOf(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value.replace("%1\$", "%") }.sorted().toList()

    private data class StringResource(val value: String, val translatable: Boolean)

    private data class Resources(
        val strings: Map<String, StringResource>,
        val plurals: Map<String, Map<String, String>>,
    )

    private companion object {
        const val APP_MODULE = "app"
        const val BASE_QUALIFIER = "values"
        const val RUSSIAN_QUALIFIER = "values-ru"
        const val RUSSIAN = "ru"
        const val STRING_TAG = "string"
        const val PLURALS_TAG = "plurals"
        const val ITEM_TAG = "item"
        const val LOCALE_TAG = "locale"
        const val NAME_ATTRIBUTE = "name"
        const val ANDROID_NAME_ATTRIBUTE = "android:name"
        const val QUANTITY_ATTRIBUTE = "quantity"
        const val TRANSLATABLE_ATTRIBUTE = "translatable"
        const val ONE = "one"
        const val FEW = "few"
        const val MANY = "many"
        const val OTHER = "other"
        const val PREVIEW = 50

        /** Языки, письменность которых обязана обходиться без кириллицы (казахский — нет). */
        val CYRILLIC_FREE_LANGUAGES = setOf("en-US", "es", "de", "zh-CN")
        val CYRILLIC = Regex("[\\u0400-\\u04FF]")

        /** Подстановки: `%1$s`, `%2$d`, `%s` — с точностью до номера аргумента. */
        val PLACEHOLDER = Regex("""%(?:\d+\$)?[a-zA-Z]""")
        val SUPPORTED_LOCALE = Regex("""forLanguageTag\("([^"]+)"\)""")
        val DECLARED_LOCALES = Regex("""INTERFACE_LOCALES[^=]*=\s*linkedSetOf\(([^)]*)\)""")

        const val PROJECT_DEFAULTS_SOURCE =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ProjectDefaults.kt"
        const val APPLICATION_PLUGIN_SOURCE =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/" +
                "AndroidApplicationConventionPlugin.kt"
        const val INTERFACE_LOCALE_SOURCE =
            "core-ui/src/main/kotlin/ru/stohastika/sklyanka/ui/locale/InterfaceLocale.kt"
    }
}
