package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.intake.MarkSideEffects
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.reminder.AcknowledgeDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.CancelDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.DeviceEnvironment
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DismissReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.FinishDeliveryCheckOnEntryUseCase
import ru.stohastika.sklyanka.domain.reminder.HandleFiredDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.HandleFiredReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderCardItem
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderIntake
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRotation
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.RescheduleOutcome
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.stohastika.sklyanka.domain.reminder.RotateReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.StartDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.SweepAtDayBoundaryUseCase
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import ru.stohastika.sklyanka.domain.reminder.port.DeviceEnvironmentSource
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingKey
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import java.time.Instant
import java.time.LocalDateTime

/**
 * Подсистема напоминаний сценария: будильники, карточки и журнал — в памяти.
 *
 * Правила не копируются. Отбор приёмов считает [RescheduleRemindersUseCase] с настоящим
 * [ru.stohastika.sklyanka.domain.reminder.WindowIntakeSelector], срабатывание — настоящий
 * [HandleFiredReminderUseCase], перевод в «пропущено» — настоящий
 * [SweepMissedIntakesUseCase]. В памяти живут только те порты, которые в продукте
 * прячут платформу: будильники, уведомления и журнал.
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
    private val alarmStore = InMemoryAlarms { effectiveKind() }

    val alarms: AlarmGateway = alarmStore

    /**
     * Среда измерения пилота (SKL-85): факты устройства задаются сценарием, а не платформой.
     *
     * Живёт в мире, потому что в продукте это чтение `Build.*` в `app`: приёмке нужен
     * тот же ответ, что ставит будильники, иначе разметка окон разошлась бы с реальностью.
     */
    var environment: DeviceEnvironment = DeviceEnvironment(
        manufacturer = "HUAWEI",
        brand = "HUAWEI",
        osRelease = "12",
        buildDisplay = "HUAWEI Pura 80 Ultra",
        batteryUnrestricted = true,
    )

    /** Доступен ли режим измерения: в обычной сборке `false`, сценарий пилота включает явно. */
    var measurementAvailable: Boolean = false

    val environmentSource: DeviceEnvironmentSource = object : DeviceEnvironmentSource {
        override suspend fun environment(): DeviceEnvironment = environment
    }

    val measurement: ReminderMeasurement = object : ReminderMeasurement {
        override fun available(): Boolean = measurementAvailable
    }

    /**
     * Отложенные срабатывания — в памяти, но переживают «перезагрузку» сценария:
     * будильники очищаются, записи остаются (SKL-35, ОВ-75).
     */
    private val deferredStore = InMemoryDeferredReminders()

    private val notifications = InMemoryNotifications(notificationsAllowed)

    val journal: ReminderJournal = InMemoryReminderJournal()

    private val settingsImpl = InMemoryReminderSettings()

    /**
     * Самопроверка доставки (SKL-86, ПМ-21, ОВ-108).
     *
     * Тот же набор доменных сценариев, что собирает `app`: проверка идёт боевым путём —
     * точный будильник, обычный канал, отдельная карточка, — но приёма не создаёт.
     * «Приложение открыто» сценарий задаёт через [appForeground], а не через платформу.
     */
    var appForeground: Boolean = false

    val acknowledgements: DeliveryCheckAcknowledgements = InMemoryDeliveryCheckAcknowledgements()

    fun startDeliveryCheck(): DeliveryCheckState = runBlocking {
        StartDeliveryCheckUseCase(alarms = alarms, journal = journal, time = clock.time)()
    }

    fun firedDeliveryCheck(): DeliveryCheckState = runBlocking {
        HandleFiredDeliveryCheckUseCase(
            journal = journal,
            notifications = notifications,
            foreground = { appForeground },
            time = clock.time,
        )()
    }

    fun finishDeliveryCheck(): DeliveryCheckState = runBlocking {
        FinishDeliveryCheckOnEntryUseCase(journal = journal, alarms = alarms, time = clock.time)()
    }

    fun cancelDeliveryCheck() {
        runBlocking { CancelDeliveryCheckUseCase(alarms = alarms, journal = journal, time = clock.time)() }
    }

    fun acknowledgeDeliveryCheck(dueAt: Instant) {
        runBlocking { AcknowledgeDeliveryCheckUseCase(acknowledgements)(dueAt) }
    }

    /** Момент тестового срабатывания в системе; `null` — не назначено. */
    fun deliveryCheckAlarm(): Instant? = alarmStore.deliveryCheck

    /** Записи проверки — отличимый вид журнала; по ним поддержка видит её след (НАП-4). */
    fun deliveryCheckRecords(): List<ReminderLogEntry> =
        (journal as InMemoryReminderJournal).entries.filter { it.event == ReminderEvent.DELIVERY_CHECK }

    /** Что просили показать тестовой карточкой: моменты, с которыми звали показ. */
    fun deliveryCheckShows(): List<LocalDateTime> = notifications.deliveryChecks.toList()

    fun isDeliveryCheckShown(): Boolean = notifications.deliveryCheckVisible()

    /**
     * Настройки со следом в журнале — как `JournalingReminderSettings` продукта (НАП-14).
     *
     * Харнесс повторяет поведение слоя данных: смена варианта измерения обязана оставить
     * `SETTINGS_CHANGED{alarm_strategy=…}`, иначе окна A/B-прогона неразличимы (SKL-85).
     */
    val settingsStore: ReminderSettings = SettingTraceSettings(settingsImpl, journal, clock)

    /**
     * Действующий механизм будильников — как `EffectiveAlarmStrategy` продукта (SKL-85).
     *
     * Обычная сборка игнорирует сохранённое значение и всегда ставит системный будильник:
     * переключателя в ней нет, а значение могло остаться от измерительной сборки.
     */
    fun effectiveKind(): AlarmKind =
        if (measurementAvailable) settingsImpl.current().alarmStrategy else AlarmKind.ALARM_CLOCK

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
            environment = environmentSource,
            time = clock.time,
        )(reason)
    }

    /**
     * Ротация журнала срабатываний (SKL-65): тот же сценарий домена, что зовёт
     * приложение при старте; правила ПМ-30 проверяются, а не пересказываются шагом.
     */
    fun rotateJournal(): ReminderLogRotation? = runBlocking {
        RotateReminderJournalUseCase(journal = journal, time = clock.time)()
    }

    /** Все записи журнала как они лежат: сценарию ротации нужен весь список, а не последние N. */
    fun journalEntries(): List<ReminderLogEntry> = (journal as InMemoryReminderJournal).entries.toList()

    /** Оценка объёма журнала — та же мера, которой пользуется правило ротации (ПМ-30). */
    fun journalStoredBytes(): Long = runBlocking { journal.storedBytes() }

    /**
     * Данность Предыстории: журнал уже что-то накопил (SKL-65).
     *
     * Записи кладутся напрямую, без боевого пути: сценарию нужен журнал заданного
     * возраста и объёма, а не прожитая история срабатываний.
     */
    fun seedJournal(entries: List<ReminderLogEntry>) {
        runBlocking { entries.forEach { journal.record(it) } }
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

    /** Отложенные срабатывания сейчас — по ним проверяются «один повтор» и «не переезжают» (SKL-35). */
    fun deferredRecords(): List<DeferredReminder> = deferredStore.stored.values.toList()

    /**
     * Данность Предыстории: настойчивый повтор уже назначен (НАП-25).
     *
     * Сценарий SKL-88 начинается с назначенного повтора, а не проживает первый показ:
     * запись и будильник ставятся так, как их оставил бы состоявшийся показ.
     */
    fun armRepeat(intakeId: String, at: Instant, attempt: Int = 1) {
        runBlocking {
            deferredStore.put(DeferredReminder(intakeId, at, attempt))
            alarms.schedule(intakeId, at, attempt)
        }
    }

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
     * Последствия отметки: снятие уведомления.
     *
     * Тем же `DismissReminderUseCase`, что у продукта (`ReminderModule`): экран напоминания
     * отметку выполняет, а закрывает карточку она сама.
     */
    fun markSideEffects(): MarkSideEffects = MarkSideEffects { intakeId ->
        DismissReminderUseCase(PlanSource(), PlanSource(), notifications, deferredStore, alarms)(intakeId)
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
            // Карточка удалённого лекарства не собирается вовсе (ПР-14) — тем же правилом,
            // что у настоящего `ReminderDataSource`.
            .filter { it.slot == slot && it.status == IntakeStatus.PENDING && !it.medicationDeleted }

        override suspend fun card(slot: ReminderSlot): ReminderCard? {
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
                    medicationDeleted = medication.isDeleted,
                    profileIsSelf = profile.isSelf,
                    profileMirrorsReminders = profile.mirrorReminders,
                )
            }
        }
    }
}

