package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.reminder.port.WatchGateway
import ru.stohastika.sklyanka.domain.reminder.port.WatchSendResult
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import java.time.Instant
import java.time.LocalDateTime

/**
 * Будильники в памяти.
 *
 * Фейк ведёт себя как система в единственном важном отношении: адрес будильника —
 * идентификатор приёма, и повторная постановка **заменяет** предыдущий (НАП-9). Именно
 * на этом держится идемпотентность пересборки, и проверять её на настоящем `AlarmManager`
 * негде — эмулятора у сборки нет.
 */
internal class FakeAlarmGateway : AlarmGateway {

    val alarms: MutableMap<String, Instant> = linkedMapOf()
    val repeats: MutableList<Triple<String, Instant, Int>> = mutableListOf()
    var anchor: Instant? = null
    var dayBoundary: Instant? = null
    var exactAllowed: Boolean = true

    /** Сколько раз будильники ставились и снимались — доказательство идемпотентности. */
    var scheduleCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set

    override suspend fun schedule(intakeId: String, at: Instant) {
        scheduleCalls++
        alarms[intakeId] = at
    }

    override suspend fun cancel(intakeId: String) {
        cancelCalls++
        alarms.remove(intakeId)
    }

    override suspend fun scheduled(): Map<String, Instant> = alarms.toMap()

    override suspend fun scheduleAnchor(at: Instant) {
        anchor = at
    }

    override suspend fun scheduleDayBoundary(at: Instant) {
        dayBoundary = at
    }

    override suspend fun scheduleRepeat(intakeId: String, at: Instant, attempt: Int) {
        repeats += Triple(intakeId, at, attempt)
        // Как в платформенной реализации: повтор известен набору поставленных будильников,
        // иначе пересборка окна сняла бы его как осиротевший.
        alarms[intakeId] = at
    }

    override fun canScheduleExact(): Boolean = exactAllowed
}

/**
 * Отложенные срабатывания в памяти: карта по идентификатору приёма.
 *
 * `remove` отвечает, была ли запись, — тем же ответом отметка решает, снимать ли
 * будильник (SKL-35). Замена по ключу воспроизводит `REPLACE` схемы: двух записей
 * об одном приёме не бывает.
 */
internal class FakeDeferredReminders : DeferredReminders {

    val stored: MutableMap<String, DeferredReminder> = linkedMapOf()

    override suspend fun all(): List<DeferredReminder> = stored.values.toList()

    override suspend fun put(reminder: DeferredReminder) {
        stored[reminder.intakeId] = reminder
    }

    override suspend fun remove(intakeId: String): Boolean = stored.remove(intakeId) != null
}

/** Показанные карточки и снятия — в порядке вызова. */
internal class FakeNotificationGateway : NotificationGateway {

    val shown: MutableList<Pair<ReminderCard, ReminderChannel>> = mutableListOf()
    val cancelled: MutableList<ReminderSlot> = mutableListOf()
    var retained: Set<String>? = null
    var enabled: Boolean = true

    val visible: List<ReminderCard> get() = shown.map { it.first }

    /** Что сейчас на экране: показ добавляет слот, снятие убирает — как в шторке. */
    private val visibleSlots: MutableSet<ReminderSlot> = mutableSetOf()

    /**
     * Доходит ли показ до шторки.
     *
     * `false` воспроизводит тихий отказ показа: приложение попросило, система не показала —
     * отключённым каналом, отклонённым полноэкранным намерением, вмешательством оболочки.
     * Разрешение при этом на месте, поэтому от [enabled] это состояние отличается.
     */
    var reachesTheShade: Boolean = true

    /**
     * Через сколько опросов уведомление становится видимым.
     *
     * Шторка наполняется не в тот же миг, в который приложение вызвало показ, и проверка
     * отметки предъявления без этой задержки измеряла бы ровно то же, что отметка вызова.
     */
    var appearsAfterPolls: Int = 0

    /** Что происходит на каждом опросе шторки: тестам нужно двигать часы. */
    var onPoll: (() -> Unit)? = null

    private var polls: Int = 0

    override suspend fun show(card: ReminderCard, channel: ReminderChannel) {
        shown += card to channel
        polls = 0
        if (reachesTheShade) visibleSlots += card.slot
    }

    override suspend fun cancel(slot: ReminderSlot) {
        cancelled += slot
        visibleSlots -= slot
    }

    override suspend fun isShown(slot: ReminderSlot): Boolean {
        onPoll?.invoke()
        polls += 1
        return slot in visibleSlots && polls > appearsAfterPolls
    }

    override suspend fun retainOnly(pendingIntakeIds: Set<String>) {
        retained = pendingIntakeIds
        visibleSlots.removeAll { slot ->
            shown.none { (card, _) ->
                card.slot == slot && card.items.any { row -> row.intakeId in pendingIntakeIds }
            }
        }
    }

    override fun areNotificationsEnabled(): Boolean = enabled
}

