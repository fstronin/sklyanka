package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile

/** Режим загрузки (ПЕР-14). Режимов ровно два, третий не появится. */
enum class TransferMode(val code: String) {
    /** По умолчанию: «Добавить к текущим». */
    MERGE("merge"),

    /** «Заменить всё»: текущие данные стираются. */
    REPLACE_ALL("replace_all"),
}

/**
 * Отчёт о применении (ПЕР-15 п. 7, ПЕР-16 п. 6, критерий 17).
 *
 * Те же числа, что уходят в журнал, только человеческим языком собирает экран.
 * Безымянные числа — не украшение: они единственный способ отличить «загрузка стёрла
 * историю» от «в файле её не было».
 */
data class TransferReport(
    val mode: TransferMode,
    val profilesAdded: Int,
    val medicationsAdded: Int,
    val medicationsUpdated: Int,
    val intakesAdded: Int,
    val intakesUpdated: Int,
    val intakesSkipped: Int,
    val decisions: Map<MatchDecision, Int>,
    val notices: List<TransferNotice>,
) {
    val matchesResolved: Int get() = decisions.values.sum()
}

/**
 * План применения: готовый список операций, построенный целиком в памяти (`TDD.md §5`).
 *
 * Это и есть то, что делает атомарность дешёвой. Транзакция **исполняет** план и не
 * принимает решений: ни поиска совпадений, ни перевыдачи идентификаторов, ни чтения
 * файла внутри неё нет. Ручного «откатим, что успели» не существует — такой откат
 * есть второй путь записи, который никогда не проверяется по-настоящему (ПЕР-27).
 *
 * @property erase стирать ли текущие данные перед вставкой; только для «заменить всё»
 * @property activeProfileId каким профилем сделать активный после применения (ПЕР-16 п. 5);
 *   null — активный профиль не меняется, человек остаётся там, где был
 */
data class TransferPlan(
    val mode: TransferMode,
    val erase: Boolean,
    val profilesToInsert: List<Profile>,
    val medicationsToInsert: List<Medication>,
    val medicationsToUpdate: List<Medication>,
    val intakesToInsert: List<Intake>,
    val intakesToUpdate: List<Intake>,
    val activeProfileId: String?,
    val archivedMedicationIds: List<String>,
    val report: TransferReport,
)

/**
 * Количества до и после по каждой сущности (`TDD.md §10.1`).
 *
 * Возвращает хранилище, а не домен: домен знает, что просил, а разбор жалобы требует
 * знать, что получилось.
 */
data class TransferApplied(
    val profilesBefore: Int,
    val medicationsBefore: Int,
    val intakesBefore: Int,
    val profilesAfter: Int,
    val medicationsAfter: Int,
    val intakesAfter: Int,
) {
    /** Строка журнала «было → стало»: только числа, без единой доменной подробности (НФТ-19). */
    val before: String get() = "$profilesBefore/$medicationsBefore/$intakesBefore"

    val after: String get() = "$profilesAfter/$medicationsAfter/$intakesAfter"
}
