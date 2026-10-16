package ru.stohastika.sklyanka.feature.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.history.HistoryCsvDocument
import ru.stohastika.sklyanka.domain.history.HistoryCsvRow
import ru.stohastika.sklyanka.domain.history.HistoryCsvStatus
import ru.stohastika.sklyanka.domain.history.HistoryCsvTable
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сборка файла выгрузки истории из таблицы домена (SKL-119, §5.16).
 *
 * Домен собрал **правила**: какие записи попадают в файл, каким исходом, с каким временем
 * и с какой долей «вовремя». Здесь появляются **слова**: подписи колонок, слова статусов,
 * оговорки шапки и имя файла. Разделение то же, что у документа отчёта ([ReportPdfComposer]):
 * домен про ресурсы не знает (НФТ-42, ADR-0007), а файл обязан открываться таблицей
 * на языке интерфейса (НФТ-43).
 *
 * `Context` здесь — ресурсы, а не платформа: состав собирается и проверяется под Robolectric,
 * а запись файла живёт в доменном сценарии выгрузки и в `app`.
 *
 * Слова берутся из того же каталога и теми же форматтерами, что документ отчёта: «принято
 * вовремя» в файле и в отчёте — одно и то же окно [−2 ч, +2 ч] (Р-21), и разойтись они
 * не имеют права.
 */
@Singleton
class HistoryCsvComposer @Inject constructor(@ApplicationContext applicationContext: Context) {

    /**
     * Контекст с закреплённым языком интерфейса (НФТ-43а, `ADR-0008`).
     *
     * Тем же способом, что композер отчёта: файл уходит из приложения, и язык телефона
     * не имеет права попасть в документ человека.
     */
    private val context: Context = applicationContext.withInterfaceLocale()

    private val text = ReportPdfText(context)

    private val locale: Locale get() = context.resources.configuration.locales[0]

    fun compose(table: HistoryCsvTable): HistoryCsvDocument {
        val pattern = text.pattern(R.string.history_export_file_pattern, locale)
        return HistoryCsvDocument(
            fileName = context.getString(R.string.history_export_file_name, table.window.from.format(pattern)),
            mediaType = context.getString(R.string.history_export_media_type),
            header = header(table),
            columns = COLUMNS.map { key -> context.getString(key) },
            rows = table.rows.map { row -> line(row, pattern) },
        )
    }

    /**
     * Состав файла словами для карточки шторки: профиль, период, число записей и формат.
     *
     * Собирается тем же композером, что и сам файл: двух версий одного содержания в продукте
     * не остаётся, и человек видит в карточке то же, что получит в файле (решение 33-B).
     */
    fun composition(table: HistoryCsvTable): ExportComposition = ExportComposition(
        profile = table.profileName,
        period = text.periodRange(table.window.from, table.window.to, locale),
        from = text.fullDate(table.window.from, locale),
        to = text.fullDate(table.window.to, locale),
        // Пустой период называет себя фразой документа отчёта: «0 записей» — измерение,
        // которого не было (Р-33).
        records = table.rows.size.toString().takeIf { table.rows.isNotEmpty() },
        format = context.getString(R.string.history_export_format_value),
    )

    /**
     * Шапка файла: профиль, период, оговорка и пометка о медицинских данных.
     *
     * Обе оговорки — те же слова, что у отчёта и его шторки: «не является медицинским
     * документом» стоит в колонтитуле каждой страницы PDF, а пометка о медицинских данных —
     * тихой строкой шторки. Второй их формулировки в продукте нет.
     */
    private fun header(table: HistoryCsvTable): List<String> = listOf(
        context.getString(R.string.history_export_header_profile, table.profileName),
        context.getString(
            R.string.history_export_header_period,
            text.periodRange(table.window.from, table.window.to, locale),
        ),
        context.getString(R.string.report_document_disclaimer),
        context.getString(R.string.history_export_note),
    )

    /**
     * Строка таблицы: дата, время плана, лекарство, доза, дозировка, статус, время отметки, «вовремя».
     *
     * Пустое значение — пустая ячейка, а не прочерк: в CSV прочерк читается как значение.
     * Колонка «Вовремя» заполняется только у «принято» (Р-21, Р-27): у пропуска сравнивать
     * нечего, а у приёма «по потребности» нет назначенного времени.
     */
    private fun line(row: HistoryCsvRow, pattern: DateTimeFormatter): List<String> = listOf(
        row.date.format(pattern),
        row.plannedTime?.let { time(it) }.orEmpty(),
        row.medication,
        text.doseText(row.dose, text.doseUnitOf(row.doseUnit, row.form)),
        text.strengthText(row.strength, row.strengthUnit).orEmpty(),
        context.getString(statusKey(row.status)),
        row.actedAt?.let { moment(it) }.orEmpty(),
        row.onTime?.let { onTime ->
            context.getString(
                if (onTime) {
                    R.string.history_export_on_time_yes
                } else {
                    R.string.history_export_on_time_no
                },
            )
        }
            .orEmpty(),
    )

    private fun statusKey(status: HistoryCsvStatus): Int = when (status) {
        HistoryCsvStatus.TAKEN -> R.string.history_export_status_taken
        HistoryCsvStatus.TAKEN_ON_DEMAND -> R.string.history_export_status_on_demand
        HistoryCsvStatus.SKIPPED_BY_PERSON -> R.string.history_export_status_skipped
        HistoryCsvStatus.MISSED_WITHOUT_MARK -> R.string.history_export_status_missed
    }

    /**
     * Дата и время отметки — машинной формой `ГГГГ-ММ-ДД ЧЧ:ММ`.
     *
     * Файл — данные для таблицы, а не проза документа: та же логика, что у даты и времени плана,
     * и та же, по которой имя файла не переводится. НФТ-43 держит язык **слов** файла (шапка,
     * колонки, статусы — они на языке интерфейса), а форма чисел в таблице одна на всех языках:
     * иначе одна и та же выгрузка на двух телефонах сортировалась бы по-разному.
     */
    private fun moment(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern(context.getString(R.string.history_export_acted_at_pattern), locale))

    private fun time(value: LocalTime): String =
        value.format(DateTimeFormatter.ofPattern(context.getString(R.string.history_time_pattern), locale))

    /**
     * Подписи колонок — в том порядке, в котором их называет норма (§5.5).
     *
     * Порядок колонок и порядок ячеек строки — одно и то же перечисление: строка собирается
     * по [COLUMNS], и разойтись они не могут.
     */
    private companion object {

        val COLUMNS: List<Int> = listOf(
            R.string.history_export_column_date,
            R.string.history_export_column_planned_time,
            R.string.history_export_column_medication,
            R.string.history_export_column_dose,
            R.string.history_export_column_strength,
            R.string.history_export_column_status,
            R.string.history_export_column_acted_at,
            R.string.history_export_column_on_time,
        )
    }
}
