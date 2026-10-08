package ru.stohastika.sklyanka.acceptance.steps.sk64

import io.cucumber.core.cli.Main
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class Sk64NavigationAcceptanceTest {

    @Test
    fun theNavigationScenariosRunOnTheStand() {
        val root = requireNotNull(System.getProperty("sklyanka.repo.root")) {
            "sklyanka.repo.root is not set: the screen test convention must be applied to this module"
        }
        val feature = File(root, "acceptance/src/main/resources/features/SKL-64.feature")
        require(feature.isFile) { "the feature file is missing: ${feature.path}" }
        val exitCode = Main.run(
            "--glue",
            "ru.stohastika.sklyanka.acceptance.steps.sk64",
            "--tags",
            "@screen and @navigation-motion",
            "--plugin",
            "progress",
            feature.absolutePath,
        )
        assertNull(
            if (exitCode.toInt() == 0) {
                null
            } else {
                "the @navigation-motion scenarios of SKL-64 did not pass on the stand"
            },
        )
    }
}
