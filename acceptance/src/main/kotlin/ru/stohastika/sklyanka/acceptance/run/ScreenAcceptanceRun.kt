package ru.stohastika.sklyanka.acceptance.run

import io.cucumber.core.cli.Main
import java.io.File

/**
 * Экранный прогон сценарной приёмки — одна обвязка на все модули (SKL-26, TDD §5).
 *
 * Cucumber-JVM запускается ИЗНУТРИ модульного теста модуля с экранами: под имитацией
 * Android шаги видят ресурсы модуля и настоящую композицию Compose. Это ответ на `ОВ-74`,
 * проверенный SKL-24 на одной задаче и здесь распространённый на остальные: ограничение
 * было свойством **места**, а не Gherkin — модуль сценарной приёмки чистый JVM по
 * `ADR-0007` и экрана не видит.
 *
 * Свойства прогона выбраны намеренно:
 *
 * - **файл сценариев тот же**, что читает сценарный прогон, —
 *   `acceptance/src/main/resources/features/SKL-NN.feature` (SKL-111). Это ресурс модуля
 *   приёмки, а не копия: единственный файл сценариев задачи живёт в одном месте;
 * - **нереализованный шаг валит прогон.** Cucumber 7 считает `undefined` отказом, и для
 *   экранной группы это правильно: в ней не бывает «ещё не дошли» — сценарий, помеченный
 *   [SCREEN_TAG] и не имеющий шага, не проверяется вообще ничем;
 * - **glue — весь пакет `ru.stohastika.sklyanka.acceptance`.** Так в прогон попадают и общие
 *   шаги из артефакта, и шаги модуля. Именно поэтому дубль между ними опасен, и именно
 *   поэтому его ищет `:acceptance:verifyAcceptanceSteps`.
 */
object ScreenAcceptanceRun {

    /** Тег сценариев, исполняемых экранным прогоном, а не сценарным (`ОВ-78`). */
    const val SCREEN_TAG = "@screen"

    /** Пакет, в котором Cucumber ищет шаги: общий артефакт и шаги модуля лежат в нём. */
    const val GLUE = "ru.stohastika.sklyanka.acceptance"

    /** Свойство с корнем репозитория: его передаёт конвенция `sklyanka.screen.tests`. */
    const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"

    /** Файл сценариев задачи. */
    fun featureOf(task: String): File {
        val root = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: apply the sklyanka.screen.tests convention to this module"
        }
        val feature = File(root, "acceptance/src/main/resources/features/$task.feature")
        require(feature.isFile) { "the feature file is missing: ${feature.path}" }
        return feature
    }

    /**
     * Исполнить экранную группу сценариев задачи.
     *
     * @return `null`, если прогон прошёл; иначе — сообщение об отказе
     */
    fun run(task: String, glue: String = GLUE, tags: String = SCREEN_TAG): String? {
        val exitCode = Main.run(
            "--glue",
            glue,
            "--tags",
            tags,
            "--plugin",
            "progress",
            "--snippets",
            "camelcase",
            featureOf(task).absolutePath,
        )
        // Cucumber возвращает байт, а не int: сравнение без приведения проходит мимо.
        return if (exitCode.toInt() == 0) {
            null
        } else {
            "the $tags scenarios of $task did not pass on the screen stand; see the Cucumber output above"
        }
    }
}
