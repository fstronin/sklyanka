package ru.heuri.sklyanka.domain.today

import ru.heuri.sklyanka.domain.intake.PrnDailyLimit
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.doseUnit
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.stock.StockWarning
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Сборка экрана «Сегодня» из приёмов суток и активных лекарств профиля
 * (`TDD.md §9`).
 *
 * Чистая функция от данных и момента времени: ни хранилища, ни часов внутри. Именно
 * поэтому её можно прогнать на JVM за миллисекунды во всех четырёх режимах наполнения
 * — обычный день, плотный день, пустой день и переход через полночь, — а не искать
 * их вручную на устройстве.
 *
 * Архивные лекарства сюда не попадают: список активных приходит выборкой слоя данных
 * (ПР-11), а приём, чьё лекарство в этом списке не значится, отбрасывается. Фильтр
 * по профилю обеспечен тем же способом — запроса без него не существует (ПР-10).
 */
class BuildTodayAgenda(private val denseLayout: DenseLayoutRule = DenseLayoutRule()) {

    operator fun invoke(
        date: LocalDate,
        intakes: List<Intake>,
        activeMedications: List<Medication>,
        now: LocalDateTime,
        zone: ZoneId,
        stockWarning: StockWarning? = null,
    ): TodayAgenda {
        val byId = activeMedications.associateBy { it.id }
        val items = intakes.mapNotNull { intake ->
            byId[intake.medicationId]?.let { medication ->
                IntakeItem(
                    intake = intake,
                    medication = medication,
                    presentation = presentationOf(intake, now),
                    markedAt = intake.actedAt?.atZone(zone)?.toLocalTime(),
                )
            }
        }
        // В ленты идут только приёмы по расписанию: приём по потребности уже случился,
        // его место — в истории лекарства, а не в списке дел на день (ПР-4).
        val scheduled = items.filter { it.medication.scheduleMode == ScheduleMode.SCHEDULED }
        val groups = GroupIntakesByTime(scheduled, now)
        return TodayAgenda(
            date = date,
            layout = denseLayout(groups, now),
            prn = prnEntries(activeMedications, items),
            progress = DayProgressRule(items, activeMedications),
            stockWarning = noticeOf(stockWarning, byId),
            activeMedications = activeMedications.count { !it.isArchived },
        )
    }

    /**
     * Единица остатка для плашки берётся у лекарства, а не у предупреждения.
     *
     * Предупреждение считает SKL-12 и единицами не занимается: «осталось 9 таблеток» и
     * «осталось 9 капсул» — вопрос склонения, а не запаса. Лекарства, которого нет среди
     * активных, плашка не показывает вовсе: оно архивировано между двумя значениями
     * потока.
     */
    private fun noticeOf(warning: StockWarning?, byId: Map<String, Medication>): StockNotice? {
        val medication = warning?.let { byId[it.medicationId] } ?: return null
        return StockNotice(
            warning = warning,
            doseUnit = DoseUnit.ofCode(medication.doseUnit) ?: medication.form.doseUnit(),
        )
    }

    /**
     * Вид карточки приёма.
     *
     * «Пропущено осознанно» и «пропущено без отметки» получают один вид: на экране дня
     * оба означают «сделать уже нечего», а различать их обязана история и отчёт врачу,
     * где различие содержательно (ОВ-35).
     */
    private fun presentationOf(intake: Intake, now: LocalDateTime): IntakePresentation = when {
        intake.status == IntakeStatus.TAKEN -> IntakePresentation.TAKEN
        intake.status != IntakeStatus.PENDING -> IntakePresentation.MISSED
        intake.scheduledAt.isAfter(now) -> IntakePresentation.UPCOMING
        else -> IntakePresentation.DUE_NOW
    }

    /**
     * Блок «по потребности».
     *
     * Число приёмов за сутки считается по назначенному времени: у приёма по потребности
     * оно равно фактическому (ИНВ-14), поэтому сутки определяются однозначно и не
     * зависят от часового пояса, в котором человек находится сейчас.
     */
    private fun prnEntries(activeMedications: List<Medication>, items: List<IntakeItem>): List<PrnEntry> =
        activeMedications
            .filter { it.scheduleMode == ScheduleMode.PRN }
            .map { medication ->
                val takenToday = items.count { item ->
                    item.medicationId == medication.id && item.intake.status == IntakeStatus.TAKEN
                }
                PrnEntry(
                    medication = medication,
                    maxPerDay = medication.maxPerDay,
                    takenToday = takenToday,
                    canTake = !PrnDailyLimit.reached(medication.maxPerDay, takenToday),
                )
            }
}
