package ru.stohastika.sklyanka.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Граница вендора: SDK вызывается ровно из одного модуля и одного класса (TDD §17).
 *
 * Правило держалось бы на внимательности ревью; тест делает его проверкой. Импорт SDK
 * в feature-модуле или в домене — это либо утечка технической детали, либо второй
 * контур, который разойдётся с первым.
 */
class AnalyticsBoundaryTest {

    private val repositoryRoot: File = findRepositoryRoot()

    @Test
    fun `the vendor SDK is imported only by the core-analytics module`() {
        val offenders = productionSources()
            .filterNot { it.path.contains("/core-analytics/") }
            .filter { it.readText().contains(VENDOR_PACKAGE) }
            .map { it.relativeTo(repositoryRoot).path }

        assertTrue("the vendor SDK leaks outside core-analytics: $offenders", offenders.isEmpty())
    }

    @Test
    fun `only the vendor sink reaches into the SDK`() {
        val importers = productionSources()
            .filter { it.path.contains("/core-analytics/") }
            .filter { it.readText().contains(VENDOR_PACKAGE) }
            .map { it.name }

        assertEquals(listOf("HuaweiAnalyticsSink.kt"), importers)
    }

    private fun productionSources(): List<File> = repositoryRoot.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
        .flatMap { module ->
            File(module, "src/main").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        }

    private fun findRepositoryRoot(): File {
        val startedAt = requireNotNull(System.getProperty("user.dir")) { "Working directory is unknown" }
        var candidate: File? = File(startedAt).absoluteFile
        while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "Repository root not found above $startedAt" }
    }

    private companion object {
        const val VENDOR_PACKAGE = "com.huawei.hms.analytics"
    }
}
