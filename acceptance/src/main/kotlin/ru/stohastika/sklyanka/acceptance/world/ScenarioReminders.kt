package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.intake.MarkSideEffects
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.reminder.DismissReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.HandleFiredReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderCardItem
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderIntake
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.RescheduleOutcome
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.SweepAtDayBoundaryUseCase
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.reminder.port.WatchGateway
import ru.stohastika.sklyanka.domain.reminder.port.WatchSendResult
import java.time.Instant
import java.time.LocalDateTime

/**
 * Подсистема напоминаний сценария: будильники, карточки и журнал — в памяти.
 *
 * Правила не копируются. Отбор приёмов считает [RescheduleRemindersUseCase] с настоящим
 * [ru.stohastika.sklyanka.domain.reminder.WindowIntakeSelector], срабатывание — настоящий
 * [HandleFiredReminderUseCase], перевод в «пропущено» — настоящий
 * [SweepMissedIntakesUseCase]. В памяти живут только те порты, которые в продукте
 * прячут платформу: будильники, уведомления и журнал. Часы — на портах `NoWatchGateway`
 * и настройках по умолчанию: часы в первой версии не настроены, и это не ошибка сценария.
 *
 * Набор будильников наблюдаем ([scheduled]) — сценарии профилей сравнивают его до и после
 * переключения активного профиля и проверяют, что флаг дублирования добавляет и снимает
 * напоминания немедленно (ПРФ-6, НАП-12).
 */
