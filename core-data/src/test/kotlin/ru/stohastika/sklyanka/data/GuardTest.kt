package ru.stohastika.sklyanka.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Тесты-стражи: проверяют не поведение, а соблюдение правил, которые иначе держатся
 * на внимательности ревью.
 *
 * Часть этих правил дублирует detekt. Дублирование намеренное: правило detekt можно
 * отключить одной строкой в конфигурации, и отключение заметит только тот, кто читает
 * дифф конфигурации. Упавший тест заметят все.
 */
class GuardTest {

    private val repositoryRoot: File = findRepositoryRoot()

    @Test
    fun everySelectOverMedicationsAndIntakesIsScopedToAProfile() {
        val offenders = listOf(MEDICATION_DAO, INTAKE_DAO).flatMap { daoName ->
            val source = sourceFile("core-data", DAO_PACKAGE_PATH, daoName).readText()
            queriesOf(source)
                .filter { (_, query) -> query.trimStart().startsWith("SELECT", ignoreCase = true) }
                .filterNot { (name, _) -> name in PROFILE_FREE_QUERIES }
                .filterNot { (_, query) -> query.contains("profile_id = :profileId") }
                .map { (name, _) -> "$daoName.$name" }
        }

        assertTrue(
            "Every list query over medications and intakes must be scoped to a profile (DR-10): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun noModuleReadsTheSystemClockDirectly() {
        val offenders = productionSources()
            .filterNot { it.isClockAllowed() }
            .filter { file -> CLOCK_CALLS.any { call -> file.readText().contains(call) } }
            .map { it.name }

        assertTrue(
            "Wall clock must be read through TimeSource only (NFR-48): $offenders",
            offenders.isEmpty(),
        )
    }

    /** Часы читаются напрямую только в platform/time/SystemTimeSource — по пути, а не по имени (ОВ-73). */
    private fun File.isClockAllowed(): Boolean = name == "GuardTest.kt" ||
        path.replace('\\', '/').endsWith("/ru/stohastika/sklyanka/platform/time/SystemTimeSource.kt")

    @Test
    fun noBuildEverFallsBackToADestructiveMigration() {
        val offenders = productionSources()
            .filterNot { it.name in SELF_REFERENCING_FILES }
            .filter { it.readText().contains("fallbackToDestructiveMigration") }
            .map { it.name }

        assertTrue(
            "A destructive migration erases the intake history (MG-2): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun noSqlComparesOrSumsDoseAndStockColumns() {
        val offenders = sourcesOf("core-data")
            .filterNot { it.name in SELF_REFERENCING_FILES }
            .filter { file -> DECIMAL_COLUMN_ARITHMETIC.containsMatchIn(file.readText()) }
            .map { it.name }

        assertTrue(
            "Decimal columns are stored as text and must never be compared or summed in SQL: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theDataLayerCarriesNoCyrillicStringLiterals() {
        val offenders = sourcesOf("core-data")
            .filter { file -> stringLiterals(file.readText()).any(::hasCyrillic) }
            .map { it.name }

        assertTrue(
            "User facing text belongs in strings.xml, not in Kotlin (NFR-42): $offenders",
            offenders.isEmpty(),
        )
    }

    /** Пары «имя метода → текст запроса» из аннотаций `@Query` в исходнике DAO. */
    private fun queriesOf(source: String): List<Pair<String, String>> = QUERY_ANNOTATION.findAll(source).map { match ->
        val query = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val tail = source.substring(match.range.last)
        val name = FUNCTION_NAME.find(tail)?.groupValues?.get(1).orEmpty()
        name to query
    }.toList()

    /** Строковые литералы исходника без содержимого комментариев. */
    private fun stringLiterals(source: String): List<String> =
        STRING_LITERAL.findAll(stripComments(source)).map { it.value }.toList()

    /** Кириллица ищется по кодам символов: диапазон в виде литерала сам был бы находкой. */
    private fun hasCyrillic(text: String): Boolean = text.any { it.code in CYRILLIC_RANGE }

    private fun stripComments(source: String): String = source.replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")

    private fun productionSources(): List<File> = PRODUCTION_MODULES.flatMap { sourcesOf(it) }

    private fun sourcesOf(module: String): List<File> = repositoryRoot.resolve("$module/src").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun sourceFile(module: String, packagePath: String, fileName: String): File =
        repositoryRoot.resolve("$module/src/main/kotlin/$packagePath/$fileName")

    private fun findRepositoryRoot(): File {
        val startedAt = requireNotNull(System.getProperty("user.dir")) { "Working directory is unknown" }
        var candidate: File? = File(startedAt).absoluteFile
        while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "Repository root not found above $startedAt" }
    }

    private companion object {
        const val DAO_PACKAGE_PATH = "ru/stohastika/sklyanka/data/room/dao"
        const val MEDICATION_DAO = "MedicationDao.kt"
        const val INTAKE_DAO = "IntakeDao.kt"

        val PRODUCTION_MODULES = listOf(
            "app",
            "core-data",
            "core-domain",
            "core-ui",
            "feature-today",
            "feature-meds",
            "feature-history",
            "feature-settings",
            "feature-onboarding",
            "feature-reminder",
        )

        /**
         * Запросы, адресующиеся по первичному ключу.
         *
         * Профиль им не нужен, потому что ключ уникален; принадлежность проверяет слой
         * выше, где известен активный профиль. Список исключений именно список, а не
         * «эти просто так работают».
         */
        val PROFILE_FREE_QUERIES = setOf("getById", "markIfPending", "selectExpiredPending")

        /** Единственное место в проекте, где системные часы читаются напрямую, — по пути (ОВ-73), плюс сам страж. */
        val SELF_REFERENCING_FILES = setOf("GuardTest.kt")

        val CLOCK_CALLS = listOf(
            "System.currentTimeMillis(",
            "Instant.now(",
            "LocalDate.now(",
            "LocalTime.now(",
            "LocalDateTime.now(",
            "ZonedDateTime.now(",
            "ZoneId.systemDefault(",
        )

        val QUERY_ANNOTATION = Regex("""@Query\(\s*(?:"{3}([\s\S]*?)"{3}|"([^"]*)")""")
        val FUNCTION_NAME = Regex("""fun\s+(\w+)""")

        /** Колонки, которые хранятся текстом: складывать и сравнивать их в SQL нельзя. */
        const val DECIMAL_COLUMNS = """(dose_amount|stock_left|strength)"""
        val DECIMAL_COLUMN_ARITHMETIC =
            Regex("""\b$DECIMAL_COLUMNS\b\s*[<>+-]|(?i)SUM\(\s*\b$DECIMAL_COLUMNS\b""")
        val STRING_LITERAL = Regex(""""(?:[^"\\\n]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val CYRILLIC_RANGE = 0x0400..0x04FF
    }
}
