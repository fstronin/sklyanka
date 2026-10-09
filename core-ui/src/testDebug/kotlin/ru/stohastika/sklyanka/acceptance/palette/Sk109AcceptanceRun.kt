package ru.stohastika.sklyanka.acceptance.palette

import io.cucumber.core.cli.Main
import java.io.File

/**
 * Прогон экранной группы приёмки SKL-109 на стенде модуля `core-ui`.
 *
 * Свой, а не общий `ScreenAcceptanceRun`: тот живёт в модуле приёмки, которого `core-ui`
 * не видит (SKL-93 идёт тем же путём). Сценарии палитры говорят только о токенах,
 * поэтому их и греет модуль токенов.
 */
internal object Sk109AcceptanceRun {

    private const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
    private const val FEATURE_PATH = "acceptance/src/main/resources/features/SKL-109.feature"
    private const val GLUE = "ru.stohastika.sklyanka.acceptance"
    private const val SCREEN_TAG = "@screen"

    /** @return `null`, если прогон прошёл; иначе — сообщение об отказе */
    fun run(tags: String = SCREEN_TAG): String? {
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
        return if (exitCode.toInt() == 0) null else "the $tags scenarios of SKL-109 did not pass on the stand"
    }
}
