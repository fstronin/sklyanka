package ru.stohastika.sklyanka.domain.history

/**
 * Готовый документ отчёта врачу.
 *
 * Домен знает о документе ровно три вещи: как он называется, какого он типа и что у него
 * внутри. Отрисовка — в экранном модуле (там живут ресурсы и формулировки), передача —
 * в `app` (там живут системный обмен и системный выбор места). Содержимое — байты, а не
 * строка: отчёт врачу существует в PDF (ОВ-15), и «содержимое как текст» перестало быть
 * верным описанием артефакта.
 *
 * Обычный класс, а не `data class`: у массива ссылочное равенство, и две копии одного
 * документа расходились бы в сравнении. Равенство задано по содержимому — документ
 * сравнивают в тестах и при разборе исхода передачи.
 */
class ReportDocument(val fileName: String, val mediaType: String, val bytes: ByteArray) {

    /** Размер в байтах: он уходит в журнал вместо содержимого (НФТ-19). */
    val sizeBytes: Int get() = bytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReportDocument) return false
        return fileName == other.fileName &&
            mediaType == other.mediaType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String = "ReportDocument($fileName, $mediaType, ${bytes.size} bytes)"
}

/** Куда сохранить отчёт: непрозрачная ссылка системного выбора места (НФТ-17). */
@JvmInline
value class ReportTarget(val handle: String)

/**
 * Исход передачи отчёта.
 *
 * Отмена — отдельный исход, а не разновидность отказа: для человека отменённый системный
 * диалог выглядит точно так же, как ошибка записи, и жалоба «нажал, и ничего не
 * произошло» разбирается только по этому различию (`TDD.md §11`).
 */
sealed interface ReportTransferOutcome {

    /** Отчёт ушёл в системный обмен. */
    data object Shared : ReportTransferOutcome

    /** Отчёт записан в выбранное человеком место. */
    data object Saved : ReportTransferOutcome

    /** Человек закрыл системный диалог. Не ошибка: состояние экрана сохраняется. */
    data object Cancelled : ReportTransferOutcome

    /** Запись не удалась: нет доступа, место исчезло, поток закрылся. */
    data class Failed(val reason: String) : ReportTransferOutcome
}

/**
 * Передача отчёта средствами системы — порт, реализуемый в `app`.
 *
 * Сети здесь нет ни в одном исходе: приложение не объявляет разрешения `INTERNET`
 * (НФТ-14, НФТ-15), и передача возможна только через системный обмен и файл. Облака нет,
 * поэтому «отправить врачу» означает «отдать системе», а не «загрузить куда-то».
 */
interface DoctorReportTransfer {

    /** Системный способ обмена: выбор приложения делает человек, а не «Склянка». */
    suspend fun share(document: ReportDocument): ReportTransferOutcome

    /**
     * Запись в документ, созданный системным выбором места (НФТ-17).
     *
     * Разрешений на внешнее хранилище приложение не запрашивает, путь наружу не
     * возвращается и не журналируется: приложение не ведёт учёта того, куда ушёл файл.
     */
    suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome
}
