package ru.stohastika.sklyanka.acceptance.steps.sk93

import io.cucumber.core.cli.Main
import java.io.File

internal object Sk93AcceptanceRun {

    private const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
    private const val FEATURE_PATH = "acceptance/src/main/resources/features/SKL-93.feature"
    private const val GLUE = "ru.stohastika.sklyanka.acceptance"

    fun run(tags: String): String? {
        val root = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: the screen test convention must be applied to this module"
        }
        val feature = File(root, FEATURE_PATH)
        require(feature.isFile) { "the feature file is missing: ${feature.path}" }
        val exitCode = Main.run(
            "--glue",
            GLUE,
            "--tags",
            tags,
            "--plugin",
            "progress",
            feature.absolutePath,
        )
        return if (exitCode.toInt() == 0) null else "the $tags scenarios of SKL-93 did not pass on the stand"
    }
}
