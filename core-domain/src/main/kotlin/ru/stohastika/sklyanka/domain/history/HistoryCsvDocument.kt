package ru.stohastika.sklyanka.domain.history

/**
 * Готовый файл выгрузки истории (SKL-119, Р-35).
 *
 * Домен знает о файле ровно то же, что об отчёте врачу: как он называется, какого он типа
 * и что внутри. Слова шапки и колонок приходят с поверхности (НФТ-42), а разделитель,
 * экранирование и порядок строк — дело этого типа: второй формат CSV в продукте
 * не заводится, потому что служебная выгрузка журнала срабатываний пишет тем же способом.
 *
 * **Шапка стоит до таблицы** и называет профиль, период и обе оговорки: файл уходит
 * из приложения в руках человека и открывается где угодно (в том числе в Excel), а рядом
 * с таблицей приёмов нет ни приложения, ни пояснений к ней. Оговорка «не является
 * медицинским документом» — та же, что на каждой странице отчёта: файл с медицинскими
 * данными не имеет права выглядеть медицинским документом.
 *
 * [rows] — строки таблицы: только исходы, состоявшиеся к моменту выгрузки. Пустой период
 * даёт файл с шапкой и колонками и без единой строки данных: это честный ответ «записей нет»,
 * а не признак поломки (Р-33).
 */
data class HistoryCsvDocument(
    val fileName: String,
    val mediaType: String,
    val header: List<String>,
    val columns: List<String>,
    val rows: List<List<String>>,
) {

    /** Сколько записей в таблице: число уходит на экран и в журнал (НФТ-19). */
    val records: Int get() = rows.size

    /** Содержимое файла целиком: собирается до открытия цели, чтобы полуфайла не появилось. */
    val content: String get() = HistoryCsv.text(this)

    /** Размер в байтах: он уходит в журнал вместо содержимого (НФТ-19). */
    val sizeBytes: Int get() = content.toByteArray(Charsets.UTF_8).size

    /**
     * Тот же файл в виде документа системной передачи.
     *
     * Передача и выбор места не различают вида документа: им нужны имя, тип и содержимое,
     * а вид уже назван [mediaType]. Своего второго механизма обмена файл истории не заводит.
     */
    fun asTransferable(): ReportDocument = ReportDocument(
        fileName = fileName,
        mediaType = mediaType,
        bytes = content.toByteArray(Charsets.UTF_8),
    )

    companion object {

        /** Тип файла: таблица, которая открывается таблицей же (Р-35). */
        const val MEDIA_TYPE: String = "text/csv"
    }
}

/**
 * Таблица как CSV — тем же способом, что служебная выгрузка журнала срабатываний (НФТ-51).
 *
 * Разделитель, кавычки и экранирование те же, и это не совпадение, а требование
 * `docs/norm/06-design.md` §5.16: второго формата CSV в продукте нет. Значение с запятой,
 * кавычкой или переводом строки берётся в кавычки, а кавычка внутри удваивается — иначе
 * название лекарства с запятой разорвало бы строку таблицы, и файл перестал бы открываться
 * таблицей.
 *
 * Названия лекарств здесь есть, и это не нарушение НФТ-19: файл — пользовательский документ,
 * он собирается по действию человека и никуда не уходит сам (НФТ-18). Журналы приложения
 * имён не несут, и это разные вещи.
 */
internal object HistoryCsv {

    private const val SEPARATOR = ','
    private const val QUOTE = '"'
    private const val NEW_LINE = "\n"

    /** Символы, из-за которых значение приходится брать в кавычки. */
    private val BREAKS_A_ROW = setOf(SEPARATOR, QUOTE, '\n', '\r')

    fun text(document: HistoryCsvDocument): String = buildString {
        // Шапка — по строке на факт: профиль, период и две оговорки читаются по одной
        // и не зависят от длины друг друга.
        document.header.forEach { line -> appendRow(listOf(line)) }
        appendRow(document.columns)
        document.rows.forEach { cells -> appendRow(cells) }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        append(cells.joinToString(SEPARATOR.toString(), transform = ::escape))
        append(NEW_LINE)
    }

    /** Значение с разделителем, кавычкой или переводом строки не имеет права разорвать таблицу. */
    private fun escape(value: String): String = if (value.any { it in BREAKS_A_ROW }) {
        QUOTE + value.replace(QUOTE.toString(), "$QUOTE$QUOTE") + QUOTE
    } else {
        value
    }
}
