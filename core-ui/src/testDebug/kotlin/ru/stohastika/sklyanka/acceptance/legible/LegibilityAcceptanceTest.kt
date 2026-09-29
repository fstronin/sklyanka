package ru.stohastika.sklyanka.acceptance.legible

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.core.cli.Main
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Экранный прогон группы `@stand` задачи SKL-25.
 *
 * Устроен так же, как прогон SKL-24 (`ScreenAcceptanceTest`): Cucumber-JVM запускается
 * изнутри модульного теста под Robolectric, и шаги видят ресурсы модуля и настоящую
 * композицию Compose. Файл сценариев тот же, что читает сценарный прогон на JVM, —
 * `docs/tasks/SKL-25/acceptance.feature`, копии нет.
 *
 * Словарь шагов свой: пакет `ru.stohastika.sklyanka.acceptance.legible` не пересекается с
 * пакетом шагов SKL-24. Это не украшение — Cucumber загружает glue по префиксу пакета,
 * и два похожих шага о масштабе шрифта в одном пакете отказались бы загружаться вовсе.
 *
 * Прогон падает и на упавшей проверке, и на нереализованном шаге: для экранной группы
 * это правильно — если шаг не написан, сценарий не проверяется ничем.
 */
@RunWith(AndroidJUnit4::class)
class LegibilityAcceptanceTest {

    @Test
    fun theScreenScenariosOfThisTaskRunOnTheStand() {
        val repositoryRoot = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: the screen test convention must be applied to this module"
        }
        val feature = File(repositoryRoot, FEATURE_PATH)
        assertTrue("the feature file is missing: ${feature.path}", feature.isFile)

        val exitCode = Main.run(
            "--glue",
            GLUE,
            "--tags",
            TAG,
            "--plugin",
            "progress",
            feature.absolutePath,
        )

        // Cucumber возвращает байт, а не int: сравнение без приведения проходит мимо.
        assertEquals(
            "the @stand scenarios of SKL-25 did not pass on the stand; see the Cucumber output above",
            0,
            exitCode.toInt(),
        )
    }

    private companion object {
        const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
        const val FEATURE_PATH = "docs/tasks/SKL-25/acceptance.feature"
        const val GLUE = "ru.stohastika.sklyanka.acceptance.legible"
        const val TAG = "@stand"
    }
}
