package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Снимок состояния подсистемы напоминаний (НАП-37, НФТ-52).
 *
 * Диагностика — часть продукта, а не отладочный экран: когда напоминания не приходят,
 * человек не может отличить «приложение сломалось» от «система запретила», и его жалоба
 * без этих данных неразбираема. Наружу снимок не уходит: сети у приложения нет (НАП-39).
 */
data class ReminderDiagnostics(
    /**
     * Последнее предъявленное напоминание: плановое, фактическое, отклонение.
     *
     * Повтор — такое же предъявление, как первый показ (НАП-37): после настойчивого
     * повтора экран обязан показать его, а не остаться на более раннем первичном показе.
     */
    val lastShown: ReminderLogEntry?,
    /**
     * Название лекарства последнего предъявления; `null` — имени не нашлось (SKL-79).
     *
     * Разрешается на экране по `medicationId` из журнала и **в журнал не пишется**:
     * выгрузка журнала файлом не должна нести медицинские данные (НФТ-19).
     */
    val lastMedicationName: String?,
    val nextAt: Instant?,
    val nextIntakeId: String?,
    /** Название лекарства ближайшего приёма; `null` — следующего нет или имя не нашлось. */
    val nextMedicationName: String?,
    /** Сколько будильников реально стоит в системе (а не сколько домен собирался поставить). */
    val totalScheduled: Int,
    val lastReschedule: ReminderLogEntry?,
    val permissions: Map<ReminderPermission, PermissionState>,
    val reliability: ReminderReliability,
    val outage: ReminderOutage?,
    /** Сводка «сколько сработало вовремя за последние 7 дней» (НФТ-51). */
    val onTime: ReminderOnTimeSummary,
    /** Сводка «сработало и пропущено за 7 дней» фразой (ОВ-49, SKL-79). */
    val week: ReminderWeekSummary,
    /** Журнал срабатываний — не менее 50 записей (НАП-37). */
    val journal: List<ReminderLogEntry>,
    /** Действующий механизм будильников приёмов (SKL-85, НАП-5). */
    val alarmKind: AlarmKind,
    /** Доступен ли в сборке переключатель варианта измерения (SKL-85, ПМ-20). */
    val measurementAvailable: Boolean,
    /**
     * Состояние и последний исход самопроверки доставки (SKL-86, НАП-37).
     *
     * Читается по последней записи проверки, а не хранится флагом: состояние переживает
     * смерть процесса и перечитывается при каждом показе (НФТ-52). Записи проверки —
     * отдельный вид события, поэтому в «последнее напоминание» и сводки они не попадают.
     */
    val deliveryCheck: DeliveryCheckState,
)

/**
 * Сборка снимка диагностики.
 *
 * Состояние разрешений читается из системы, а не из сохранённого флага (НФТ-52), число
 * будильников — из системы, а не из плана: расхождение «план есть, будильников нет» — это
 * и есть отказ, который экран обязан показать.
 */
@Suppress("LongParameterList")
class BuildReminderDiagnosticsUseCase(
    private val journal: ReminderJournal,
    private val alarms: AlarmGateway,
    private val permissions: PermissionStateReader,
    private val plan: ReminderPlanSource,
    private val reliability: ReadReminderReliabilityUseCase,
    /**
     * Названия лекарств для первого уровня (ОВ-49).
     *
     * Нужны, чтобы назвать последнее и следующее напоминание простыми словами. Имя
     * разрешается на сборке снимка и **в журнал не попадает**: журнал хранит только
     * идентификаторы (НФТ-19).
     */
    private val medications: MedicationRepository,
    private val measurement: ReminderMeasurement,
    private val time: TimeSource,
) {

    suspend operator fun invoke(limit: Int = JOURNAL_LIMIT): ReminderDiagnostics {
        val now = time.now()
        val zone = time.zone()
        val scheduled = alarms.scheduled()
        val next = scheduled.entries.filter { it.value.isAfter(now) }.minByOrNull { it.value }
        val last = lastDelivery()

        // Ожидавшиеся срабатывания: приёмы, чей момент прошёл, а отметки так и нет.
        // Нижняя граница — сутки назад: перерыв длиной больше суток человек и так увидит
        // в истории, а выборка на всю базу стоила бы полного прохода по приёмам.
        // Приёмы, напоминание о которых обязано было состояться: не только ожидающиеся,
        // но и переведённые в пропущенные — именно они и доказывают перерыв (НАП-38).
        val expected = WindowIntakeSelector
            .expected(
                plan.candidates(
                    from = now.atZone(zone).toLocalDateTime().minusDays(1),
                    toInclusive = now.atZone(zone).toLocalDateTime(),
                ),
            )
            .map { ExpectedFiring(it.intakeId, it.scheduledAt.atZone(zone).toInstant()) }

        // Один срез журнала на обе недельные сводки: второй проход по журналу не нужен.
        val week = journal.since(now.minus(ReminderOnTimeRule.WINDOW))
        return ReminderDiagnostics(
            lastShown = last,
            lastMedicationName = last?.medicationId?.let { medications.get(it)?.name },
            nextAt = next?.value,
            nextIntakeId = next?.key,
            nextMedicationName = nextMedicationName(next?.key),
            totalScheduled = scheduled.size,
            lastReschedule = journal.last(ReminderEvent.RESCHEDULED),
            permissions = permissions.all(),
            reliability = reliability(),
            outage = OutageDetector.detect(
                now = now,
                expected = expected,
                firedIntakeIds = journal.firedIntakeIds(now.minus(OUTAGE_LOOKBACK)),
            ),
            // Сводка считается по промежутку времени, а не по последним записям: «вовремя
            // за 7 дней» от длины показанного журнала зависеть не имеет права (НФТ-51).
            onTime = ReminderOnTimeRule.summarize(week),
            week = ReminderWeekSummaryRule.summarize(week),
            journal = journal.recent(limit),
            alarmKind = alarms.alarmKind(),
            measurementAvailable = measurement.available(),
            // Состояние проверки — из журнала: и «идёт», и исход переживают смерть
            // процесса, а экран перечитывает их при каждом показе (НФТ-52).
            deliveryCheck = DeliveryCheckRule.stateOf(journal.last(ReminderEvent.DELIVERY_CHECK)),
        )
    }

    /** Имя ближайшего приёма: лекарство по `nextIntakeId`, имя разрешается на экране (НФТ-19). */
    private suspend fun nextMedicationName(nextIntakeId: String?): String? = nextIntakeId
        ?.let { plan.intake(it) }
        ?.medicationId
        ?.let { medications.get(it)?.name }

    /**
     * Последнее предъявление — первый показ или повтор (НАП-37, SKL-63).
     *
     * Два запроса «последняя запись вида» вместо нового метода порта: видов предъявления
     * ровно два, и оба отвечаются существующим контрактом журнала. Сводка «вовремя»
     * повторы по-прежнему не считает — это разные вопросы (`ReminderOnTimeRule`).
     */
    private suspend fun lastDelivery(): ReminderLogEntry? = listOfNotNull(
        journal.last(ReminderEvent.SHOWN),
        journal.last(ReminderEvent.REPEATED),
    ).maxByOrNull { it.occurredAt }

    companion object {
        /** НАП-37: журнал последних срабатываний — не менее 50 записей. */
        const val JOURNAL_LIMIT: Int = 50

        /** Глубина поиска перерыва: сутки. Дальше вопрос решается историей, а не диагностикой. */
        private val OUTAGE_LOOKBACK: Duration = Duration.ofDays(1)
    }
}
