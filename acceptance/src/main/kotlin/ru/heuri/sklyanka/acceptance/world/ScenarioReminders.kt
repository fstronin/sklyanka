package ru.heuri.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.medication.doseUnit
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.profile.ProfileSetReplanner
import ru.heuri.sklyanka.domain.reminder.HandleFiredReminderUseCase
import ru.heuri.sklyanka.domain.reminder.ReminderCard
import ru.heuri.sklyanka.domain.reminder.ReminderCardItem
import ru.heuri.sklyanka.domain.reminder.ReminderChannel
import ru.heuri.sklyanka.domain.reminder.ReminderEvent
import ru.heuri.sklyanka.domain.reminder.ReminderIntake
import ru.heuri.sklyanka.domain.reminder.ReminderLogEntry
import ru.heuri.sklyanka.domain.reminder.ReminderSlot
import ru.heuri.sklyanka.domain.reminder.RescheduleOutcome
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.heuri.sklyanka.domain.reminder.port.AlarmGateway
import ru.heuri.sklyanka.domain.reminder.port.NoWatchGateway
import ru.heuri.sklyanka.domain.reminder.port.NotificationGateway
import ru.heuri.sklyanka.domain.reminder.port.ReminderCardSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettings
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettingsValues
import java.time.Instant
import java.time.LocalDateTime

/**
 * Подсистема напоминаний сценария: будильники, карточки и журнал — в памяти.
 *
 * Правила не копируются. Отбор приёмов считает [RescheduleRemindersUseCase] с настоящим
 * [ru.heuri.sklyanka.domain.reminder.WindowIntakeSelector], срабатывание — настоящий
 * [HandleFiredReminderUseCase], перевод в «пропущено» — настоящий
 * [SweepMissedIntakesUseCase]. В памяти живут только те порты, которые в продукте
 * прячут платформу: будильники, уведомления и журнал. Часы — на портах `NoWatchGateway`
 * и настройках по умолчанию: часы в первой версии не настроены, и это не ошибка сценария.
 *
 * Набор будильников наблюдаем ([scheduled]) — сценарии профилей сравнивают его до и после
 * переключения активного профиля и проверяют, что флаг дублирования добавляет и снимает
 * напоминания немедленно (ПРФ-6, НАП-12).
 */