/** Будильники в памяти: адрес — идентификатор приёма, повторная постановка заменяет (НАП-9). */
private class InMemoryAlarms(private val kind: () -> AlarmKind) : AlarmGateway {

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

    override suspend fun syncMirror(placed: List<DirectBootAlarm>) = Unit

    override fun canScheduleExact(): Boolean = true

    override suspend fun alarmKind(): AlarmKind = kind()

    /** Тестовое срабатывание проверки: адрес один, повторная постановка заменяет (SKL-86). */
    var deliveryCheck: Instant? = null
        private set

    override suspend fun scheduleDeliveryCheck(at: Instant) {
        deliveryCheck = at
    }

    override suspend fun cancelDeliveryCheck() {
        deliveryCheck = null
    }

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

    override suspend fun find(intakeId: String): DeferredReminder? = stored[intakeId]

    override suspend fun put(reminder: DeferredReminder) {
        stored[reminder.intakeId] = reminder
    }

    override suspend fun remove(intakeId: String): Boolean = stored.remove(intakeId) != null
}

/** Шторка в памяти: один слот — одна карточка, показ и снятие меняют видимое (НАП-36). */
private class InMemoryNotifications(private val allowed: () -> Boolean) : NotificationGateway {

    val shown: MutableList<Pair<ReminderCard, ReminderChannel>> = mutableListOf()

