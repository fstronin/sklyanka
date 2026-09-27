package ru.heuri.sklyanka.acceptance.screen

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.core.cli.Main
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Экранный прогон сценарной приёмки (SKL-24, ответ на `ОВ-74`).
 *
 * Cucumber-JVM запускается ИЗНУТРИ модульного теста под Robolectric: шаги при этом
 * видят ресурсы модуля, настоящую композицию Compose и снятие снимков. Файл сценариев
 * тот же, что читает сценарный прогон на JVM, — `docs/tasks/SKL-NN/acceptance.feature`,
 * копии нет.
 *
 * Сценарный прогон на JVM (`:acceptance:acceptanceRun`) группу `@screen` исключает
 * и печатает её отдельным списком: `undefined` перестаёт означать одновременно
 * «шаг не написан» и «шаг здесь написать нельзя» (`ОВ-78`).
 *
 * Прогон падает не только на упавшей проверке, но и на нереализованном шаге: Cucumber 7
 * считает `undefined` отказом. Для экранной группы это правильно — в ней не бывает
 * «ещё не дошли»: если шаг не написан, сценарий не проверяется ничем.
 */
@RunWith(AndroidJUnit4::class)
class ScreenAcceptanceTest {

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
            "the @screen scenarios of SKL-24 did not pass on the stand; see the Cucumber output above",
            0,
            exitCode.toInt(),
        )
    }

    private companion object {
        const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
        const val FEATURE_PATH = "docs/tasks/SKL-24/acceptance.feature"
        const val GLUE = "ru.heuri.sklyanka.acceptance.screen"
        const val TAG = "@screen"
    }
}
