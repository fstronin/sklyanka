package ru.stohastika.sklyanka.domain.widget

import ru.stohastika.sklyanka.domain.history.DayOutcomeRule
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.medication.counter
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.TodayAgenda
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Сборка снимков виджетов из данных домена (SKL-118, `TDD.md §7`).
 *
 * Чистые функции от данных и момента времени: ни хранилища, ни часов внутри. День
 * собирается результатом [BuildTodayAgenda] — тем же, чем собирается день экрана
 * «Сегодня»: виджет обязан показывать тот же день и то же состояние приёма, что
 * приложение (ПР-22), и разойтись эти два дня не имеют права.
 *
 * Времена и даты — локальные правила домена (НФТ-34): снимок несёт `LocalDate` и `LocalTime`,
 * а «сегодня» и полночь считает [ru.stohastika.sklyanka.domain.time.TimeSource] у вызывающего
 * (НФТ-48). Прямых системных часов здесь нет.
 */
object BuildWidgetSnapshots {

    /**
     * День «Сегодня»: приёмы дня активного профиля.
     *
     * Приёмы «по потребности» в снимок не попадают: у виджета нет действия «принять
     * по потребности» (оно рождает приём), и показывать их в списке дел значило бы
     * предлагать человеку действие, которого на этой поверхности нет (ПР-4, «Вне рамок»).
     */
    fun day(
        date: LocalDate,
        intakes: List<Intake>,
        activeMedications: List<Medication>,
        now: LocalDateTime,
        zone: ZoneId,
        context: ProfileContext,
    ): WidgetDaySnapshot = WidgetDaySnapshot(
        date = date,
        items = lanes(date, intakes, activeMedications, now, zone),
        activeMedications = activeMedications.count { !it.isArchived && !it.isDeleted },
        profile = indication(context),
    )

    /**
     * Неделя: семь последних дней — от шести дней назад до сегодняшнего включительно (§5.11).
     *
     * Состояние дня берётся у правила истории ([DayOutcomeRule]) — тем же словарём §5.2,
     * которым живёт блок «Последние 7 дней» карточки лекарства: полоса в виджете не имеет
     * права выглядеть иначе, чем тот же блок в приложении. Счётчик — правило ЭКР-6: числитель
     * считает только завершившиеся сутки, знаменатель — приёмы всех семи дней.
     *
     * Числа считаются только по сгенерированным приёмам: день без приёмов показывает
     * состояние «приёмов не было», а не предсказание (П-7). Лекарство, которого нет
     * в [medications], отбрасывается — тем же правилом, что и в дне экрана (ПР-11);
     * приёмы «по потребности» в план не входят (ПР-4).
     */
    fun week(
        today: LocalDate,
        intakes: List<Intake>,
        medications: List<Medication>,
        historyStartsAt: LocalDate?,
        context: ProfileContext,
    ): WidgetWeekSnapshot {
        val byId = medications.associateBy { it.id }
        val planned = intakes.filter { byId[it.medicationId]?.scheduleMode == ScheduleMode.SCHEDULED }
        val byDate = planned.groupBy { it.scheduledAt.toLocalDate() }
        val days = weekOf(today).map { date ->
            val ofDay = byDate[date].orEmpty()
            WidgetDaySummary(
                date = date,
                state = DayOutcomeRule.state(ofDay, date, today, historyStartsAt),
                taken = ofDay.count { it.status == IntakeStatus.TAKEN },
                planned = ofDay.size,
            )
        }
        return WidgetWeekSnapshot(
            date = today,
            counter = counterOf(days),
            days = days,
            profile = indication(context),
        )
    }

    /**
     * Одно лекарство: его приёмы на сутки, по возрастанию времени.
     *
     * Лекарство приходит целиком, а не именем: имя и единица дозы — свойства лекарства,
     * и брать их отдельным чтением значило бы допустить снимок с именем одного лекарства
     * и приёмами другого.
     */
    fun medication(
        date: LocalDate,
        medication: Medication,
        intakes: List<Intake>,
        now: LocalDateTime,
        zone: ZoneId,
        context: ProfileContext,
    ): WidgetMedicationSnapshot = WidgetMedicationSnapshot(
        date = date,
        medicationId = medication.id,
        name = medication.name,
        items = lanes(date, intakes, listOf(medication), now, zone),
        profile = indication(context),
    )