    /** Показы тестовой карточки: моменты, с которыми её просили показать (SKL-86). */
    val deliveryChecks: MutableList<LocalDateTime> = mutableListOf()

    /** Доходит ли тестовый показ до шторки: `false` воспроизводит тихий отказ показа. */
    var deliveryCheckReachesTheShade: Boolean = true

    private var deliveryCheckVisible: Boolean = false

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

    override suspend fun showDeliveryCheck(at: LocalDateTime) {
        if (!allowed()) return
        deliveryChecks += at
        deliveryCheckVisible = deliveryCheckReachesTheShade
    }

    override suspend fun isDeliveryCheckShown(): Boolean = deliveryCheckVisible

    override suspend fun cancelDeliveryCheck() {
        deliveryCheckVisible = false
    }

    fun deliveryCheckVisible(): Boolean = deliveryCheckVisible
}

/** Подтверждения проверки в памяти: момент последней объявленной проверки (SKL-86). */
private class InMemoryDeliveryCheckAcknowledgements : DeliveryCheckAcknowledgements {

    private var announcedAt: Instant? = null

    override suspend fun announced(): Instant? = announcedAt

    override suspend fun remember(dueAt: Instant) {
        announcedAt = dueAt
    }
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

    /**
     * Ротация по-настоящему, а не no-op: сценарии SKL-65 проверяют правило ПМ-30,
     * и журнал, который «делает вид», проверял бы сам себя.
     */
    override suspend fun storedBytes(): Long = entries.sumOf { it.estimatedBytes() }

    override suspend fun deleteOlderThan(before: Instant): Int {
        val removed = entries.count { it.occurredAt.isBefore(before) }
        entries.removeAll { it.occurredAt.isBefore(before) }
        return removed
    }

    override suspend fun deleteOldest(protectedSince: Instant, limit: Int): Int {
        val victims = entries.withIndex()
            .filter { it.value.occurredAt.isBefore(protectedSince) }
            .sortedBy { it.value.occurredAt }
            .take(limit)
            .map { it.index }
            .toSet()
        if (victims.isEmpty()) return 0
        val kept = entries.filterIndexed { index, _ -> index !in victims }
        val removed = entries.size - kept.size
        entries.clear()
        entries += kept
        return removed
    }

    /**
     * Оценка цены записи: слагаемые те же, что у SQL-оценки `RoomReminderJournal`
     * (`TDD.md §8`), в упрощённом виде. Приёмке важно, чтобы объём рос с данными
     * и падал после удаления, а не точная байтовая величина.
     */
    private fun ReminderLogEntry.estimatedBytes(): Long {
        var bytes = ROW_OVERHEAD_BYTES.toLong()
        bytes += (intakeId?.length ?: 0) + (medicationId?.length ?: 0) + (profileId?.length ?: 0)
        bytes += (reason?.length ?: 0) + (channel?.id?.length ?: 0)
        return bytes
    }

    private companion object {
        const val ROW_OVERHEAD_BYTES = 48
    }
}

/**
 * Настройки со следом в журнале — поведение `JournalingReminderSettings` (НАП-14).
 *
 * След пишется только при изменении значения: экран настроек перечитывает состояние
 * при каждом возвращении, и запись «на каждое касание» превратила бы журнал
 * срабатываний в журнал открытий настроек.
 */
private class SettingTraceSettings(
    private val store: InMemoryReminderSettings,
    private val journal: ReminderJournal,
    private val clock: ScenarioClock,
) : ReminderSettings {

    override suspend fun read(): ReminderSettingsValues = store.read()

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        val before = store.current().persistentEnabled
        store.setPersistentEnabled(enabled)
        if (before != enabled) trace(ReminderSettingKey.PERSISTENT_ENABLED.trace(enabled))
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        val before = store.current().snoozeMinutes
        store.setSnoozeMinutes(minutes)
        if (before != minutes) trace(ReminderSettingKey.SNOOZE_MINUTES.trace(minutes), minutes = minutes)
    }

    override suspend fun setAlarmStrategy(kind: AlarmKind) {
        val before = store.current().alarmStrategy
        store.setAlarmStrategy(kind)
        if (before != kind) trace(ReminderSettingKey.ALARM_STRATEGY.trace(kind.code))
    }

    private suspend fun trace(value: String, minutes: Int? = null) {
        journal.record(
            ReminderLogEntry(
                occurredAt = clock.now(),
                event = ReminderEvent.SETTINGS_CHANGED,
                reason = value,
                minutes = minutes,
            ),
        )
    }
}

/** Настройки напоминания в памяти: значения по умолчанию — те же, что в продукте. */
private class InMemoryReminderSettings : ReminderSettings {

    private var values = ReminderSettingsValues()

    /** Значения на сейчас — синхронно: механизм выбирается в момент постановки будильника. */
    fun current(): ReminderSettingsValues = values

    override suspend fun read(): ReminderSettingsValues = values

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        values = values.copy(persistentEnabled = enabled)
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        values = values.copy(snoozeMinutes = minutes)
    }

    override suspend fun setAlarmStrategy(kind: AlarmKind) {
        values = values.copy(alarmStrategy = kind)
    }
}
