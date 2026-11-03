package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-91 — структурная часть контактных листов.
 *
 * Эти сценарии говорят не о вёрстке, а об устройстве артефакта: где лежат листы,
 * чем включается их сборка и не выросли ли эталоны. Читать репозиторий дешевле, чем
 * рисовать: рисование — экранный прогон (`@screen`), а здесь проверяется, что оно
 * адресовано в `build/`, а не в `screenshots/`.
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория,
 * а не о человеке с лекарствами.
 */
class LocalizationContactSheetSteps {

    private var generator: String = ""
    private var convention: String = ""

    @Когда("^разработчик собирает контактные листы для визревью$")
    fun theDeveloperBuildsTheContactSheets() {
        generator = File(RepositoryLayout.root, GENERATOR).readText()
        convention = File(RepositoryLayout.root, CONVENTION).readText()
    }

    @Тогда("^листы складываются в build/visual-review по языкам$")
    fun theSheetsLandInBuildVisualReviewPerLanguage() {
        assertTrue(
            generator.contains(REVIEW_DIRECTORY),
            "the generator must write under build/, where a review artifact belongs and goldens do not: " +
                "$REVIEW_DIRECTORY is missing",
        )
        assertTrue(
            generator.contains("matrixLocaleTag") || generator.contains("SUPPORTED"),
            "the sheets must be grouped by the declared interface language",
        )
    }

    @И("^обычный прогон проверок их не собирает$")
    fun theOrdinaryRunDoesNotBuildThem() {
        assertTrue(
            generator.contains("assumeTrue"),
            "the generator must be gated: without the flag the ordinary run must not draw hundreds of sheets",
        )
        assertTrue(
            convention.contains(VISUAL_REVIEW_PROPERTY),
            "the build must pass the visual review flag to the test JVM",
        )
    }

    @И("^число эталонных снимков не растёт$")
    fun theNumberOfGoldensDoesNotGrow() {
        val goldens = RepositoryLayout.root.listFiles()
            ?.filter { it.isDirectory }
            ?.map { File(it, SNAPSHOT_DIRECTORY) }
            ?.filter { it.isDirectory }
            ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == PNG }.toList() }
            .orEmpty()
        assertTrue(
            goldens.size <= GOLDEN_LIMIT,
            "the localization matrix must not add goldens: ${goldens.size} are in the repository",
        )
        assertEquals(
            emptyList<File>(),
            goldens.filter { golden -> golden.path.contains(REVIEW_ARTIFACT_WORD) },
            "review sheets must never land in the golden directories",
        )
    }

    private companion object {
        const val GENERATOR =
            "app/src/testDebug/kotlin/ru/stohastika/sklyanka/acceptance/locale/matrix/LocalizationContactSheetTest.kt"
        const val CONVENTION =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ScreenTestsConventionPlugin.kt"
        const val REVIEW_DIRECTORY = "build/visual-review"
        const val REVIEW_ARTIFACT_WORD = "visual-review"
        const val SNAPSHOT_DIRECTORY = "screenshots"

        /** The number of snapshot baselines the repository is allowed to carry. */
        const val GOLDEN_LIMIT = 148
        const val VISUAL_REVIEW_PROPERTY = "sklyanka.visualReview"
        const val PNG = "png"
    }
}
