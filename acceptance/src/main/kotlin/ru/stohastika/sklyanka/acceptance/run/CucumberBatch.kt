package ru.stohastika.sklyanka.acceptance.run

import io.cucumber.core.cli.Main
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Прогон нескольких целей сценариев одним запуском JVM (`CucumberBatch`).
 *
 * Зачем: цель перечисления и запуска — **файл сценариев**, и целей сто один. Пока каждая
 * цель была отдельным `javaexec`, сборка платила за это сотней запусков JVM на задачу
 * (пять задач — пять сотен запусков), и проект `:acceptance` становился самым долгим
 * звеном единого прогона: шесть минут из шести с половиной.
 *
 * Отдельные запуски были выбраны не зря — неразбираемый файл одной задачи не должен
 * обрывать остальные. Здесь это свойство сохранено: цели исполняются **по одной**, и
 * исключение одной цели печатается её собственным выводом и не доходит до следующей.
 * Экономится только повторная загрузка JVM и словаря шагов.
 *
 * Обмен с задачей — протокол с длиной, а не «доверять порядку». На каждую цель:
 *
 * ```
 * \u0001<путь цели>\u0001<длина вывода в знаках>\u0001<вывод цели>\u0002<код возврата>\u0002
 * ```
 *
 * Длина, а не строка-разделитель: вывод Cucumber — чужой текст, и он вправе содержать
 * любые строки, включая те, что мы выбрали бы разделителем. Служебные знаки \u0001 и
 * \u0002 в отчётах не печатает никто, а `readBatch` в сборочном скрипте читает ровно
 * эти отбивки — их значения обязаны совпадать с константами ниже.
 *
 * Запускается задачей `:acceptance:*` тем же способом, каким прежде запускался
 * `io.cucumber.core.cli.Main`: тот же classpath, та же кодировка, тот же
 * `sklyanka.repo.root`.
 */
object CucumberBatch {

    /** Отбивка начала блока: за ней — путь цели и длина вывода. */
    const val OPEN: Char = '\u0001'

    /** Отбивка конца блока: за ней — код возврата цели. */
    const val CLOSE: Char = '\u0002'

    /** Аргумент-разделитель: до него — аргументы Cucumber, после — цели. */
    const val TARGETS: String = "--sklyanka-targets"

    /**
     * Прогон всех переданных целей по одной.
     *
     * Две отмеченные детектором вещи здесь выбраны намеренно, и обе — цена изоляции цели:
     *
     * - **Перехват `Throwable`.** Отдельный запуск на цель тем и держался: поломка одной
     *   цели не лишала прогон отчёта по остальным ста целям. Поймать `Exception` значило бы
     *   оставить дыру ровно там, где отказ интереснее всего: `Error` в словаре шагов или
     *   в разборе файла. Поймано и напечатано в выводе цели — так же, как это делал exit code
     *   отдельного JVM.
     * - **Копия массива аргументов (spread operator).** `Main.run` — метод Java с `vararg`,
     *   и передать в него собранный список иначе, чем распаковкой, нечем. Копия делается
     *   раз на цель, а не в цикле по шагам сценария.
     */
    @JvmStatic
    @Suppress("SpreadOperator", "TooGenericExceptionCaught")
    fun main(args: Array<String>) {
        val separator = args.indexOf(TARGETS)
        require(separator >= 0) {
            "$TARGETS is missing: the task passes the Cucumber arguments first, the targets after it"
        }
        val cucumberArgs = args.take(separator)
        val targets = args.drop(separator + 1)
        require(targets.isNotEmpty()) { "no scenario targets given" }

        val out = PrintStream(System.out, true, "UTF-8")
        val stdout = System.out
        val stderr = System.err
        targets.forEach { target ->
            val captured = ByteArrayOutputStream()
            val stream = PrintStream(captured, true, "UTF-8")
            val exitCode = try {
                System.setOut(stream)
                System.setErr(stream)
                Main.run(*(cucumberArgs + target).toTypedArray()).toInt()
            } catch (failure: Throwable) {
                // Так же, как это делал отдельный запуск: исключение цели печатается в её
                // вывод, прогон продолжается следующей целью. Иначе первая же поломка
                // словаря шагов отняла бы у прогона отчёт по остальным ста целям.
                failure.printStackTrace(stream)
                1
            } finally {
                System.setOut(stdout)
                System.setErr(stderr)
            }
            val output = captured.toString("UTF-8")
            out.print("$OPEN$target$OPEN${output.length}$OPEN")
            out.print(output)
            out.print("$CLOSE$exitCode$CLOSE")
        }
        out.flush()
    }
}
