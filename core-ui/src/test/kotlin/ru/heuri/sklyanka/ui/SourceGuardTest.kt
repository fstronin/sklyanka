package ru.heuri.sklyanka.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Страж строк дизайн-системы (НФТ-42, критерий 12).
 *
 * В исходниках `core-ui` нет ни одного строкового литерала кириллицей — включая
 * превью и отладочный каталог. Правило дублирует детектор detekt намеренно: детектор
 * можно отключить строкой в конфигурации, а падающий тест придётся объяснить.
 *
 * Комментарии по-русски разрешены и проверкой не затрагиваются: комментарий не
 * попадает в интерфейс и не требует перевода.
 */
class SourceGuardTest {

    @Test
    fun `no cyrillic string literal in the design system sources`() {
        val offenders = sourceFiles().flatMap { file ->
            cyrillicLiterals(file.readText()).map { literal -> "${file.path}: $literal" }
        }
        assertTrue(
            "Cyrillic string literals must live in strings.xml:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the guard actually finds a violation when there is one`() {
        // Страж, который ничего не находит, не страж. Слово для образца собрано из
        // кодов символов: написанное буквами, оно само было бы тем нарушением,
        // которое ищет и этот тест, и правило detekt RussianTextInCode.
        val quote = "\""
        val violation = "val label = " + quote + CYRILLIC_SAMPLE + quote
        assertTrue(cyrillicLiterals(violation).isNotEmpty())
        assertTrue("a comment is not a violation", cyrillicLiterals("// " + CYRILLIC_SAMPLE).isEmpty())
        assertTrue(cyrillicLiterals("val tag = " + quote + "today_screen" + quote).isEmpty())
    }

    @Test
    fun `there are sources to check at all`() {
        assertTrue("no Kotlin sources found — the guard would pass on nothing", sourceFiles().isNotEmpty())
    }

    private fun sourceFiles(): List<File> = SOURCE_ROOTS
        .map { moduleDir().resolve(it) }
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private companion object {
        val SOURCE_ROOTS = listOf("src/main/kotlin", "src/debug/kotlin")

        /** «Принято» в кодах символов. */
        val CYRILLIC_SAMPLE = charArrayOf(
            '\u041F',
            '\u0440',
            '\u0438',
            '\u043D',
            '\u044F',
            '\u0442',
            '\u043E',
        ).concatToString()

        /** Строковый литерал: обычный в кавычках или «сырой» в тройных кавычках. */
        val STRING_LITERAL = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val LINE_COMMENT = Regex("//[^\\n]*")
        val BLOCK_COMMENT = Regex("/\\*[\\s\\S]*?\\*/")
        val CYRILLIC = Regex("[\\u0400-\\u04FF]")

        /**
         * Каталог модуля.
         *
         * Gradle запускает тесты из каталога модуля, но искать вверх дешевле, чем
         * зависеть от этого: тот же тест запускают и из IDE.
         */
        fun moduleDir(): File {
            var candidate: File? = File(System.getProperty("user.dir") ?: ".")
            while (candidate != null) {
                if (candidate.resolve("src/main/kotlin").isDirectory) return candidate
                if (candidate.resolve("core-ui/src/main/kotlin").isDirectory) return candidate.resolve("core-ui")
                candidate = candidate.parentFile
            }
            error("core-ui sources not found from ${System.getProperty("user.dir")}")
        }

        fun cyrillicLiterals(source: String): List<String> {
            val withoutComments = source
                .replace(BLOCK_COMMENT, " ")
                .replace(LINE_COMMENT, " ")
            return STRING_LITERAL.findAll(withoutComments)
                .map { it.value }
                .filter { CYRILLIC.containsMatchIn(it) }
                .toList()
        }
    }
}
