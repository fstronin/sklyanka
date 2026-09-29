package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Настройки стенда, которые Robolectric читает из classpath (SKL-24, TDD §4.1).
 *
 * Файл генерируется, а не лежит в каждом модуле руками, по одной причине: `graphicsMode`
 * тут — не украшение. В устаревшем режиме композиция не рисуется вовсе, снимок выходит
 * пустым полотном и **проходит сверку с таким же пустым эталоном**. Модуль, в котором эту
 * строку забыли, выглядел бы проверенным. Поэтому строка одна на весь проект.
 */
abstract class WriteRobolectricConfigTask : DefaultTask() {

    /** Уровень API имитации: тот же, на который собирается приложение. */
    @get:Input
    abstract val sdk: Property<Int>

    /** Квалификаторы среды: локаль, ширина экрана, плотность. */
    @get:Input
    abstract val qualifiers: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val target = outputDirectory.get().file(FILE_NAME).asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            # Сгенерировано конвенцией sklyanka.screen.tests (SKL-24). Руками не правится.
            sdk=${sdk.get()}
            # NATIVE — настоящий растеризатор Skia. В LEGACY композиция не рисуется,
            # и снимок выходит пустым, ничего об этом не сообщая.
            graphicsMode=NATIVE
            qualifiers=${qualifiers.get()}
            """.trimIndent() + "\n",
        )
    }

    private companion object {
        const val FILE_NAME = "robolectric.properties"
    }
}
