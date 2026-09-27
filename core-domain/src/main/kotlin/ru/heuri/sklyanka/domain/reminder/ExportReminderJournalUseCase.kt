package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.domain.transfer.ExportTarget
import ru.heuri.sklyanka.domain.transfer.TransferFiles
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Готовый файл журнала срабатываний (НФТ-51).
 *
 * Те же три поля, что у отчёта врачу: как называется, какого типа, что внутри. Больше
 * домен о файле знать не должен — место выбирает система по просьбе человека.
 */
data class ReminderJournalDocument(val fileName: String, val content: String, val records: Int) {

    /** Размер в байтах: он уходит на экран вместо содержимого (НФТ-19). */
    val sizeBytes: Int get() = content.toByteArray(Charsets.UTF_8).size

    companion object {
        /**
         * Тип файла.
         *
         * Таблица, а не текст прозой: файл выгружают, чтобы разобрать жалобу
         * «напоминание не пришло», и разбирают его построчно — глазами или чем угодно,
         * что открывает таблицы. Формат переноса данных (JSON) здесь не годится:
         * этот файл в приложение не загружается и загружаться не должен.
         */
        const val MEDIA_TYPE: String = "text/csv"

        private const val NAME_PREFIX = "sklyanka-reminder-journal-"
        private const val NAME_SUFFIX = ".csv"

        /**
         * Имя файла `sklyanka-reminder-journal-ГГГГ-ММ-ДД.csv`.
         *
         * Дата — локальная дата устройства: человек ищет файл по тому дню, в который
         * он его сохранял. Имён профилей и названий лекарств в имени нет (НФТ-19).
         */
        fun fileName(date: LocalDate): String =
            NAME_PREFIX + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + NAME_SUFFIX
    }
}

/**
 * Исход выгрузки журнала.
 *
 * Отмена системного диалога сюда не попадает: до этой операции дело доходит только
 * после того, как человек выбрал место, — отмену видит экран (`HistoryRoute`, SKL-18).
 */
sealed interface ReminderJournalExportOutcome {

    data class Saved(val records: Int, val bytes: Int) : ReminderJournalExportOutcome

    /** Места не хватило, и это выяснилось **до** начала записи (тот же порядок, что ПЕР-31). */
    data object NotEnoughSpace : ReminderJournalExportOutcome

    /** Запись сорвалась: цель снята, файла с целевым именем не остаётся. */
    data object Failed : ReminderJournalExportOutcome
}

/**
 * Выгрузка журнала срабатываний файлом (НФТ-51).
 *
 * Файловая часть — **тот же механизм, что у переноса данных** ([TransferFiles]), как
 * и требует НФТ-51: системный выбор места, запись целиком, снятие цели при срыве.
 * Второй путь записи файла означал бы второй набор ошибок в том же месте.
 *
 * Выгруженный файл подчиняется тому же запрету, что экран: **названий лекарств, дозировок
 * и имён профилей в нём нет, только идентификаторы** (НФТ-19). Это и есть причина, по
 * которой выгрузка собирается из записей журнала и ничего к ним не досоединяет: файл
 * уходит из приложения в руках человека, который не обязан понимать, что он кому-то
 * показывает.
 *
 * Наружу файл не уходит сам: сети у приложения нет (НАП-39, ПЛ-3).
 */
