package ru.stohastika.sklyanka.acceptance

import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Каталог событий как проверяемый факт (АН-3, ПМ-26).
 *
 * Сценарии о сети утверждают не только «кто объявил разрешение», но и «что вообще может
 * уйти»: единственный сетевой путь — контур аналитики, а он способен передать только
 * события каталога. Отсюда три проверки, и все три — по скомпилированному домену,
 * а не по тексту документации:
 *
 * 1. каждое событие реализует [AnalyticsEvent] и объявлено в доменном модуле;
 * 2. ни один конструктор события не принимает свободный текст или идентификатор —
 *    только закрытые перечисления каталога, поэтому название лекарства, доза, время
 *    приёма и идентификатор профиля не пролезают в событие конструкцией;
 * 3. имена событий — ровно проводные имена каталога, без свободных строк.
 *
 * Классы перечисляются по каталогу классов домена, а не списком в тесте: список стал бы
 * вторым каталогом и однажды разошёлся бы с первым.
 */
object AnalyticsCatalogFacts {

    /** Все реализации [AnalyticsEvent], найденные в скомпилированном домене. */
    fun eventClasses(): List<Class<*>> = cached {
        val location = requireNotNull(AnalyticsEvent::class.java.protectionDomain?.codeSource?.location) {
            "the analytics contract has no code source: the catalog cannot be inspected"
        }
        val loader = AnalyticsEvent::class.java.classLoader
        classNames(File(location.toURI()))
            .mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
            .filter { AnalyticsEvent::class.java.isAssignableFrom(it) && it != AnalyticsEvent::class.java }
            .filterNot { Modifier.isAbstract(it.modifiers) }
            .sortedBy { it.simpleName }
            .toList()
    }

    /**
     * Имена классов контракта: домен приезжает и каталогом классов, и готовым jar.
     *
     * Сценарный прогон берёт домен из classpath теста (`core-domain.jar`), а модульные
     * тесты — из каталога классов. Проверка обязана видеть оба случая: молча «не нашлось
     * классов» означало бы «событий нет» и зеленело бы на пустом каталоге.
     */
    private fun classNames(root: File): Sequence<String> = when {
        root.isDirectory -> root.walkTopDown()
            .filter { it.isFile && it.extension == CLASS_EXTENSION }
            .map { file -> file.relativeTo(root).path.toClassName() }
        root.isFile -> JarFile(root).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(PACKAGE_PATH) && it.name.endsWith(".$CLASS_EXTENSION") }
                .map { it.name.toClassName() }
                .toList()
                .asSequence()
        }
        else -> error(
            "the analytics contract is readable neither as a class directory nor as a jar " +
                "(${root.path}); the catalog check cannot enumerate its implementations",
        )
    }

    private fun String.toClassName(): String =
        removeSuffix(CLASS_EXTENSION).removeSuffix(".").replace(File.separatorChar, '.').replace('/', '.')

    /** Типы параметров, через которые в событие пролезли бы медицинские данные или идентификатор. */
    fun freeTextParameters(): List<String> = eventClasses().flatMap { type ->
        type.constructors
            .filterNot { it.isSynthetic }
            .flatMap { constructor ->
                constructor.parameterTypes
                    .filterNot { it.isEnum }
                    .map { parameter -> "${type.simpleName}(${parameter.simpleName})" }
            }
    }

    /** Проводные имена каталога: то, что поставщик получит как имя события. */
    fun names(): Set<String> = AnalyticsEventName.entries.map { it.wire }.toSet()

    /**
     * Ни одно событие не принимает свободный текст или идентификатор (АН-3).
     *
     * Проверяется конструкторами скомпилированного домена: если через событие нельзя
     * передать строку, название лекарства, дозу, время приёма и идентификатор профиля
     * в него не пролезут — ни сегодня, ни после правки экрана.
     */
    fun assertNoFreeTextInEvents() {
        val free = freeTextParameters()
        require(free.isEmpty()) {
            "an analytics event takes a value outside the closed catalog: ${free.sorted()}. " +
                "Medical data and identifiers must not enter an event (АН-3, НФТ-19)"
        }
        require(names().isNotEmpty()) { "the analytics catalog declares no event at all" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(load: () -> T): T = CACHE.getOrPut(TYPE) { load() } as T

    private val CACHE = mutableMapOf<String, Any>()
    private const val TYPE = "events"
    private const val CLASS_EXTENSION = "class"
    private const val PACKAGE_PATH = "ru/stohastika/sklyanka/domain/analytics"
}
