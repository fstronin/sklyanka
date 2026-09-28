package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.dateOf
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.acceptance.world.TransferFileBuilder
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.transfer.MatchDecision
import ru.heuri.sklyanka.domain.transfer.TransferFormat
import ru.heuri.sklyanka.domain.transfer.TransferMode
import ru.heuri.sklyanka.domain.transfer.TransferOutcome
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

/**
 * Шаги SKL-19 «Перенос данных: выгрузка и загрузка файлом» — доменная часть.
 *
 * Здесь живут Предыстория, содержимое файла, действия загрузки и проверки состояния
 * устройства. Правила не копируются: снимок собирает `TransferSnapshot.of`, слияние
 * и замену исполняют настоящие `BuildTransferPlan` и `ApplyTransferUseCase`, проверку
 * файла — `ValidateSnapshot`. Мир сценария (`AcceptanceWorld.transfer`) держит порты
 * платформы в памяти, а провод (кодек) подставляет тот прогон, у которого он есть.
 *
 * Один класс, а не два, потому что сценарий связывает строки разных фаз общими фактами:
 * решение по совпадению помнит запись устройства, а проверка истории — приём, о котором
 * говорила Предыстория. Разложенные по классам, эти факты пришлось бы передавать через мир
 * и терять связь между строками.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class TransferSteps(private val world: AcceptanceWorld) {

    /** Лекарство устройства, о котором говорит текущая проверка. */
    private var deviceMedication: String? = null

    /** Идентификатор записи устройства: ею проверяются «время приёма» и «история при своём». */
    private var deviceMedicationId: String? = null

    /** Запись, добавленная из файла решением «добавить отдельно» (ПЕР-21). */
    private var addedMedicationId: String? = null

    /** Имя модели, под которым записи устройства и файла сходятся в одну (НФТ-19). */
    private var fileModelName: String? = null

    /** Приём, названный проверкой «остаётся одна запись». */
    private var checkedIntake: Intake? = null

    /** Запись устройства, которой файл навязывал свой идентификатор (ПЕР-9). */
    private var collidedDevice: Medication? = null

    /** Лекарство, названное строкой файла последним: «его расписание и история перенесены». */
    private var lastFileMedicationKey: String? = null

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^активен свой профиль «(.+?)»$")
    fun theOwnProfileIsActive(name: String) {
        val profile = world.profiles.get(name)
        assertTrue(profile.isSelf, "«$name» must be the phone owner, it is declared first")
        world.profiles.use(name)
    }

    @Допустим("^на устройстве есть профиль «(.+?)», чьи напоминания дублируются владельцу телефона$")
    fun theDeviceHasAForeignProfile(name: String) {
        val profile = world.profiles.get(name)
        assertFalse(profile.isSelf, "«$name» must not be the phone owner")
        assertTrue(profile.mirrorReminders, "the premise says the reminders are mirrored to the owner")
    }

    @Допустим("^у профиля «(.+?)» есть лекарство «(.+?)» дозировкой (\\S+) (\\S+) с приёмами в (.+)$")
    fun theProfileHasAMedicationWithStrength(
        profile: String,
        medication: String,
        strength: String,
        unit: String,
        times: String,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = RussianDates.times(times),
        )
        applyStrength(medication, strength, unit)
    }

    @Допустим("^у профиля «(.+?)» есть лекарство «(.+?)» дозировкой (\\S+) (\\S+) в режиме «по потребности»$")
    fun theProfileHasAnOnDemandMedicationWithStrength(
        profile: String,
        medication: String,
        strength: String,
        unit: String,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = PRN_DAILY_LIMIT,
        )
        applyStrength(medication, strength, unit)
    }

    @Допустим("^у профиля «(.+?)» есть три лекарства$")
    fun theProfileHasThreeMedications(profile: String) {
        repeat(THREE) { index ->
            world.medications.add(
                profile = world.profiles,
                profileName = profile,
                name = "лекарство профиля ${index + 1}",
                times = listOf(SCHEDULE_TIME),
            )
        }
    }

    @Допустим("^в архиве лежит двенадцать завершённых курсов$")
    fun theArchiveHoldsTwelveCourses() {
        repeat(TWELVE) { index ->
            val name = "архивный курс ${index + 1}"
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(SCHEDULE_TIME),
            )
            world.medications.archive(name, world.clock.now())
        }
    }

    @Допустим("^история приёмов ведётся с (.+)$")
    fun theHistoryStartsAt(date: String) {
        val owner = world.profiles.get(world.profiles.active)
        val medication = world.medications.all().first { it.profileId == owner.id }
        world.intakes.markTaken(nameOf(medication), world.dateOf(date).atTime(SCHEDULE_TIME))
    }

    /**
     * Экран переноса в доменных сценариях не собирается: они говорят о содержимом файла
     * и состоянии устройства. Строка нужна, чтобы Предыстория читалась человеком;
     * в экранных прогонах экран строит стенд.
     */
    @Допустим("^человек открыл экран «Перенос данных»$")
    fun thePersonOpenedTheTransferScreen() = Unit

    // --- Содержимое файла ----------------------------------------------------------------------

    @Допустим(
        "^файл выгрузки от (.+?) содержит лекарство «(.+?)» дозировкой (\\S+) (\\S+) профиля «(.+?)», " +
            "которого на устройстве нет$",
    )
    @Suppress("LongParameterList")
    fun theFileHasAnUnmatchedMedication(
        date: String,
        medication: String,
        strength: String,
        unit: String,
        profile: String,
    ) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        val modelName = "file-medication-unmatched"
        builder.medication(
            key = medication,
            profileKey = FILE_SELF,
            name = modelName,
            strength = RussianDates.amount(strength),
            strengthUnit = strengthCode(unit),
            times = listOf(MORNING),
        )
        val moment = world.localMoment(FILE_DATE, MORNING_TEXT)
        builder.intake(
            medicationKey = medication,
            at = moment,
            status = IntakeStatus.TAKEN,
            actedAt = world.clock.momentOf(moment),
            source = IntakeSource.PHONE,
        )
        world.transfer.fileMedicationNames[medication] = modelName
        lastFileMedicationKey = medication
        world.transfer.prepare(builder.build(exportedAt = exportedAt(date)))
    }

    @Допустим(
        "^файл содержит лекарство «(.+?)» дозировкой (\\S+) (\\S+) профиля «(.+?)» с приёмами в (.+?)" +
            "(?: и остатком (\\d+) таблеток)?$",
    )
    @Suppress("LongParameterList")
    fun theFileHasAMatchingMedication(
        medication: String,
        strength: String,
        unit: String,
        profile: String,
        times: String,
        stock: String?,
    ) {
        val device = world.medications.get(world.medicationNamed(medication))
        assertEquals(
            0,
            RussianDates.amount(strength).compareTo(requireNotNull(device.strength)),
            "the premise names the strength of $medication",
        )
        assertEquals(strengthCode(unit), device.strengthUnit, "the premise names the unit of $medication")
        assertEquals(world.profiles.get(profile).id, device.profileId, "the premise names the owner of $medication")

        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medicationLike(
            key = medication,
            profileKey = FILE_SELF,
            device = device,
            times = RussianDates.times(times),
            stockLeft = stock?.let { BigDecimal(it) },
        )
        world.transfer.fileMedicationNames[medication] = device.name
        lastFileMedicationKey = medication
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    @Допустим("^в файле есть приём «(.+?)» за (.+?), (\\d{2}:\\d{2}), отмеченный принятым$")
    fun theFileHasATakenIntake(medication: String, date: String, time: String) {
        val moment = world.localMoment(date, time)
        world.transfer.addFileIntake(
            medicationId = fileMedication(medication).id,
            scheduledAt = moment,
            status = IntakeStatus.TAKEN,
            actedAt = world.clock.momentOf(moment),
            source = IntakeSource.PHONE,
        )
    }

    @Допустим("^на устройстве приём «(.+?)» за (.+?), (\\d{2}:\\d{2}) отмечен принятым в (\\d{2}:\\d{2})$")
    fun theDeviceIntakeIsTakenAt(medication: String, date: String, time: String, markedAt: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(date, time)
        val started = world.clock.nowLocal()
        // Отметка идёт настоящим доменом: состояние, с которого начинается сценарий,
        // обязано быть тем же, каким его оставляет отметка.
        world.markExisting(name, moment, LocalTime.parse(markedAt), MarkResult.TAKEN)
        world.clock.moveTo(started)
        deviceMedication = name
        deviceMedicationId = world.medications.get(name).id
    }

    @Допустим("^тот же приём за (.+?), (\\d{2}:\\d{2}) есть в файле со статусом «ожидается»$")
    fun theSameIntakeIsPendingInTheFile(date: String, time: String) {
        val name = requireNotNull(deviceMedication) { "the scenario named no intake of the device" }
        if (world.transfer.prepared == null) prepareFileWith(name)
        world.transfer.addFileIntake(
            medicationId = fileMedication(name).id,
            scheduledAt = world.localMoment(date, time),
            status = IntakeStatus.PENDING,
        )
    }

    @Допустим("^у профиля «(.+?)» на устройстве напоминания дублируются владельцу телефона$")
    fun theDeviceMirrorsTheRemindersOf(profile: String) {
        assertTrue(
            world.profiles.get(profile).mirrorReminders,
            "the premise says the reminders of «$profile» are mirrored to the owner",
        )
    }

    @Допустим("^в файле у профиля «(.+?)» дублирование напоминаний выключено$")
    fun theFileHasMirroringOffForThatProfile(profile: String) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.profile(profile, mirrorReminders = false)
        builder.medication(
            key = TRANSFERRED_FILE_MEDICATION,
            profileKey = profile,
            name = TRANSFERRED_FILE_MEDICATION,
            strength = BigDecimal.ONE,
            strengthUnit = MILLIGRAM,
            times = listOf(MORNING),
        )
        val moment = world.localMoment(FILE_DATE, MORNING_TEXT)
        builder.intake(
            medicationKey = TRANSFERRED_FILE_MEDICATION,
            at = moment,
            status = IntakeStatus.TAKEN,
            actedAt = world.clock.momentOf(moment),
            source = IntakeSource.PHONE,
        )
        world.transfer.fileMedicationNames[TRANSFERRED_FILE_MEDICATION] = TRANSFERRED_FILE_MEDICATION
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    @Допустим("^файл содержит профиль «(.+?)» с двумя лекарствами$")
    fun theFileHasAProfileWithTwoMedications(profile: String) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.profile(profile)
        repeat(TWO) { index ->
            builder.medication(
                key = "лекарство отца ${index + 1}",
                profileKey = profile,
                name = "file-medication-father-${index + 1}",
                times = listOf(SCHEDULE_TIME),
            )
        }
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    @Допустим(
        "^файл содержит лекарство «(.+?)», а на устройстве уже есть лекарство, " +
            "заведённое с тем же внутренним идентификатором$",
    )
    fun theFileMedicationCollidesByIdentifier(medication: String) {
        val owner = world.profiles.get(world.profiles.active)
        val device = world.medications.all().first { it.profileId == owner.id }
        val modelName = "file-medication-collision"
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medicationLike(
            key = medication,
            profileKey = FILE_SELF,
            device = device,
            times = listOf(MORNING),
            id = device.id,
            name = modelName,
        )
        world.transfer.fileMedicationNames[medication] = modelName
        lastFileMedicationKey = medication
        collidedDevice = device
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    @Допустим(
        "^человек выбрал режим «Добавить к текущим» и подтвердил загрузку файла " +
            "с тремя профилями и семью лекарствами$",
    )
    fun thePersonConfirmedAFileWithManyRecords() {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.profile("Файл второй")
        builder.profile("Файл третий")
        val plan = listOf(FILE_SELF to THREE, "Файл второй" to TWO, "Файл третий" to TWO)
        plan.forEachIndexed { profileIndex, (profileKey, count) ->
            repeat(count) { index ->
                builder.medication(
                    key = "файловое лекарство $profileIndex-$index",
                    profileKey = profileKey,
                    name = "file-medication-$profileIndex-$index",
                    times = listOf(SCHEDULE_TIME),
                )
            }
        }
        world.transfer.useMode(TransferMode.MERGE)
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    // --- Действия загрузки ---------------------------------------------------------------------

    @Допустим("^человек выбрал режим «Добавить к текущим»$")
    fun thePersonChoseMergeMode() {
        world.transfer.useMode(TransferMode.MERGE)
    }

    @Когда("^человек выбирает этот файл и подтверждает загрузку$")
    fun thePersonChoosesTheFileAndConfirms() {
        world.transfer.findMatches()
        world.transfer.apply()
    }

    @Когда("^человек подтверждает загрузку$")
    fun thePersonConfirmsTheImport() {
        world.transfer.apply()
    }

    @Когда("^человек выбирает по совпадению решение «([^»]+)» и подтверждает загрузку$")
    fun thePersonDecidesOnTheMatch(decision: String) {
        val match = requireNotNull(world.transfer.findMatches().firstOrNull()) {
            "the file has no match to decide on"
        }
        rememberDeviceRecord(match.device)
        world.transfer.decide(match.file.id, decisionOf(decision))
        world.transfer.apply()
    }

    @Когда("^человек выбирает решение «оставить моё» и подтверждает загрузку$")
    fun thePersonKeepsMineAndConfirms() {
        val match = requireNotNull(world.transfer.findMatches().firstOrNull()) {
            "the file has no match to decide on"
        }
        rememberDeviceRecord(match.device)
        world.transfer.decide(match.file.id, MatchDecision.KEEP_MINE)
        world.transfer.apply()
    }

    // --- Выгрузка, напоминания, журнал, копия --------------------------------------------------

    /**
     * Шесть ближайших напоминаний как данность Предыстории.
     *
     * Приёмы создаются на границах внутри 48-часового окна (НАП-7) и планируются настоящей
     * пересборкой: набор будильников считается правилом `WindowIntakeSelector`, а не шагом.
     */
    @Допустим("^на устройстве было запланировано шесть ближайших напоминаний$")
    fun theDeviceHadSixReminders() {
        world.intakes.restore(emptyMap())
        val name = world.medications.names().first { world.medications.get(it).times.isNotEmpty() }
        val day = world.clock.today()
        val moments = listOf(
            day.atTime(NOON, 0),
            day.atTime(AFTERNOON, 0),
            day.atTime(EVENING_HOUR, 0),
            day.plusDays(1).atTime(MORNING_HOUR, 0),
            day.plusDays(1).atTime(NOON, 0),
            day.plusDays(1).atTime(AFTERNOON, 0),
        )
        moments.forEach { moment -> world.intakes.pending(name, moment) }
        world.reminders.reschedule(RescheduleReason.APP_START)
        assertEquals(SIX, world.reminders.scheduled().size, "the premise requires six scheduled reminders")
    }

    @Когда("^человек загружает файл в режиме «Заменить всё» и загрузка завершается$")
    fun thePersonReplacesWithTheFile() {
        prepareSimpleFile(medicationName = "file-medication-replanned", times = listOf(MORNING, EVENING))
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        val outcome = world.transfer.apply()
        assertTrue(outcome is TransferOutcome.Applied, "the replacement must be applied, not $outcome")
    }

    @Тогда("^напоминания планируются заново по расписаниям из файла$")
    fun theRemindersFollowTheFile() {
        val fileMedication = requireNotNull(world.transfer.prepared).medications.single()
        val alarms = world.reminders.scheduled()
        assertTrue(alarms.isNotEmpty(), "the import must leave the reminders scheduled")
        val foreign = alarms.keys.filter { intakeId ->
            world.intakes.get(intakeId)?.medicationId != fileMedication.id
        }
        assertEquals(emptyList<String>(), foreign, "every reminder must belong to the schedule from the file")
    }

    @Тогда("^ни одно напоминание по лекарству, которого больше нет на устройстве, не срабатывает$")
    fun removedMedicationsDoNotRemind() {
        val removed = world.transfer.medicationsBeforeApply.map { it.id }.toSet()
        assertTrue(
            world.reminders.scheduled().keys.none { world.intakes.get(it)?.medicationId in removed },
            "an alarm of a removed medication is still scheduled",
        )
        world.clock.moveTo(world.clock.today().atTime(EVENING))
        world.reminders.deliverDue()
        val missed = world.reminders.shownCards.filter { card ->
            card.items.any { item -> world.intakes.get(item.intakeId)?.medicationId in removed }
        }
        assertEquals(emptyList<String>(), missed.map { it.profileName }, "a removed medication reminded the person")
    }

    @Допустим("^человек выполнил загрузку в режиме «Заменить всё» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonPerformedAReplacement(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        prepareSimpleFile(medicationName = "file-medication-journal", times = listOf(MORNING))
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        world.transfer.apply()
    }

    @Когда("^разработчик смотрит локальный журнал приложения$")
    fun theDeveloperLooksAtTheJournal() = Unit

    // Строка шага дословная из Gherkin: разбить её или сократить нельзя.
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    @Тогда(
        "^в нём есть запись о загрузке: время, режим, сколько записей было до и стало после, отметка о страховочной копии$",
    )
    fun theJournalKeepsTheTransfer() {
        val record = requireNotNull(world.log.records.lastOrNull { it.event == OpEvent.TRANSFER_APPLIED }) {
            "the journal must keep the applied transfer: ${world.log.events}"
        }
        assertEquals(world.clock.now(), record.at, "the record must carry the moment of the transfer")
        assertTrue(
            requireNotNull(record.detail).startsWith(TransferMode.REPLACE_ALL.code),
            "the record must name the mode: ${record.detail}",
        )
        val counts = Regex("\\d+/\\d+/\\d+")
        assertTrue(counts.matches(requireNotNull(record.before)), "the record must carry the counts before")
        assertTrue(counts.matches(requireNotNull(record.after)), "the record must carry the counts after")
        assertTrue(
            requireNotNull(record.detail).contains(TransferFormat.SAFETY_COPY_NAME),
            "the record must mark the safety copy: ${record.detail}",
        )
    }

    @Тогда("^в журнале нет ни одного названия лекарства, дозировки или имени профиля$")
    fun theJournalHasNoMedicalData() {
        val text = world.log.records.joinToString("\n") { record ->
            listOfNotNull(
                record.event.name,
                record.profileId,
                record.medicationId,
                record.intakeId,
                record.before,
                record.after,
                record.source,
                record.detail,
            ).joinToString(" ")
        }
        assertTrue(text.isNotEmpty(), "the scenario must have left a journal to check")
        val names = world.transfer.medicationsBeforeApply.map { it.name } +
            world.transfer.profilesBeforeApply.map { it.name } +
            world.transfer.prepared?.medications?.map { it.name }.orEmpty()
        names.forEach { name ->
            assertFalse(occursAsToken(text, name), "the journal must not carry «$name» (НФТ-19):\n$text")
        }
    }

    @Допустим("^страховочная копия была создана при замене (.+)$")
    fun theSafetyCopyWasCreatedAt(date: String) {
        val started = world.clock.nowLocal()
        world.clock.moveTo(world.dateOf(date).atTime(SAFETY_COPY_TIME, 0))
        prepareSimpleFile(medicationName = "file-medication-copy-one", times = listOf(MORNING))
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        world.transfer.apply()
        assertEquals(ONE, world.transfer.files.internalCopyCount, "the first replacement must create the safety copy")
        world.clock.moveTo(started)
    }

    @Когда("^человек выполняет ещё одну замену (.+)$")
    fun thePersonReplacesAgain(date: String) {
        world.clock.moveTo(world.dateOf(date).atTime(SAFETY_COPY_TIME, 0))
        prepareSimpleFile(medicationName = "file-medication-copy-two", times = listOf(EVENING))
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        world.transfer.apply()
    }

    @Тогда("^во внутреннем хранилище приложения остаётся одна страховочная копия — от (.+)$")
    fun theSafetyCopyIsTheOnlyOne(date: String) {
        assertEquals(ONE, world.transfer.files.internalCopyCount, "the copy must be overwritten, not accumulated")
        val copy = requireNotNull(world.transfer.files.safetyCopy())
        assertEquals(TransferFormat.SAFETY_COPY_NAME, copy.name)
        assertEquals(
            world.dateOf(date),
            copy.createdAt.atZone(world.clock.zone).toLocalDate(),
            "the remaining copy must be from the last replacement",
        )
    }

    @Тогда("^копий файлов, сохранённых человеком наружу, приложение не хранит$")
    fun theAppKeepsNoCopiesOfExportedFiles() {
        // Выгрузка кладёт документ туда, куда указал человек, и не перекладывает его
        // во внутреннее хранилище: там по-прежнему одна страховочная копия (ПЕР-36).
        world.transfer.export(handle = "chosen://outside")
        assertEquals(ONE, world.transfer.files.internalCopyCount)
    }

    // --- Проверки слияния ----------------------------------------------------------------------

    @Тогда("^у профиля «(.+?)» появляется лекарство «(.+?)» дозировкой (\\S+) (\\S+)$")
    fun theNewMedicationAppears(profile: String, medication: String, strength: String, unit: String) {
        val owner = world.profiles.get(profile)
        val modelName = requireNotNull(world.transfer.fileMedicationNames[medication])
        val record = requireNotNull(
            world.medications.all().firstOrNull {
                it.profileId == owner.id &&
                    it.name == modelName
            },
        ) {
            "the merged file medication must appear in «$profile»"
        }
        assertEquals(0, RussianDates.amount(strength).compareTo(requireNotNull(record.strength)))
        assertEquals(strengthCode(unit), record.strengthUnit)
        addedMedicationId = record.id
        lastFileMedicationKey = medication
    }

    @Тогда("^его расписание и история приёмов из файла перенесены$")
    fun itsScheduleAndHistoryArrived() {
        val medication = requireNotNull(world.medications.byId(requireNotNull(addedMedicationId)))
        val file = fileMedication(requireNotNull(lastFileMedicationKey))
        assertEquals(file.times, medication.times, "the schedule must come from the file")
        val history = world.intakes.all().filter { it.medicationId == medication.id }
        assertTrue(history.isNotEmpty(), "the history of the file medication must arrive with it")
    }

    @Тогда("^человека ни о чём не спрашивали по этому лекарству$")
    fun thePersonWasNotAsked() {
        val fileMedication = fileMedication(requireNotNull(lastFileMedicationKey))
        assertTrue(
            world.transfer.lastMatches.none { it.file.id == fileMedication.id },
            "an unmatched medication must be added without a question (ПЕР-15 п. 5)",
        )
    }

    @Тогда("^у профиля «(.+?)» (.+?) с названием «(.+?)» и дозировкой (\\S+) (\\S+)$")
    fun theDeviceHasThatManyRecords(
        profile: String,
        count: String,
        medication: String,
        strength: String,
        unit: String,
    ) {
        val owner = world.profiles.get(profile)
        val modelName = requireNotNull(world.transfer.fileMedicationNames[medication])
        val records = world.medications.all().filter { it.profileId == owner.id && it.name == modelName }
        assertEquals(if (count.startsWith("одно")) ONE else TWO, records.size, "records behind «$medication»")
        records.first().let { record ->
            assertEquals(0, RussianDates.amount(strength).compareTo(requireNotNull(record.strength)))
            assertEquals(strengthCode(unit), record.strengthUnit)
        }
        fileModelName = modelName
        addedMedicationId = records.firstOrNull { it.id != deviceMedicationId }?.id
    }

    @Тогда("^время приёма записи на устройстве — (.+)$")
    fun theDeviceRecordKeepsItsTimes(times: String) {
        val record = requireNotNull(world.medications.byId(requireNotNull(deviceMedicationId)))
        assertEquals(RussianDates.times(times), record.times)
    }

    @Тогда("^приём за (.+?), (\\d{2}:\\d{2}) (.+)$")
    fun theFileIntakeLanded(date: String, time: String, outcome: String) {
        val moment = world.localMoment(date, time)
        val found = world.intakes.all().filter { it.scheduledAt == moment }
        when {
            outcome.startsWith("попадает в историю добавленного") ->
                assertEquals(listOf(addedMedicationId), found.map { it.medicationId })

            outcome.startsWith("попадает в историю этого") ->
                assertEquals(listOf(deviceMedicationId), found.map { it.medicationId })

            else -> assertTrue(found.isEmpty(), "the rejected intake must not enter the history at all")
        }
    }

    @Тогда("^в истории за (.+?) остаётся одна запись приёма «(.+?)» на (\\d{2}:\\d{2})$")
    fun exactlyOneIntakeRemains(date: String, medication: String, time: String) {
        val medicationId = world.medications.get(world.medicationNamed(medication)).id
        val moment = world.localMoment(date, time)
        val records = world.intakes.all().filter { it.medicationId == medicationId && it.scheduledAt == moment }
        assertEquals(ONE, records.size, "the history must not double")
        checkedIntake = records.single()
    }

    @Тогда("^она считается принятой, время отметки — (.+?), (\\d{2}:\\d{2})$")
    fun itCountsAsTakenAt(date: String, time: String) {
        val record = requireNotNull(checkedIntake) { "the scenario checked no intake before" }
        assertEquals(IntakeStatus.TAKEN, record.status)
        assertEquals(world.clock.momentOf(world.localMoment(date, time)), record.actedAt)
    }

    @Тогда("^остаток упаковки «(.+?)» не пересчитывается по истории из файла$")
    fun theStockIsNotRecalculated(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val before = requireNotNull(world.transfer.medicationsBeforeApply.firstOrNull { it.id == id })
        val after = requireNotNull(world.medications.byId(id))
        assertEquals(before.stockLeft, after.stockLeft, "the stock must come from the accepted record, not the history")
    }

    @Тогда("^у профиля «(.+?)» на устройстве напоминания по-прежнему дублируются владельцу телефона$")
    fun theMirroringSurvives(profile: String) {
        assertTrue(world.profiles.get(profile).mirrorReminders, "the settings of the device must not be overwritten")
    }

    @Тогда("^цвет профиля «(.+?)» остаётся прежним$")
    fun theColorSurvives(profile: String) {
        val current = world.profiles.get(profile)
        val before = requireNotNull(world.transfer.profilesBeforeApply.firstOrNull { it.id == current.id })
        assertEquals(before.color, current.color)
    }

    @Тогда("^лекарства и история профиля «(.+?)» из файла перенесены$")
    fun theMedicationsAndHistoryArrived(profile: String) {
        val owner = world.profiles.get(profile)
        val modelName = requireNotNull(world.transfer.fileMedicationNames[TRANSFERRED_FILE_MEDICATION])
        val record = requireNotNull(
            world.medications.all().firstOrNull {
                it.profileId == owner.id &&
                    it.name == modelName
            },
        )
        assertTrue(
            world.intakes.all().any { it.medicationId == record.id },
            "the history of the transferred medication must arrive with it",
        )
    }

    @Тогда("^на устройстве появляется профиль «(.+?)» с двумя лекарствами$")
    fun theProfileAppearsWithTwoMedications(profile: String) {
        val owner = requireNotNull(world.profiles.all().firstOrNull { it.name == profile }) {
            "the merged profile must appear on the device"
        }
        assertEquals(TWO, world.medications.all().count { it.profileId == owner.id })
    }

    @Тогда("^своим профилем остаётся «(.+?)»$")
    fun theSelfProfileStays(profile: String) {
        assertEquals(profile, world.profiles.self()?.name)
        assertEquals(
            world.profiles.get(profile).id,
            runBlocking { world.profileSession.activeStore.current() },
            "merging must not switch the active profile",
        )
    }

    /**
     * Утверждение о контракте запросов, а не о разметке (`ПРФ-4`): наблюдать список приёмов
     * без профиля в репозитории нечем — значит, и экрану взять такой список неоткуда.
     * Та же проверка, что в `ProfileIsolationSteps` (SKL-15): экран не может показать
     * приёмы двух профилей вместе.
     */
    @Тогда("^ни один экран не показывает приёмы двух профилей одновременно$")
    fun noScreenShowsBothProfilesAtOnce() {
        val screenReads = IntakeRepository::class.java.methods.filter { method ->
            method.returnType == Flow::class.java && method.genericReturnType.typeName.contains(INTAKE)
        }
        assertTrue(screenReads.isNotEmpty(), "the guard found no observable intake queries at all")
        val unscoped = screenReads
            .filterNot { method -> method.parameterTypes.any { it == String::class.java } }
            .map { it.name }
        assertEquals(emptyList<String>(), unscoped, "an observable intake query appeared without a profile")
    }

    @Тогда("^оба лекарства существуют на устройстве по отдельности$")
    fun bothMedicationsExistSeparately() {
        val device = requireNotNull(collidedDevice)
        val modelName = requireNotNull(world.transfer.fileMedicationNames.values.firstOrNull())
        val fileRecord = requireNotNull(world.medications.all().firstOrNull { it.name == modelName }) {
            "the file medication must arrive on the device"
        }
        assertTrue(fileRecord.id != device.id, "the file medication must get a new identifier (ПЕР-9)")
        assertTrue(world.medications.byId(device.id) != null, "the device record must keep its identifier")
        assertEquals(
            TWO,
            world.medications.all().count { it.id == device.id || it.id == fileRecord.id },
            "the two medications must exist separately",
        )
    }

    @Тогда("^ни одна запись устройства не оказалась переписана записью из файла$")
    fun noDeviceRecordWasOverwritten() {
        val device = requireNotNull(collidedDevice)
        val current = requireNotNull(world.medications.byId(device.id))
        assertEquals(device, current, "the record whose identifier the file used must stay untouched")
    }

    @Тогда("^история каждого лекарства осталась при своём лекарстве$")
    fun theHistoryKeptItsMedication() {
        world.transfer.intakesBeforeApply.forEach { intake ->
            val current = world.intakes.get(intake.id)
            if (current != null) {
                assertEquals(intake.medicationId, current.medicationId)
            }
        }
        assertTrue(
            world.transfer.intakesBeforeApply.isNotEmpty(),
            "the scenario must begin with history of its own",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun rememberDeviceRecord(device: Medication) {
        deviceMedicationId = device.id
        fileModelName = device.name
    }

    /**
     * Файл с одним лекарством — заготовка сценариев, которые содержимое не называют.
     *
     * Сценарий говорит «человек загружает файл», не перечисляя, что в нём; файл обязан
     * существовать, иначе проверка «напоминания планируются заново» была бы беспредметной.
     */
    private fun prepareSimpleFile(medicationName: String, times: List<LocalTime>) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = medicationName,
            profileKey = FILE_SELF,
            name = medicationName,
            times = times,
            startDate = world.clock.today(),
        )
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    private fun prepareFileWith(medication: String) {
        val device = world.medications.get(medication)
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medicationLike(key = medication, profileKey = FILE_SELF, device = device)
        world.transfer.fileMedicationNames[medication] = device.name
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
    }

    private fun fileMedication(medication: String): Medication {
        val modelName = world.transfer.fileMedicationNames[medication] ?: medication
        return requireNotNull(world.transfer.prepared?.medications?.firstOrNull { it.name == modelName }) {
            "the prepared file has no medication behind «$medication»"
        }
    }

    private fun applyStrength(medication: String, strength: String, unit: String) {
        world.medications.update(medication) {
            it.copy(strength = RussianDates.amount(strength), strengthUnit = strengthCode(unit))
        }
    }

    private fun exportedAt(date: String): Instant = world.clock.momentOf(world.localMoment(date, EXPORT_TIME))

    private fun nameOf(medication: Medication): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medication.id },
    ) { "unknown medication id: ${medication.id}" }

    private fun strengthCode(unit: String): String = if (unit == MILLIGRAM_DISPLAY) MILLIGRAM else unit

    /**
     * Встречается ли имя отдельным словом, а не куском идентификатора.
     *
     * `file-med-2` — идентификатор, и именем «med-2» он не является; журналу идентификаторы
     * разрешены (НФТ-19, `TDD.md §10.2`), а названия — нет. Поэтому имя ищется с границами,
     * в которые дефис входит: идентификаторов с именем внутри проверка не ловит.
     */
    private fun occursAsToken(text: String, name: String): Boolean =
        Regex("(?<![\\w-])${Regex.escape(name)}(?![\\w-])").containsMatchIn(text)

    private fun decisionOf(text: String): MatchDecision = when (text) {
        "оставить моё" -> MatchDecision.KEEP_MINE
        "взять из файла" -> MatchDecision.TAKE_FROM_FILE
        "добавить отдельно" -> MatchDecision.ADD_SEPARATELY
        "не загружать" -> MatchDecision.DO_NOT_LOAD
        else -> error("unknown decision: $text")
    }

    private companion object {
        const val FILE_SELF = "файловый свой"
        const val FILE_DATE = "5 октября 2026"
        const val EXPORT_TIME = "18:14"
        const val MORNING_TEXT = "09:00"
        const val SAFETY_COPY_TIME = 10

        const val TWO = 2
        const val THREE = 3
        const val SIX = 6
        const val TWELVE = 12
        const val ONE = 1
        const val PRN_DAILY_LIMIT = 1

        const val MILLIGRAM = "mg"
        const val MILLIGRAM_DISPLAY = "мг"

        const val MORNING_HOUR = 8
        const val NOON = 12
        const val AFTERNOON = 16
        const val EVENING_HOUR = 20

        const val TRANSFERRED_FILE_MEDICATION = "file-medication-transferred"

        const val INTAKE = "Intake"

        val MORNING: LocalTime = LocalTime.of(9, 0)
        val EVENING: LocalTime = LocalTime.of(21, 0)
        val SCHEDULE_TIME: LocalTime = LocalTime.of(8, 0)
    }
}
