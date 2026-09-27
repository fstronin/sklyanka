package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
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
class ScenarioMedications(private val clock: ScenarioClock) {

    private val byName = linkedMapOf<String, Medication>()

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
        byName[name] = Medication(
            id = "medication-${byName.size + 1}",
            profileId = owner.id,
            // Названий лекарств в модели сценария нет: в журнал и в отчёты попадают
            // только идентификаторы (НФТ-19), а имя из Gherkin — ключ сценария.
            name = "med-${byName.size + 1}",
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

    fun archive(name: String, at: Instant) {
        update(name) { it.copy(archivedAt = at, archiveReason = ArchiveReason.MANUAL) }
    }

    fun unarchive(name: String) {
        update(name) { it.copy(archivedAt = null, archiveReason = null) }
    }

    fun stockOf(name: String): BigDecimal? = get(name).stockLeft

    /** Списание и пополнение приходят из домена по идентификатору, а не по имени. */
    fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        val name = byName.entries.firstOrNull { it.value.id == medicationId }?.key ?: return
        byName[name] = byName.getValue(name).copy(stockLeft = stockLeft)
    }

    fun focusOn(name: String) {
        last = name
    }
}