@Suppress("TooManyFunctions")
class ScenarioReminders(
    private val clock: ScenarioClock,
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    private val repositories: ScenarioRepositories,
    private val log: ScenarioLog,
    /**
     * Включены ли уведомления в системе — тот же ответ, что читает диагностика.
     *
     * Порт шторки отвечает им: пока разрешение отозвано, система молча роняет показ,
     * и карточка в шторке не появляется (сценарий «ни одно уведомление не показывается»,
     * SKL-29). Второй копии «что выдано» здесь нет — состояние живёт в [ScenarioSystemState].
     */
    private val notificationsAllowed: () -> Boolean = { true },
) {

    /**
     * Порты подсистемы открыты наружу, потому что ими питаются экраны диагностики
     * и настроек (SKL-20): журнал, состояние будильников и настройки — то же самое
     * состояние, что видят сценарии напоминаний, и второй его копии в приёмке нет.
     */
    private val alarmStore = InMemoryAlarms()

    val alarms: AlarmGateway = alarmStore

    /**
     * Отложенные срабатывания — в памяти, но переживают «перезагрузку» сценария:
     * будильники очищаются, записи остаются (SKL-35, ОВ-75).
     */
    private val deferredStore = InMemoryDeferredReminders()

    /**
     * Часы: порт записывает отзыв карточки, отправка отвечает «не настроены» (SKL-16).
     *
     * Заглушка не молчит о действии: сценарий «отметка закрывает карточку на часах»
     * проверяет, что продукт позвал отзыв, а не что Wear Engine его исполнил.
     */
    private val watch = RecordingWatchGateway()

    /** Идентификаторы приёмов, чьи карточки на часах продукт отозвал. */
    val revokedWatchIntakeIds: List<String> get() = watch.revoked

    private val notifications = InMemoryNotifications(notificationsAllowed)

    val journal: ReminderJournal = InMemoryReminderJournal()

    val settingsStore: ReminderSettings = InMemoryReminderSettings()

    /**
     * Значения настроек на сейчас — синхронно, для состояний экранов.
     *
     * Экранам настройки приходят потоком; стенд приёмки спрашивает их разово, и второй
     * копии «сколько минут откладывать» у него быть не должно.
     */
    fun settings(): ReminderSettingsValues = runBlocking { settingsStore.read() }

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
            deferred = deferredStore,
            sweepMissed = SweepMissedIntakesUseCase(repositories.intakeStore, intakes.window, clock.time, log),
            time = clock.time,
        )(reason)
    }

    /**
     * Перезагрузка без устройства: будильники системы стираются, записи базы остаются.
     *
     * `AlarmManager` после перезагрузки не хранит намерений приложения — поэтому
     * `scheduled()` возвращает пусто, пока пересборка не поставит будильники заново;
     * отложенные срабатывания живут в базе и переживают её (SKL-35, ОВ-75).
     */
    fun simulateReboot() {
        alarmStore.wipe()
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
        due.forEach { intakeId -> deliver(intakeId) }
        return due
    }

    /**
     * Доставить срабатывания по мере наступления времени — как это делает система.
     *
     * Часы переводятся на момент каждого будильника, а не на конечный: отклонение
     * и повтор считаются от времени срабатывания, и прыжок вперёд сломал бы и то и другое.
     * Повтор, поставленный во время доставки, срабатывает, если его время тоже наступило.
     */
    fun deliverThrough(until: LocalDateTime) {
        val target = clock.momentOf(until)
        while (true) {
            val next = scheduled().entries
                .filter { !it.value.isAfter(target) }
                .minByOrNull { it.value }
                ?: break
            clock.moveTo(next.value.atZone(clock.zone).toLocalDateTime())
            deliver(next.key)
        }
        clock.moveTo(until)
    }

    /** Карточка приёма — тот же источник, что у показа: второй копии состава карточки нет. */
    fun cardSource(): ReminderCardSource = PlanSource()

    /**
     * Последствия отметки: снятие уведомления и отзыв карточки часов.
     *
     * Тем же `DismissReminderUseCase`, что у продукта (`ReminderModule`): экран напоминания
     * отметку выполняет, а закрывает карточку она сама.
     */
    fun markSideEffects(): MarkSideEffects = MarkSideEffects { intakeId ->
        DismissReminderUseCase(PlanSource(), PlanSource(), notifications, watch)(intakeId)
    }

    /** Откладывание — тем же сценарием домена, что у экрана напоминания (НАП-30). */
    fun snoozeReminder(): SnoozeReminderUseCase =
        SnoozeReminderUseCase(PlanSource(), alarms, notifications, journal, deferredStore, clock.time)

    /**
     * Граница суток: перевод просроченных в пропущенные и снятие их карточек (НАП-34).
     *
     * Тот же сценарий, что запускает будильник границы суток в `app`; здесь он зовётся
     * моментом сценария.
     */
    fun dayBoundarySweep(): Int = runBlocking {
        SweepAtDayBoundaryUseCase(
            sweepMissed = SweepMissedIntakesUseCase(repositories.intakeStore, intakes.window, clock.time, log),
            plan = PlanSource(),
            notifications = notifications,
            alarms = alarms,
            journal = journal,
            time = clock.time,
        )()
    }

    /** Показана ли карточка приёма в шторке сейчас — «напоминание исчезает из шторки». */
    fun isNotificationShown(intakeId: String): Boolean = runBlocking {
        val intake = PlanSource().intake(intakeId) ?: return@runBlocking false
        notifications.isShown(intake.slot)
    }

    /** Включены ли уведомления в системе: ответ платформы, а не сохранённый флаг. */
    fun notificationsEnabled(): Boolean = notifications.areNotificationsEnabled()

    /** Показанные карточки с каналами: «первое обычным сигналом, повтор настойчивым». */
    fun shownWithChannels(): List<Pair<ReminderCard, ReminderChannel>> = notifications.shown.toList()

    private fun deliver(intakeId: String) {
        runBlocking {
            // Номер показа читается до снятия будильника: снятие забывает и номер,
            // и без этого повтор каждый раз считался бы первым.
            val attempt = alarmStore.attemptOf(intakeId) ?: 0
            alarms.cancel(intakeId)
            fired(intakeId, attempt)
        }
    }

    /** Показанные карточки в порядке показа — то, что человек видел в шторке. */
    val shownCards: List<ReminderCard> get() = notifications.shown.map { it.first }

    /** Запомнить набор будильников: им проверяется «переключение профиля его не меняет». */
    fun rememberScheduled() {
        before = scheduled()
    }

    fun scheduledBefore(): Map<String, Instant>? = before

    private suspend fun fired(intakeId: String, attempt: Int) {
        HandleFiredReminderUseCase(
            plan = PlanSource(),
            cards = PlanSource(),
            notifications = notifications,
            watch = watch,
            alarms = alarms,
            journal = journal,
            deferred = deferredStore,
            settings = settingsStore,
            window = intakes.window,
            time = clock.time,
        )(intakeId, attempt)
    }

    /**
     * Приёмы глазами планировщика — для снимка диагностики (SKL-20, НАП-37).
     *
     * Тот же источник, что у пересборки окна: второй список кандидатов разошёлся бы
     * с первым, и признак перерыва считался бы по чужим приёмам.
     */
    fun planSource(): ReminderPlanSource = PlanSource()

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

    /** Номер показа рядом с моментом: 0 — первый, 1… — повтор (НАП-25). */
    private val attempts = linkedMapOf<String, Int>()

    override suspend fun schedule(intakeId: String, at: Instant, attempt: Int) {
        byIntake[intakeId] = at
        attempts[intakeId] = attempt
    }

    override suspend fun cancel(intakeId: String) {
        byIntake.remove(intakeId)
        attempts.remove(intakeId)
    }

    override suspend fun scheduled(): Map<String, Instant> = byIntake.toMap()

    override suspend fun scheduleAnchor(at: Instant) = Unit

    override suspend fun scheduleDayBoundary(at: Instant) = Unit

    override fun canScheduleExact(): Boolean = true

    fun attemptOf(intakeId: String): Int? = attempts[intakeId]

    /** Перезагрузка: система не хранит намерений приложения, будильников больше нет. */
    fun wipe() {
        byIntake.clear()
        attempts.clear()
    }
}

