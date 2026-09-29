package ru.stohastika.sklyanka.acceptance.world

import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Лекарства сценария: расписание, доза, курс, остаток, архив.
 *
 * Поиск строгий: неизвестное имя — ошибка шага, а не молчаливое создание лекарства
 * с безобидными значениями. Молчаливое создание было обходом чужого объявления шага —
 * лекарство заводится строкой Предыстории, и если строка не сработала, сценарий
 * обязан об этом сказать.
 */
@Suppress("TooManyFunctions")
class ScenarioMedications(private val clock: ScenarioClock) {

    private val byName = linkedMapOf<String, Medication>()

    /**
     * Счётчик идентификаторов лекарств.
     *
     * Отдельный, а не размер карты: повторное заведение имени (Предыстория задачи
     * перезаводит «пять активных лекарств», уже названных общим миром) не должно
     * выдавать двум лекарствам один идентификатор — ключи ленивого списка архива
     * обязаны быть уникальными.
     */
    private var sequence = 0

    /** Лекарство, о котором шла речь последним: шаги без имени говорят о нём. */
    var last: String = ""
        private set

    fun has(name: String): Boolean = byName.containsKey(name)

    fun get(name: String): Medication = requireNotNull(byName[name]) { "unknown medication: $name" }

    fun names(): List<String> = byName.keys.toList()

    fun all(): List<Medication> = byName.values.toList()

    fun byId(medicationId: String): Medication? = byName.values.firstOrNull { it.id == medicationId }

    @Suppress("LongParameterList")
    fun add(
        profile: ScenarioProfiles,
        profileName: String,
        name: String,
        times: List<LocalTime> = emptyList(),
        startDate: LocalDate = clock.today(),
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ) {
        val owner = profile.get(profileName)
        val number = ++sequence
        byName[name] = Medication(
            id = "medication-$number",
            profileId = owner.id,
            // Названий лекарств в модели сценария нет: в журнал и в отчёты попадают
            // только идентификаторы (НФТ-19), а имя из Gherkin — ключ сценария.
            name = "med-$number",
            form = MedicationForm.TABLET,
            shape = null,
            doseAmount = BigDecimal.ONE,
            doseUnit = "unit",
            strength = null,
            strengthUnit = null,
            foodRelation = FoodRelation.ANY,
            scheduleMode = mode,
            maxPerDay = maxPerDay,
            times = times,
            startDate = startDate,
            endDate = endDate,
            // Учёт остатка по умолчанию выключен: сценарий, которому остаток важен,
            // задаёт его отдельной строкой.
            stockLeft = null,
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
        last = name
    }

    fun update(name: String, change: (Medication) -> Medication) {
        byName[name] = change(get(name))
        last = name
    }

    /**
     * Запись лекарства хранилищем: сохранение курса из формы (SKL-09).
     *
     * Ключ берётся у существующей записи с тем же идентификатором, а у новой — её имя:
     * правка существующего курса не заводит в мире второй записи о том же лекарстве.
     */
    fun put(medication: Medication) {
        val key = byName.entries.firstOrNull { it.value.id == medication.id }?.key ?: medication.name
        byName[key] = medication
        last = key
    }

    /**
     * Довести лекарства профиля до числа, названного Предысторией.
     *
     * Сценарий говорит «профиль заведён с четырьмя лекарствами», а по имени называет
     * только те, о которых идёт речь. Строка списка профилей показывает все четыре,
     * поэтому недостающие заводятся здесь — в момент, когда число проверяется. Иначе
     * шаг показывал бы экран с числом из собственной строки вместо числа Предыстории.
     */
    fun ensureCount(profile: ScenarioProfiles, profileName: String, count: Int) {
        val owner = profile.get(profileName)
        val missing = count - all().count { it.profileId == owner.id }
        repeat(missing.coerceAtLeast(0)) {
            add(
                profile = profile,
                profileName = profileName,
                name = "$UNNAMED_MEDICATION ${all().count { it.profileId == owner.id } + 1}",
            )
        }
    }

    /**
     * Убрать лекарство из мира сценария.
     *
     * Это не действие продукта, а данность Предыстории: сценарий, которому нужно
     * «у профиля нет ни одного лекарства по потребности», обязан убрать заведённое
     * общей Предысторией — архивация оставила бы лекарство в профиле.
     */
    fun remove(name: String) {
        byName.remove(name)
    }

    fun archive(name: String, at: Instant) {
        update(name) { it.copy(archivedAt = at, archiveReason = ArchiveReason.MANUAL) }
    }

    /**
     * Архивация хранилищем — так её видит настоящий `ArchiveMedicationUseCase` (SKL-09).
     *
     * Момент берётся у часов сценария (НФТ-48), а не у системных. Повторная архивация
     * возвращает `false`, как и условное обновление настоящего хранилища (ИНВ-9).
     */
    fun archiveById(medicationId: String, reason: ArchiveReason): Boolean {
        val entry = byName.entries
            .firstOrNull { it.value.id == medicationId && !it.value.isArchived }
            ?: return false
        byName[entry.key] = entry.value.copy(archivedAt = clock.now(), archiveReason = reason)
        last = entry.key
        return true
    }

    fun unarchive(name: String) {
        update(name) { it.copy(archivedAt = null, archiveReason = null) }
    }

    fun stockOf(name: String): BigDecimal? = get(name).stockLeft

    fun packageSizeOf(name: String): BigDecimal? = get(name).packageSize

    private var stockBeforeAction: Map<String, BigDecimal?> = emptyMap()

    /** Остаток на момент последнего действия: им проверяется «отметка не изменила остаток». */
    fun rememberStock() {
        stockBeforeAction = byName.mapValues { it.value.stockLeft }
    }

    fun stockBefore(name: String): BigDecimal? {
        require(stockBeforeAction.containsKey(name)) { "the stock of $name was never remembered" }
        return stockBeforeAction[name]
    }

    /** Списание и пополнение приходят из домена по идентификатору, а не по имени. */
    fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        val name = byName.entries.firstOrNull { it.value.id == medicationId }?.key ?: return
        byName[name] = byName.getValue(name).copy(stockLeft = stockLeft)
    }

    fun focusOn(name: String) {
        last = name
    }

    /** Снимок лекарств для транзакции: откат групповой отметки возвращает и остатки. */
    fun snapshot(): Map<String, Medication> = byName.toMap()

    fun restore(snapshot: Map<String, Medication>) {
        byName.clear()
        byName.putAll(snapshot)
    }

    private companion object {
        /** Ключ лекарства, которое Предыстория посчитала, но не назвала. */
        const val UNNAMED_MEDICATION = "лекарство без имени"
    }
}