class ScenarioReminders(
    private val clock: ScenarioClock,
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    private val repositories: ScenarioRepositories,
    private val log: ScenarioLog,
) {

    private val alarms = InMemoryAlarms()
    private val notifications = InMemoryNotifications()
    private val journal = InMemoryReminderJournal()
    private val settings = InMemoryReminderSettings()

    private var before: Map<String, Instant>? = null

    /**
     * Перепланирование по изменению состава профилей — та же связка, что в `app`
     * (`ProfileModule`): переключение дублирования обязано обновить окно немедленно
     * (НАП-11, ПРФ-6). Генерация приёмов причине `PROFILE_SET_CHANGED` не положена,
     * и здесь её нет — как в `ReminderPlanReasons` продукта.
     */
    val replanner: ProfileSetReplanner = ProfileSetReplanner { reschedule(RescheduleReason.PROFILE_SET_CHANGED) }

    /** Пересобрать окно будильников тем же сценарием домена, что и в продукте. */
    fun reschedule(reason: RescheduleReason): RescheduleOutcome = runBlocking {
        RescheduleRemindersUseCase(
            plan = PlanSource(),
            alarms = alarms,
            notifications = notifications,
            journal = journal,
            sweepMissed = SweepMissedIntakesUseCase(repositories.intakeStore, intakes.window, clock.time, log),
            time = clock.time,
        )(reason)
    }

    /** Что стоит в системе сейчас: идентификатор приёма → момент срабатывания. */
    fun scheduled(): Map<String, Instant> = runBlocking { alarms.scheduled() }

    /** Момент срабатывания будильника приёма либо `null`, если напоминания нет. */
    fun alarmOf(intakeId: String): Instant? = scheduled()[intakeId]

    /**
     * Доставить сработавшие будильники — как это делает система: будильник одноразовый,
     * после срабатывания он снимается, а приём проходит через настоящий
     * [HandleFiredReminderUseCase].
     */
    fun deliverDue(): List<String> {
        val due = scheduled().filterValues { !it.isAfter(clock.now()) }.keys.sorted()
        due.forEach { intakeId ->
            runBlocking {
                alarms.cancel(intakeId)
                fired(intakeId)
            }
        }
        return due
    }

    /** Показанные карточки в порядке показа — то, что человек видел в шторке. */
    val shownCards: List<ReminderCard> get() = notifications.shown.map { it.first }

    /** Запомнить набор будильников: им проверяется «переключение профиля его не меняет». */
    fun rememberScheduled() {
        before = scheduled()
    }

    fun scheduledBefore(): Map<String, Instant>? = before

    private suspend fun fired(intakeId: String) {
        HandleFiredReminderUseCase(
            plan = PlanSource(),
            cards = PlanSource(),
            notifications = notifications,
            watch = NoWatchGateway,
            alarms = alarms,
            journal = journal,
            settings = settings,
            window = intakes.window,
            time = clock.time,
        )(intakeId)
    }

    /**
     * Приёмы глазами планировщика и содержимое карточки — тем же соединением приёма,
     * лекарства и профиля, которое `core-data` делает одним запросом.
     */
    private inner class PlanSource :
        ReminderPlanSource,
        ReminderCardSource {

        override suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake> =
            intakes.all()
                .filter { it.scheduledAt >= from && it.scheduledAt <= toInclusive }
                .mapNotNull { projection(it.id) }

        override suspend fun intake(intakeId: String): ReminderIntake? = projection(intakeId)

        override suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake> = intakes.all()
            .mapNotNull { projection(it.id) }
            .filter { it.slot == slot && it.status == IntakeStatus.PENDING }

        override suspend fun card(slot: ReminderSlot, mirroredToWatch: Boolean): ReminderCard? {
            val profile = profiles.byId(slot.profileId) ?: return null
            val items = pendingInSlot(slot).mapNotNull { row ->
                medications.byId(row.medicationId)?.let { medication ->
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

        private fun projection(intakeId: String): ReminderIntake? {
            val intake = intakes.get(intakeId)
            val medication = intake?.let { medications.byId(it.medicationId) }
            val profile = medication?.let { profiles.byId(it.profileId) }
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
}

/** Будильники в памяти: адрес — идентификатор приёма, повторная постановка заменяет (НАП-9). */
private class InMemoryAlarms : AlarmGateway {

    private val byIntake = linkedMapOf<String, Instant>()

    override suspend fun schedule(intakeId: String, at: Instant) {
        byIntake[intakeId] = at
    }

    override suspend fun cancel(intakeId: String) {
        byIntake.remove(intakeId)
    }

    override suspend fun scheduled(): Map<String, Instant> = byIntake.toMap()

    override suspend fun scheduleAnchor(at: Instant) = Unit

    override suspend fun scheduleDayBoundary(at: Instant) = Unit

    override suspend fun scheduleRepeat(intakeId: String, at: Instant, attempt: Int) {
        byIntake[intakeId] = at
    }

    override suspend fun scheduleTest(at: Instant) = Unit

    override fun canScheduleExact(): Boolean = true
}

/** Шторка в памяти: один слот — одна карточка, показ и снятие меняют видимое (НАП-36). */
private class InMemoryNotifications : NotificationGateway {

    val shown: MutableList<Pair<ReminderCard, ReminderChannel>> = mutableListOf()

    private val visible: MutableSet<ReminderSlot> = mutableSetOf()

    override suspend fun show(card: ReminderCard, channel: ReminderChannel) {
        shown += card to channel
        visible += card.slot
    }

    override suspend fun cancel(slot: ReminderSlot) {
        visible -= slot
    }

    override suspend fun isShown(slot: ReminderSlot): Boolean = slot in visible

    override suspend fun retainOnly(pendingIntakeIds: Set<String>) {
        visible.removeAll { slot ->
            shown.none { (card, _) -> card.slot == slot && card.items.any { it.intakeId in pendingIntakeIds } }
        }
    }

    override suspend fun showTest() = Unit

    override fun areNotificationsEnabled(): Boolean = true
}

/** Журнал срабатываний в памяти: след настоящего [HandleFiredReminderUseCase]. */
private class InMemoryReminderJournal : ReminderJournal {

    val entries: MutableList<ReminderLogEntry> = mutableListOf()

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

/** Настройки напоминания в памяти: значения по умолчанию — те же, что в продукте. */
private class InMemoryReminderSettings : ReminderSettings {

    private var values = ReminderSettingsValues()

    override suspend fun read(): ReminderSettingsValues = values

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        values = values.copy(persistentEnabled = enabled)
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        values = values.copy(snoozeMinutes = minutes)
    }
}
