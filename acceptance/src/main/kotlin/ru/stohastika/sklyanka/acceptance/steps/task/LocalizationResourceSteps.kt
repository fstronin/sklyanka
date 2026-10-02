package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import org.w3c.dom.Element
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Структурные шаги SKL-62: полнота переводов по репозиторию.
 *
 * Проверка читает исходники `values-XX`, а не собранный артефакт: сообщение обязано
 * называть модуль и ключ, которых не хватает. Правило то же, что у guard-теста
 * `LocalizationResourcesTest`; шаг существует, чтобы сценарий приёмки был исполняемым,
 * а не описанием.
 */
class LocalizationResourceSteps {

    private var problems: List<String> = emptyList()

    @Когда("^разработчик читает ресурсы объявленных языков$")
    fun theDeveloperReadsTheResourcesOfTheDeclaredLanguages() {
        problems = declaredLanguages().filterNot { it == RUSSIAN }.flatMap { language ->
            modules().flatMap { module -> checkModule(module, language) }
        }
    }

    @Тогда("^каждый объявленный язык переводит все строки по всем модулям$")
    fun everyDeclaredLanguageTranslatesEveryString() {
        val missing = problems.filter { it.startsWith(MISSING) }
        assertTrue(missing.isEmpty(), "untranslated strings:\n" + missing.joinToString("\n"))
    }

    @Тогда("^ни одна переведённая строка не пуста$")
    fun noTranslatedStringIsEmpty() {
        val empty = problems.filter { it.startsWith(EMPTY) }
        assertTrue(empty.isEmpty(), "empty translations:\n" + empty.joinToString("\n"))
    }

    @Тогда("^каждый перевод сохраняет подстановки русской строки$")
    fun everyTranslationKeepsThePlaceholders() {
        val placeholders = problems.filter { it.startsWith(PLACEHOLDER) }
        assertTrue(placeholders.isEmpty(), "placeholder mismatches:\n" + placeholders.joinToString("\n"))
    }

    @Тогда("^у каждого plurals есть форма «other»$")
    fun everyPluralHasOther() {
        val missing = problems.filter { it.startsWith(NO_OTHER) }
        assertTrue(missing.isEmpty(), "plurals without «other»:\n" + missing.joinToString("\n"))
    }

    @Тогда("^у plurals каждого языка есть все формы, которые выбирает его правило$")
    fun everyPluralHasTheFormsOfItsRule() {
        val missing = problems.filter { it.startsWith(NO_QUANTITY) }
        assertTrue(missing.isEmpty(), "plurals without a required form:\n" + missing.joinToString("\n"))
    }

    @Тогда("^ни в одной строке английского, испанского, немецкого и китайского нет кириллицы$")
    fun nonRussianTranslationsContainNoCyrillic() {
        val cyrillic = problems.filter { it.startsWith(CYRILLIC) }
        assertTrue(cyrillic.isEmpty(), "russian text in translations:\n" + cyrillic.joinToString("\n"))
    }

    @Когда("^разработчик читает объявление языков$")
    fun theDeveloperReadsTheLanguageDeclaration() {
        problems = emptyList()
        declared = declaredLanguages()
    }

    @Тогда("^состав объявленных языков совпадает с `locales_config` и списком сборки$")
    fun theDeclaredLanguagesMatchTheSystemAndTheBuild() {
        assertTrue(declared.isNotEmpty(), "no declared languages found")
        assertTrue(
            declared == localesConfig(),
            "locales_config.xml must declare $declared",
        )
        val plugin = File(RepositoryLayout.root, APPLICATION_PLUGIN).readText()
        assertTrue(
            plugin.contains("ProjectDefaults.INTERFACE_LOCALES"),
            "resourceConfigurations must take the languages from ProjectDefaults",
        )
    }