/**
 * Отложенные срабатывания в памяти: карта по идентификатору приёма, как в схеме (SKL-35).
 *
 * Замена по ключу воспроизводит `REPLACE`: отложенное срабатывание у приёма ровно одно,
 * и повторная запись не может дать второй будильник. Хранилище живёт дольше будильников —
 * на этом и держится «переживает перезагрузку».
 */
private class InMemoryDeferredReminders : DeferredReminders {

    val stored: MutableMap<String, DeferredReminder> = linkedMapOf()

    override suspend fun all(): List<DeferredReminder> = stored.values.toList()

    override suspend fun put(reminder: DeferredReminder) {
        stored[reminder.intakeId] = reminder
    }

    override suspend fun remove(intakeId: String): Boolean = stored.remove(intakeId) != null
}

/**
 * Часы как порт: отправка отвечает «не настроены», отзыв запоминается.
 *
 * Отдельная реализация рядом с доменной [ru.stohastika.sklyanka.domain.reminder.port.NoWatchGateway]:
 * той безразличен отзыв, а сценарию — нет. Поведение доставки то же: чип «Продублировано
 * на часы» не появляется (ЧАС-14), пока интеграция отложена (SKL-16).
 */
private class RecordingWatchGateway : WatchGateway {

    private val revokedIds = mutableListOf<String>()

    val revoked: List<String> get() = revokedIds.toList()

    override suspend fun send(card: ReminderCard): WatchSendResult = WatchSendResult.NOT_CONFIGURED

    override suspend fun revoke(intakeId: String) {
        revokedIds += intakeId
    }
}

/** Шторка в памяти: один слот — одна карточка, показ и снятие меняют видимое (НАП-36). */
private class InMemoryNotifications(private val allowed: () -> Boolean) : NotificationGateway {

    val shown: MutableList<Pair<ReminderCard, ReminderChannel>> = mutableListOf()

    private val visible: MutableSet<ReminderSlot> = mutableSetOf()

    /**
     * Показ идёт, только пока система разрешает уведомления.
     *
     * На Android `NotificationManager.notify` при отозванном разрешении не падает и ничего
     * не показывает — молча роняет. Порт повторяет это поведение, а не изображает показ,
     * которого человек не увидел бы (SKL-29, НАП-18).
     */
    override suspend fun show(card: ReminderCard, channel: ReminderChannel) {
        if (!allowed()) return
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

    override fun areNotificationsEnabled(): Boolean = allowed()
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
