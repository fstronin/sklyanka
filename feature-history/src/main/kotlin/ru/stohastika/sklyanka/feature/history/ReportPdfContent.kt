package ru.stohastika.sklyanka.feature.history

/**
 * Состав документа отчёта врачу — данные, а не рисунок (`TDD.md §5.3`).
 *
 * Собирается [ReportPdfComposer] из [ru.stohastika.sklyanka.domain.history.DoctorReport]
 * и ресурсов модуля; раскладывается по страницам [PdfReportLayout]; рисуется
 * [PdfPagePainter]. Разделение нужно потому, что настоящий PDF (`PdfDocument`) живёт
 * только на устройстве, а состав, склонения и порядок разделов обязаны проверяться
 * на JVM: формат подтверждён дизайном, и ошибка в нём — это ошибка в числах, которые
 * человек несёт врачу.
 *
 * Готовых русских строк здесь нет: формулировки приходят из ресурсов (НФТ-42).
 */
internal data class ReportPdfContent(
    val fileName: String,
    val mediaType: String,
    /** Шапка — только на первой странице документа (решение дизайна). */
    val header: List<ReportPdfLine>,
    /**
     * `null`, когда за период нет ни одной записи: сводка с заголовком не печатается
     * вовсе, вместо неё стоит [emptyNote] (решение дизайна, ревизия 33-B SKL-33).
     */
    val summaryTitle: ReportPdfLine?,
    val summary: List<ReportPdfSummaryLine>,
    /** `null`, когда за период нет ни одной записи: блок «По лекарствам» не печатается. */
    val medicationsTitle: ReportPdfLine?,
    val medicationBlocks: List<ReportPdfBlock>,
    val emptyNote: ReportPdfLine?,
    /**
     * Разделы «Про» расширенного состава — после базовых, в порядке §5.15 (SKL-119).
     *
     * Пустой список — базовый документ: разделов без данных не бывает вовсе (Р-33), и пустой
     * таблицы на их месте тоже. Первый раздел начинается с новой страницы: страницы базового
     * документа от добавления не сдвигаются (Р-36).
     */
    val extendedSections: List<ReportPdfSection> = emptyList(),
    /** Оговорка колонтитула; печатается на каждой странице (решение дизайна). */
    val disclaimer: String,
    /** «Склянка · стр. N»: подпись страницы, которую разметка ставит в колонтитул. */
    val brandPage: (Int) -> String,
)

/**
 * Раздел «Про»: заголовок, строка меты периода и строки показателей (`docs/norm/06-design.md` §5.15).
 *
 * Строка меты обязательна у каждого раздела: лист врачу печатают и пересылают отдельно —
 * по той же причине, по которой оговорка стоит на каждом листе.
 */
internal data class ReportPdfSection(
    /** «СРАВНЕНИЕ ПЕРИОДОВ» — заголовок раздела. */
    val title: String,
    /**
     * Строки меты раздела: «Период: …» и, у сравнения, следом «Предыдущий период: …».
     *
     * Каждый раздел начинается метой своего периода (§5.15): у сравнения их две, и без первой
     * левая колонка чисел — текущий период — оставалась бы без названия.
     */
    val metas: List<String>,
    /** Строки показателей: подпись, число периода и, у сравнения, число предыдущего справа. */
    val rows: List<ReportPdfSectionRow> = emptyList(),
    /** Прочие строки раздела: предложение правила, числа и даты паттерна. */
    val lines: List<ReportPdfLine> = emptyList(),
)

/** Строка показателя раздела: подпись Golos Text, число Literata 600, правое поле — второго периода. */
internal data class ReportPdfSectionRow(val label: String, val value: String, val right: String? = null)

/**
 * Логическая строка документа.
 *
 * [right] — правый столбец строки лекарства («18 из 20 (90 %)»), прижатый к правому полю;
 * числа сводки живут не здесь, а в [ReportPdfSummaryLine] и набираются Literata 600
 * сразу за подписью.
 */