    @Тогда("^сборка оставляет в артефакте ресурсы только объявленных языков$")
    fun theBuildKeepsOnlyTheDeclaredLanguages() {
        val localized = modules().flatMap { module ->
            File(RepositoryLayout.root, "$module/src/main/res").listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("values-") }
                ?.map { "$module/${it.name}" }
                .orEmpty()
        }
        val known = declared.filterNot { it == RUSSIAN }.map(::qualifierOf).toSet()
        val unexpected = localized.filterNot { path -> known.any { path.endsWith(it) } }
        assertTrue(unexpected.isEmpty(), "undeclared language resources:\n" + unexpected.joinToString("\n"))
    }

    private fun checkModule(module: String, language: String): List<String> {
        val prefix = "$module ${qualifierOf(language)}"
        val base = parse(File(RepositoryLayout.root, "$module/src/main/res/$BASE"))
        val localized = parse(File(RepositoryLayout.root, "$module/src/main/res/${qualifierOf(language)}"))
        val problems = mutableListOf<String>()
        base.strings.filterValues { it.translatable }.forEach { (name, resource) ->
            val translated = localized.strings[name]
            if (translated == null) {
                problems += "$MISSING $prefix $name"
                return@forEach
            }
            if (translated.value.isBlank()) problems += "$EMPTY $prefix $name"
            if (placeholders(resource.value) != placeholders(translated.value)) {
                problems += "$PLACEHOLDER $prefix $name"
            }
        }
        base.plurals.forEach { (name, quantities) ->
            val translated = localized.plurals[name]
            if (translated == null) {
                problems += "$MISSING $prefix $name"
                return@forEach
            }
            if (!translated.containsKey(OTHER)) problems += "$NO_OTHER $prefix $name"
            requiredQuantities(language).filterNot { translated.containsKey(it) }.forEach { quantity ->
                problems += "$NO_QUANTITY $prefix $name $quantity"
            }
            val expected = quantities.values.flatMap(::placeholders).distinct().sorted()
            val actual = translated.values.flatMap(::placeholders).distinct().sorted()
            if (expected != actual) problems += "$PLACEHOLDER $prefix $name"
        }
        if (language in CYRILLIC_FREE) {
            (localized.strings.mapValues { it.value.value } + localized.plurals.mapValues { it.value.values })
                .forEach { (name, value) ->
                    if (CYRILLIC_LETTERS.containsMatchIn(value.toString())) problems += "$CYRILLIC $prefix $name"
                }
        }
        return problems
    }

    private fun modules(): List<String> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.filter { File(it, "src/main/res/$BASE/strings.xml").isFile }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()

    private fun declaredLanguages(): Set<String> {
        val defaults = File(RepositoryLayout.root, PROJECT_DEFAULTS).readText()
        val declaration = DECLARED.find(defaults)?.groupValues?.get(1)
            ?: error("INTERFACE_LOCALES is not declared in $PROJECT_DEFAULTS")
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
                .firstOrNull { it.nodeName == ANDROID_NAME }
                ?.nodeValue
        }.toSet()
    }

    private fun parse(directory: File): Resources {
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
                    val name = element.getAttribute(NAME)
                    if (name.isNotEmpty()) {
                        strings[name] = StringResource(
                            element.textContent.trim(),
                            element.getAttribute(TRANSLATABLE) != "false",
                        )
                    }
                }
                PLURALS_TAG -> {
                    val name = element.getAttribute(NAME)
                    val items = element.getElementsByTagName(ITEM_TAG)
                    plurals[name] = (0 until items.length).associate { itemIndex ->
                        val item = items.item(itemIndex) as Element
                        item.getAttribute(QUANTITY) to item.textContent.trim()
                    }
                }
            }
        }
    }

    private fun qualifierOf(language: String): String {
        val parts = language.split('-')
        val region = parts.getOrNull(1)
        return if (region == null) "values-${parts[0]}" else "values-${parts[0]}-r$region"
    }

    private fun requiredQuantities(language: String): Set<String> = when (language) {
        "en-US", "es", "de", "kk" -> setOf(ONE, OTHER)
        "zh-CN" -> setOf(OTHER)
        else -> setOf(OTHER)
    }

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER_PATTERN.findAll(value).map { it.value.replace("%1\$", "%") }.sorted().toList()

    private data class StringResource(val value: String, val translatable: Boolean)

    private data class Resources(
        val strings: Map<String, StringResource>,
        val plurals: Map<String, Map<String, String>>,
    )

    private var declared: Set<String> = emptySet()

    private companion object {
        const val APP_MODULE = "app"
        const val BASE = "values"
        const val RUSSIAN = "ru"
        const val STRING_TAG = "string"
        const val PLURALS_TAG = "plurals"
        const val ITEM_TAG = "item"
        const val LOCALE_TAG = "locale"
        const val NAME = "name"
        const val QUANTITY = "quantity"
        const val TRANSLATABLE = "translatable"
        const val ANDROID_NAME = "android:name"
        const val ONE = "one"
        const val OTHER = "other"

        const val MISSING = "MISSING"
        const val EMPTY = "EMPTY"
        const val PLACEHOLDER = "PLACEHOLDER"
        const val NO_OTHER = "NO_OTHER"
        const val NO_QUANTITY = "NO_QUANTITY"
        const val CYRILLIC = "CYRILLIC"

        val CYRILLIC_FREE = setOf("en-US", "es", "de", "zh-CN")
        val CYRILLIC_LETTERS = Regex("[\\u0400-\\u04FF]")
        val PLACEHOLDER_PATTERN = Regex("""%(?:\d+\$)?[a-zA-Z]""")
        val DECLARED = Regex("""INTERFACE_LOCALES[^=]*=\s*linkedSetOf\(([^)]*)\)""")

        const val PROJECT_DEFAULTS =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ProjectDefaults.kt"
        const val APPLICATION_PLUGIN =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/" +
                "AndroidApplicationConventionPlugin.kt"
    }
}
