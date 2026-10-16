package ru.stohastika.sklyanka.feature.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ru.stohastika.sklyanka.domain.history.HistoryCsvDocument
import ru.stohastika.sklyanka.domain.history.ReportPreset
import java.time.LocalDate

/**
 * Состояния шторки выгрузки истории для снимков (SKL-119).
 *
 * Состав собирает **настоящий** `HistoryCsvComposer`: снимок ловит и разметку, и формулировки
 * файла, а строка-заглушка показала бы не то, что человек увидит в шторке. Документ —
 * с настоящими словами каталога, но с условными записями: снимок проверяет вид шторки,
 * а правила выгрузки держат доменные тесты и сценарная приёмка.
 */
internal object ExportStates {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Обычное состояние: записи есть, число названо числом. */
    fun withRecords(outcome: ExportOutcome? = null): ExportHistoryUiState = ExportHistoryUiState(
        sheet = sheet(
            records = "44",
            outcome = outcome,
        ),
        outcome = outcome,
    )

    /** Пустой период: вместо числа записей стоит фраза, файл остаётся с шапкой (Р-33). */
    fun withoutRecords(): ExportHistoryUiState = ExportHistoryUiState(
        sheet = sheet(records = null, outcome = null),
    )

    /** Места не хватило: сообщение первым элементом, содержимое остаётся (§5.16). */
    fun withoutSpace(): ExportHistoryUiState = ExportHistoryUiState(
        sheet = sheet(records = "44", outcome = ExportOutcome.NO_SPACE),
        outcome = ExportOutcome.NO_SPACE,
    )

    /** Успех: содержимое заменяется целиком — «Файл сформирован» и «Сохранён». */
    fun saved(): ExportHistoryUiState = ExportHistoryUiState(
        sheet = sheet(records = "44", outcome = ExportOutcome.SAVED),
        outcome = ExportOutcome.SAVED,
    )

    private fun sheet(records: String?, outcome: ExportOutcome?): ExportUi = ExportUi(
        document = HistoryCsvDocument(
            fileName = "sklyanka-history-2026-09-01.csv",
            mediaType = HistoryCsvDocument.MEDIA_TYPE,
            header = emptyList(),
            columns = emptyList(),
            rows = emptyList(),
        ),
        composition = ExportComposition(
            profile = "profile-a",
            period = "1 — 30 сентября 2026",
            from = "1 сентября 2026",
            to = "30 сентября 2026",
            records = records,
            format = context.getString(R.string.history_export_format_value),
        ),
        preset = ReportPreset.THIS_MONTH,
        customFrom = LocalDate.of(2026, 9, 1),
        customTo = LocalDate.of(2026, 9, 30),
        today = LocalDate.of(2026, 9, 30),
    )
}