    /**
     * Семь последних дней: от шести дней назад до сегодняшнего включительно (§5.11).
     *
     * Будущих дней в окне нет: норма называет «неделю» последними семью днями, и кружка
     * «завтра» у полосы не бывает.
     */
    fun weekOf(today: LocalDate): List<LocalDate> = (WEEK_LENGTH - 1 downTo 0).map { today.minusDays(it.toLong()) }

    /**
     * Приёмы лент в порядке экрана: тот же состав, тот же порядок и тот же вид карточки,
     * что у «Сегодня» (ПР-22).
     *
     * Отбор приёмов — тот же, что на экране: лекарства, которого нет в списке активных,
     * в ленты не попадают (ПР-11), а приём «по потребности» живёт в своём блоке (ПР-4).
     */
    private fun lanes(
        date: LocalDate,
        intakes: List<Intake>,
        medications: List<Medication>,
        now: LocalDateTime,
        zone: ZoneId,
    ): List<WidgetIntake> {
        // Сутки нарезаются здесь, а не запросом: чистая сборка обязана быть верной при
        // любом входе, а приём соседних суток в ленте дня — не «лишний», а чужие сутки
        // (виджет показывает ровно один день).
        val ofDay = intakes.filter { it.scheduledAt.toLocalDate() == date }
        val agenda: TodayAgenda = BuildTodayAgenda()(date, ofDay, medications, now, zone)
        return agenda.layout.groups.flatMap { it.items }.map { it.widget() }
    }

    /**
     * Счётчик недели — правило ЭКР-6, то же, что у блока «Последние 7 дней» (§5.2).
     *
     * Полоса собирается словарём истории ([DayCell]) и отдаётся существующему правилу
     * счётчика: второй формулы «сколько принято за неделю» в продукте нет. Сегодняшний
     * день в числитель не входит — иначе каждое утро счётчик показывал бы провал,
     * которого нет (ЭКР-6).
     */
    private fun counterOf(days: List<WidgetDaySummary>): WeekCounter = WeekAdherenceStrip(
        days = days.map { it.cell() },
        taken = days.filter { it.isCompleted() }.sumOf { it.taken },
        planned = days.sumOf { it.planned },
    ).counter()

    private fun WidgetDaySummary.isCompleted(): Boolean = state != DayState.AHEAD && state != DayState.BEFORE_HISTORY

    /** Ячейка полосы §5.2: сегодняшний день счёту не подлежит вовсе (ЭКР-6). */
    private fun WidgetDaySummary.cell(): DayCell = when {
        state == DayState.AHEAD -> DayCell.Today(date)
        state == DayState.BEFORE_HISTORY -> DayCell.BeforeCourse(date)
        else -> DayCell.Completed(date = date, taken = taken, planned = planned)
    }

    private fun IntakeItem.widget(): WidgetIntake = WidgetIntake(
        intakeId = intake.id,
        time = intake.scheduledAt.toLocalTime(),
        medicationName = medication.name,
        dose = medication.doseOf(doseAmount),
        presentation = presentation,
    )

    /** Доза приёма вместе с единицей и дозировкой лекарства — состав подписи строки (§5.11). */
    private fun Medication.doseOf(amount: BigDecimal): WidgetDose = WidgetDose(
        amount = amount,
        // Единица приводится к закрытому списку тем же правилом, что и на экране: код формы —
        // запасной ответ, когда запись пришла из файла переноса или из старой версии (МГ-6).
        unit = (DoseUnit.ofCode(doseUnit) ?: form.doseUnit()).code,
        strength = strength.takeIf { strengthUnit != null },
        strengthUnit = strengthUnit.takeIf { strength != null },
    )

    /**
     * Компактная индикация профиля: имя и цвет, без слов «чужой» и «подопечный» (ПР-24).
     *
     * Признак — `showsForeignIndication`, то есть «профиль не владельца»: он вычисляется
     * из `is_self`, а не хранится отдельным полем (ПРФ-16), и второго его определения
     * у виджета быть не может.
     */
    private fun indication(context: ProfileContext): WidgetProfileIndication? = context
        .takeIf { it.showsForeignIndication }
        ?.let { WidgetProfileIndication(name = it.name, color = it.color) }

    /** Ширина полосы недели: семь дней (§5.11, `DayCell.WEEK_LENGTH`). */
    private const val WEEK_LENGTH = 7
}