/** Часы: по умолчанию не настроены, как и в первой версии до SKL-16. */
internal class FakeWatchGateway(private var result: WatchSendResult = WatchSendResult.NOT_CONFIGURED) : WatchGateway {

    val sent: MutableList<ReminderCard> = mutableListOf()
    val revoked: MutableList<String> = mutableListOf()

    fun connect() {
        result = WatchSendResult.SENT
    }

    override suspend fun send(card: ReminderCard): WatchSendResult {
        sent += card
        return result
    }

    override suspend fun revoke(intakeId: String) {
        revoked += intakeId
    }
}

/** Журнал срабатываний в памяти: по нему проверяется и след, и отсутствие медицинских данных. */
internal class RecordingReminderJournal : ReminderJournal {

    val entries: MutableList<ReminderLogEntry> = mutableListOf()

    fun events(): List<ReminderEvent> = entries.map { it.event }

    fun of(event: ReminderEvent): List<ReminderLogEntry> = entries.filter { it.event == event }

    override suspend fun record(entry: ReminderLogEntry) {
        entries += entry
    }

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = entries.takeLast(limit).reversed()

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = entries.lastOrNull { it.event == event }

    override suspend fun firedIntakeIds(since: Instant): Set<String> = entries
        .filter { it.event == ReminderEvent.FIRED && !it.occurredAt.isBefore(since) }
        .mapNotNull { it.intakeId }
        .toSet()

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}

/** Настройки напоминания в памяти. Значения по умолчанию — те же, что в продукте. */
internal class FakeReminderSettings(private var values: ReminderSettingsValues = ReminderSettingsValues()) :
    ReminderSettings {

    override suspend fun read(): ReminderSettingsValues = values

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        values = values.copy(persistentEnabled = enabled)
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        values = values.copy(snoozeMinutes = minutes)
    }
}

/** Разрешения: по умолчанию выданы, кроме нечитаемого программно автозапуска EMUI (НАП-20). */
internal class FakePermissionStateReader : PermissionStateReader {

    val states: MutableMap<ReminderPermission, PermissionState> = ReminderPermission.entries
        .associateWith { PermissionState.GRANTED }
        .toMutableMap()
        .also { it[ReminderPermission.AUTOSTART] = PermissionState.UNKNOWN }

    override fun state(permission: ReminderPermission): PermissionState = states[permission] ?: PermissionState.UNKNOWN
}

/**
 * Источник данных планировщика поверх фейковых хранилищ.
 *
 * Собирает то же соединение приёма, лекарства и профиля, которое `core-data` делает
 * одним запросом. Нужен именно такой фейк, а не список готовых кандидатов: архивация
 * лекарства и переключение дублирования напоминаний должны влиять на план так же, как
 * в продукте, иначе тесты проверяют не правило, а собственные заготовки.
 */
internal class FakeReminderPlanSource(
    private val intakes: FakeIntakeRepository,
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
) : ReminderPlanSource,
    ReminderCardSource {

    override suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake> =
        intakes.stored.values
            .filter { it.scheduledAt >= from && it.scheduledAt <= toInclusive }
            .mapNotNull { projection(it.id) }

    override suspend fun intake(intakeId: String): ReminderIntake? = projection(intakeId)

    override suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake> = intakes.stored.values
        .mapNotNull { projection(it.id) }
        .filter { it.slot == slot && it.status == IntakeStatus.PENDING }

    override suspend fun card(slot: ReminderSlot, mirroredToWatch: Boolean): ReminderCard? {
        val rows = pendingInSlot(slot)
        val profile = profiles.get(slot.profileId) ?: return null
        val items = rows.mapNotNull { row ->
            medications.get(row.medicationId)?.let { medication ->
                ReminderCardItem(
                    intakeId = row.intakeId,
                    medicationId = medication.id,
                    medicationName = medication.name,
                    form = medication.form,
                    doseAmount = medication.doseAmount,
                    doseUnit = medication.form.doseUnit().code,
                    strength = medication.strength,
                    strengthUnit = medication.strengthUnit,
                )
            }
        }
        return ReminderCard(
            slot = slot,
            scheduledAt = slot.scheduledAt,
            items = items,
            mirroredToWatch = mirroredToWatch,
            profileIsSelf = profile.isSelf,
            profileName = profile.name,
        )
    }

    private suspend fun projection(intakeId: String): ReminderIntake? {
        val intake = intakes.stored[intakeId]
        val medication = intake?.let { medications.get(it.medicationId) }
        val profile = medication?.let { profiles.get(it.profileId) }
        return if (intake == null || medication == null || profile == null) {
            null
        } else {
            ReminderIntake(
                intakeId = intake.id,
                medicationId = medication.id,
                profileId = profile.id,
                scheduledAt = intake.scheduledAt,
                status = intake.status,
                scheduleMode = medication.scheduleMode,
                medicationArchived = medication.isArchived,
                profileIsSelf = profile.isSelf,
                profileMirrorsReminders = profile.mirrorReminders,
            )
        }
    }
}
