package ru.stohastika.sklyanka.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Отсутствие вендора в продукте (SKL-100, ПМ-31).
 *
 * Huawei-аналитика и A/B убраны из релиза; страж держит это свойство кода: ни один
 * продуктовый исходник не импортирует пакеты Huawei, вендорские классы не возвращаются
 * молча. Возврат провайдера — новое решение, и тогда этот тест меняется осознанно.
 */
class AnalyticsBoundaryTest {

    private val repositoryRoot: File = findRepositoryRoot()

    @Test
    fun `no product source imports a Huawei package`() {
        val offenders = productionSources()
            .filter { it.readText().contains(VENDOR_IMPORT_PREFIX) }
            .map { it.relativeTo(repositoryRoot).path }

        assertTrue("a Huawei package leaked into the product: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the vendor adapter classes are gone`() {
        val leftovers = VENDOR_CLASSES
            .map { name -> File(repositoryRoot, name) }
            .filter { it.isFile }
            .map { it.relativeTo(repositoryRoot).path }

        assertFalse("vendor adapter classes are back: $leftovers", leftovers.isNotEmpty())
    }

    private fun productionSources(): List<File> {
        val productModule = { name: String ->
            name == "app" || name.startsWith("core-") || name.startsWith("feature-")
        }
        return repositoryRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
            .filter { module -> productModule(module.name) }
            .flatMap { module ->
                File(module, "src/main").walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
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
        const val VENDOR_IMPORT_PREFIX = "import com.huawei."

        /** Вендорские классы SKL-82/83; в релизе без вендора их быть не должно. */
        val VENDOR_CLASSES = listOf(
            "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics/HuaweiAnalyticsSink.kt",
            "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics/HuaweiEventMapping.kt",
            "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics/HuaweiExperimentVendor.kt",
        )
    }
}