class ExportReminderJournalUseCase(
    private val journal: ReminderJournal,
    private val files: TransferFiles,
    private val time: TimeSource,
) {

    /** Имя файла по локальной дате устройства — экрану нужно **до** системного выбора места. */
    fun fileName(): String = ReminderJournalDocument.fileName(time.today())

    /** Содержимое целиком: собирается до открытия цели, чтобы полуфайла не появилось. */
    suspend fun document(): ReminderJournalDocument {
        val entries = journal.since(time.now().minus(WINDOW))
        return ReminderJournalDocument(
            fileName = fileName(),
            content = ReminderJournalCsv.of(entries),
            records = entries.size,
        )
    }

    suspend operator fun invoke(target: ExportTarget): ReminderJournalExportOutcome {
        val document = document()
        val bytes = document.sizeBytes

        if (files.freeSpaceBytes() < bytes.toLong() * SPACE_MARGIN) return ReminderJournalExportOutcome.NotEnoughSpace

        return runCatching { files.write(target, document.content) }.fold(
            onSuccess = { ReminderJournalExportOutcome.Saved(records = document.records, bytes = bytes) },
            onFailure = {
                // Недописанная цель снимается: файл, который выглядит выгруженным журналом,
                // но кончается посреди строки, хуже отсутствия файла.
                files.discard(target)
                ReminderJournalExportOutcome.Failed
            },
        )
    }

    private companion object {
        /**
         * Глубина выгрузки — 30 суток.
         *
         * Столько журнал обязан хранить (НФТ-51), и ровно столько выгрузка отдаёт:
         * обещать больше нечем, а отдавать меньше незачем.
         */
        val WINDOW: Duration = Duration.ofDays(30)

        /** Тот же двукратный запас по месту, что у выгрузки данных (ПЕР-31). */
        const val SPACE_MARGIN = 2L
    }
}

/**
 * Журнал таблицей.
 *
 * Заголовки — служебные имена полей записи, а не подписи для человека: файл читают
 * построчно, сверяя с `TDD.md §11.1`, и переводить имена полей значило бы делать файл
 * непригодным ровно для того, ради чего его просят. Русского текста здесь нет и потому,
 * что тексты интерфейса живут в ресурсах (НФТ-42).
 */
internal object ReminderJournalCsv {

    private const val SEPARATOR = ','
    private const val QUOTE = '"'
    private const val NEW_LINE = "\n"

    /** Символы, из-за которых значение приходится брать в кавычки. */
    private val BREAKS_A_ROW = setOf(SEPARATOR, QUOTE, '\n', '\r')

    private val HEADER = listOf(
        "occurred_at",
        "event",
        "intake_id",
        "medication_id",
        "profile_id",
        "scheduled_at",
        "fired_at",
        "deviation_seconds",
        "channel",
        "delivered",
        "reason",
        "alarms_cancelled",
        "alarms_scheduled",
        "attempt",
        "snooze_minutes",
        "watch_result",
        "on_time",
    )

    fun of(entries: List<ReminderLogEntry>): String = (
        listOf(HEADER) + entries.map(::cells)
        ).joinToString(NEW_LINE) { row -> row.joinToString(SEPARATOR.toString(), transform = ::escape) } + NEW_LINE

    /**
     * Строка одной записи.
     *
     * Моменты — в ISO-8601 в UTC, как они и хранятся: перевод в местное время сделал бы
     * файлы двух устройств несравнимыми, а вопрос «что было раньше» — неразрешимым.
     */
    private fun cells(entry: ReminderLogEntry): List<String> = listOf(
        entry.occurredAt.toString(),
        entry.event.code,
        entry.intakeId.orEmpty(),
        entry.medicationId.orEmpty(),
        entry.profileId.orEmpty(),
        entry.scheduledAt?.toString().orEmpty(),
        entry.firedAt?.toString().orEmpty(),
        entry.deviationSeconds?.toString().orEmpty(),
        entry.channel?.id.orEmpty(),
        entry.delivered?.toString().orEmpty(),
        entry.reason.orEmpty(),
        entry.alarmsCancelled?.toString().orEmpty(),
        entry.alarmsScheduled?.toString().orEmpty(),
        entry.attempt?.toString().orEmpty(),
        entry.minutes?.toString().orEmpty(),
        entry.watchResult.orEmpty(),
        // Признак НАП-2 рядом с отклонением: иначе доля «вовремя» в файле считается
        // заново и каждый раз по своему порогу.
        if (entry.event == ReminderEvent.SHOWN) ReminderOnTimeRule.isOnTime(entry.deviationSeconds).toString() else "",
    )

    /** Значение с разделителем, кавычкой или переводом строки не имеет права разорвать таблицу. */
    private fun escape(value: String): String = if (value.any { it in BREAKS_A_ROW }) {
        QUOTE + value.replace(QUOTE.toString(), "$QUOTE$QUOTE") + QUOTE
    } else {
        value
    }
}