internal data class ReportPdfLine(val text: String, val role: ReportPdfRole, val right: String? = null)

/** Строка сводки: подпись Golos Text и число Literata 600 (правило пары шрифтов §2.3). */
internal data class ReportPdfSummaryLine(val label: String, val value: String)

/**
 * Блок одного лекарства.
 *
 * [right] относится к первой строке; блок кладётся на страницу целиком, чтобы строка
 * с названием не оставалась на одной странице, а её пропуски — на другой.
 */
internal data class ReportPdfBlock(val lines: List<ReportPdfLine>, val right: String?)

/** Роль строки: шрифт, кегль, цвет и высота. Роли — не стили по месту, а места применения. */
internal enum class ReportPdfRole {
    /** Название документа: Literata 600, 20 pt. */
    TITLE,

    /** «Склянка · профиль «Я»»: Golos Text 600, 12 pt. */
    SUBTITLE,

    /** Период и момент формирования: Golos Text 400, 10 pt, вторичный цвет. */
    META,

    /** Заголовок раздела («СВОДКА»): Golos Text 600, 11 pt. */
    SECTION,

    /** Основной текст: Golos Text 400, 10 pt. */
    BODY,

    /** Вторичная строка блока лекарства: Golos Text 400, 10 pt, вторичный цвет. */
    BODY_MUTED,

    /** Число сводки: Literata 600, 12 pt. */
    SUMMARY_VALUE,

    /** Колонтитул: Golos Text 400, 8 pt, вторичный цвет. */
    FOOTER,

    /** Горизонтальная линия между разделами. */
    DIVIDER,

    /** Акцентная линия под шапкой. */
    HEADER_RULE,
    ;

    /** Правило рисуется линией, а не текстом. */
    val isRule: Boolean get() = this == DIVIDER || this == HEADER_RULE
}

/**
 * Геометрия страницы A4 в точках: 1 pt = 1/72 дюйма.
 *
 * A4 210×297 мм — 595×842 pt; поля 18/18/16 мм — решение дизайна. Числа не разбросаны
 * по painter'у: вёрстка и рисование обязаны мерить одной линейкой, иначе блок,
 * поместившийся в разметке, обрежется при печати.
 */
internal data class PdfPageGeometry(
    val width: Float = 595f,
    val height: Float = 842f,
    val marginSide: Float = 51f,
    val marginTop: Float = 51f,
    val marginBottom: Float = 45f,
    val footerGap: Float = 12f,
)

/**
 * Текст документа одной строкой — для приёмки и проверок состава.
 *
 * Подпись сводки и значение склеиваются пробелом, правый столбец приписывается в конец
 * строки. Это не представление для человека (человек видит PDF), а компактный вид
 * состава, по которому приёмка сверяет, что документ говорит о том же, о чём экран.
 */
internal fun ReportPdfContent.documentText(): String = buildList {
    header.forEach { add(it.text) }
    summaryTitle?.let { add(it.text) }
    summary.forEach { line ->
        val parts = listOfNotNull(line.label, line.value.takeIf { value -> value.isNotEmpty() })
        add(parts.joinToString(" "))
    }
    medicationsTitle?.let { add(it.text) }
    medicationBlocks.forEach { block ->
        block.lines.forEachIndexed { index, line ->
            val parts = listOfNotNull(
                line.text.takeIf { it.isNotEmpty() },
                block.right.takeIf { index == 0 },
            )
            add(parts.joinToString(" "))
        }
    }
    emptyNote?.let { add(it.text) }
    extendedSections.forEach { section ->
        add(section.title)
        section.metas.forEach { add(it) }
        section.rows.forEach { row ->
            add(listOfNotNull(row.label, row.value.takeIf { it.isNotEmpty() }, row.right).joinToString(" "))
        }
        section.lines.forEach { add(it.text) }
    }
    add(disclaimer)
}.joinToString("\n")
