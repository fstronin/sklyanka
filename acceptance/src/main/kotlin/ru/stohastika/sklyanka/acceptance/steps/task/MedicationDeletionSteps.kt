package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.MedicationActionType
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.DeleteOutcome
import ru.stohastika.sklyanka.domain.medication.UndoDeleteOutcome
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.WindowIntakeSelector
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.domain.undo.UndoableMark
import java.time.LocalTime
import java.time.YearMonth

/**
 * Доменные шаги приёмки SKL-99 — мягкое удаление без экранов.
 *
 * Здесь только правила: удаление настоящим `DeleteMedicationUseCase`, возврат настоящим
 * `UndoDeleteMedicationUseCase`, скрытие — теми же репозиториями, что у продукта, и
 * перенос настоящими сценариями домена. Провод файла (поле `deleted_at` в JSON)
 * в доменном прогоне не участвует: его держит настоящий кодек, и проверяет его
 * `JsonTransferCodecTest`; шаг «файл без поля удаления» говорит о состоянии, которое
 * такой файл порождает, — `deletedAt = null`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class MedicationDeletionSteps(private val world: AcceptanceWorld) {

    /** Имя записи файла: слияние перевыдаёт идентификаторы (ПЕР-9), ищем по имени. */
    private var fileMedicationName: String? = null

    /** Идентификатор удалённого лекарства: после загрузки ключ сценария уже не действует. */
    private var deletedMedicationId: String? = null

    /** Снимок выгрузки: чем «человек загружает выгруженный файл». */
    private var exportedSnapshot: TransferSnapshot? = null

    /** Число приёмов каждого лекарства до запуска генерации: «новых не появилось». */
    private var intakesBeforePlan: Map<String, Int> = emptyMap()

    /** Исход последней попытки удаления: им проверяется отказ по границе TAKEN. */
    private var lastDeleteAttempt: DeleteOutcome? = null

    // --- Предыстория ---------------------------------------------------------------------

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмом (.+?) в (\\d{2}:\\d{2})$")
    fun theProfileHasMedicationWithIntake(profile: String, medication: String, date: String, time: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(java.time.LocalTime.parse(time)),
        )
        world.intakes.pending(world.medicationNamed(medication), world.localMoment(date, time))
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмами (\\d{1,2}) и (\\d{1,2}) сентября$")
    fun theProfileHasMedicationWithTwoIntakes(
        profile: String,
        medication: String,
        firstDay: String,
        secondDay: String,
    ) {
        // Слово «сентября» в шаге держит границу с общим шагом расписания: там приёмы —
        // времена суток, здесь — календарные даты, и совпадение шаблонов было бы двусмысленным.
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(MORNING),
        )
        world.intakes.pending(
            world.medicationNamed(medication),
            world.localMoment("$firstDay сентября", MORNING_TEXT),
        )
        world.intakes.pending(
            world.medicationNamed(medication),
            world.localMoment("$secondDay сентября", MORNING_TEXT),
        )
    }

    @Допустим("^у профиля «([^»]+)» заведены лекарства «([^»]+)» и «([^»]+)»$")
    fun theProfileHasTwoMedications(profile: String, first: String, second: String) {
        world.medications.add(profile = world.profiles, profileName = profile, name = first, times = listOf(MORNING))
        world.medications.add(profile = world.profiles, profileName = profile, name = second, times = listOf(MORNING))
    }

    @Допустим("^курс «([^»]+)» убран в архив$")
    fun theCourseIsArchived(medication: String) {
        // Архивация без истории: удалению она не мешает (ПР-14) — в отличие от отметок
        // «принято», которые разворачивает фикстура завершённого курса.
        world.medications.archive(medication, world.clock.now())
    }

    @Допустим("^лекарство «([^»]+)» удалено человеком$")
    fun theMedicationIsAlreadyDeleted(medication: String) {
        deletedMedicationId = world.medications.get(medication).id
        val outcome = world.deleteMedication(medication)
        assertTrue(outcome is DeleteOutcome.Deleted, "the deletion must take effect: $outcome")
    }

    @Допустим("^приёмы лекарства «([^»]+)» запланированы на неделю вперёд$")
    fun theIntakesOfTheMedicationArePlanned(medication: String) {
        world.plan(PlanScope.AllProfiles, PlanReason.MANUAL_CHECK)
        assertTrue(
            world.intakes.of(medication).any { it.status == IntakeStatus.PENDING },
            "the window must carry pending intakes of «$medication»",
        )
    }

    @Допустим("^напоминания на них поставлены$")
    fun theAlarmsAreScheduled() {
        world.reminders.reschedule(RescheduleReason.MANUAL)
        assertTrue(world.reminders.scheduled().isNotEmpty(), "the alarms must be scheduled")
    }

    @Допустим("^в файле выгрузки есть удалённое лекарство «([^»]+)»$")
    fun theFileCarriesADeletedMedication(key: String) {
        val profile = world.profiles.get(world.profiles.active)
        val builder = TransferFileBuilder(world.clock)
            .profile(profile.name, isSelf = true)
            .medication(
                key = key,
                profileKey = profile.name,
                name = "med-file-deleted",
                times = listOf(MORNING),
                deleted = true,
            )
        fileMedicationName = "med-file-deleted"
        world.transfer.prepare(builder.build())
    }

    @Допустим("^файл выгрузки не содержит поля «deleted_at»$")
    fun theFileHasNoDeletionField() {
        // Состояние старого файла: поля нет — удаления нет. Провод держит настоящий кодек
        // (`JsonTransferCodecTest`); здесь проверяется следствие для применения.
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = MAGNESIUM,
            times = listOf(MORNING),
        )
        // Имя записи в модели: после замены ключ сценария уже не действует (ключ — имя файла).
        fileMedicationName = world.medications.get(MAGNESIUM).name
        world.transfer.prepare(world.transfer.snapshot())
    }

    // --- Действия ------------------------------------------------------------------------

    @Когда("^человек удаляет лекарство «([^»]+)»$")
    fun thePersonDeletesTheMedication(medication: String) {
        world.rememberState()
        deletedMedicationId = world.medications.get(medication).id
        val outcome = world.deleteMedicationWithWindow(medication)
        assertTrue(outcome is DeleteOutcome.Deleted, "the deletion must take effect: $outcome")
    }

    @Когда("^человек пытается удалить лекарство «([^»]+)»$")
    fun thePersonTriesToDeleteTheMedication(medication: String) {
        world.rememberState()
        lastDeleteAttempt = world.deleteMedication(medication)
    }

    @Когда("^человек нажимает «Отменить» в окне удаления$")
    fun thePersonUndoesTheDeletion() {
        val window = requireNotNull(world.undoActions.live()) as UndoableDeletion
        world.undoActions.clear()
        val outcome = world.undoDeleteMedication(medicationNamedById(window.medicationId))
        assertTrue(outcome is UndoDeleteOutcome.Undone, "$outcome")
    }

    @Когда("^окно отмены истекает$")
    fun theUndoWindowExpires() {
        world.clock.moveTo(world.clock.nowLocal().plus(UndoableActionStore.WINDOW))
    }

    @Когда("^человек отмечает приём «([^»]+)» принятым с живым окном отмены$")
    fun thePersonMarksWithLiveWindow(medication: String) {
        val name = world.medicationNamed(medication)
        val intake = world.intakes.of(name).first()
        world.markFromScreen(intake.id, world.clock.nowLocal(), MarkResult.TAKEN)
        world.undoActions.publishMark(
            intakeIds = listOf(intake.id),
            result = MarkResult.TAKEN,
            medicationNames = listOf(name),
        )
    }

    @Когда("^приложение генерирует приёмы на неделю вперёд$")
    fun theAppGeneratesTheWindow() {
        intakesBeforePlan = world.medications.names().associateWith { world.intakes.of(it).size }
        world.plan(PlanScope.AllProfiles, PlanReason.MANUAL_CHECK)
    }

    @Когда("^человек выгружает данные в файл$")
    fun thePersonExportsTheFile() {
        exportedSnapshot = world.transfer.snapshot()
        val outcome = world.transfer.export()
        assertTrue(outcome is ExportOutcome.Saved, "$outcome")
    }

    @Когда("^человек загружает выгруженный файл в режиме «Заменить всё»$")
    fun thePersonLoadsTheExportedFile() {
        // Сценарий без выгрузки готовит файл Предысторией: берётся либо снимок выгрузки,
        // либо подготовленный файл — оба проходят один и тот же путь применения.
        val snapshot = exportedSnapshot ?: requireNotNull(world.transfer.prepared) {
            "export or prepare the file before loading it"
        }
        world.transfer.prepare(snapshot)
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        val outcome = world.transfer.apply()
        assertTrue(outcome is TransferOutcome.Applied, "the load must apply: $outcome")
    }

    // --- Проверки: состояние записи -------------------------------------------------------

    @Тогда("^у лекарства «([^»]+)» проставлен момент удаления$")
    fun theDeletionMomentIsSet(medication: String) {
        assertNotNull(world.medications.get(medication).deletedAt, "the deletion moment must be filled")
    }

    @Тогда("^лекарство «([^»]+)» считается удалённым$")
    fun theMedicationIsDeleted(medication: String) {
        assertTrue(world.medications.get(medication).isDeleted)
    }

    @Тогда("^поля архива у лекарства «([^»]+)» пусты$")
    fun theArchiveFieldsAreClear(medication: String) {
        val stored = world.medications.get(medication)
        assertNull(stored.archivedAt, "INV-21: archived_at must be cleared")
        assertNull(stored.archiveReason, "INV-21: archive_reason must be cleared")
    }

    @Тогда("^записи приёмов лекарства «([^»]+)» остались в базе$")
    fun theIntakesAreKept(medication: String) {
        assertTrue(world.intakes.of(medication).isNotEmpty(), "ПР-14: the intake rows must stay")
    }

    @Тогда("^удаление отклонено, потому что есть отметка «принято»$")
    fun theDeletionWasRefused() {
        assertEquals(DeleteOutcome.HasTakenIntakes, lastDeleteAttempt, "the taken mark must block the deletion")
    }

    @Тогда("^момента удаления у лекарства «([^»]+)» нет$")
    fun thereIsNoDeletionMoment(medication: String) {
        assertNull(world.medications.get(medication).deletedAt)
    }

    @Тогда("^лекарство «([^»]+)» остаётся активным$")
    fun theMedicationStaysActive(medication: String) {
        val stored = world.medications.get(medication)
        assertFalse(stored.isDeleted || stored.isArchived, "the record must stay active")
    }

    @Тогда("^лекарство «([^»]+)» остаётся удалённым$")
    fun theMedicationStaysDeleted(medication: String) {
        assertTrue(world.medications.get(medication).isDeleted)
    }

    // --- Проверки: скрытие ----------------------------------------------------------------

    @Тогда("^у профиля «([^»]+)» удалённое лекарство не показывается среди активных$")
    fun theDeletedMedicationIsHiddenFromActive(profile: String) {
        val profileId = world.profiles.get(profile).id
        val active = runBlocking { world.repositories.medicationStore.observeActive(profileId).first() }
        assertTrue(active.none { it.isDeleted }, "ПР-14: a deleted medication must not be active")
    }

    @Тогда("^у профиля «([^»]+)» оно не показывается в архиве$")
    fun theDeletedMedicationIsHiddenFromArchive(profile: String) {
        val profileId = world.profiles.get(profile).id
        val archived = runBlocking { world.repositories.medicationStore.observeArchived(profileId).first() }
        assertTrue(archived.none { it.isDeleted }, "ПР-14: a deleted medication must not be archived")
    }

    @Тогда("^приёмов «([^»]+)» на «Сегодня» нет$")
    fun thereAreNoIntakesToday(medication: String) {
        val profileId = world.profiles.get(world.profiles.active).id
        val day = runBlocking { world.repositories.intakeStore.observeDay(profileId, world.clock.today()).first() }
        val medicationId = world.medications.get(medication).id
        assertTrue(day.none { it.medicationId == medicationId }, "ПР-14: Today must not show them")
    }

    @Тогда("^архивный счётчик профиля его не учитывает$")
    fun theArchiveCounterIgnoresIt() {
        val profileId = world.profiles.get(world.profiles.active).id
        val archived = runBlocking { world.repositories.medicationStore.observeArchived(profileId).first() }
        assertTrue(archived.none { it.isDeleted }, "the archive counter must not count deleted records")
    }

    @Тогда("^приёмы «([^»]+)» не входят в историю профиля$")
    fun theIntakesAreOutOfHistory(medication: String) {
        val profileId = world.profiles.get(world.profiles.active).id
        val window = HistoryWindow.of(HISTORY_MONTH)
        val slice = world.history.slice(profileId, window)
        val medicationId = world.medications.get(medication).id
        assertTrue(slice.intakes.none { it.medicationId == medicationId }, "ПР-14: history excludes them")
    }

    @Тогда("^разбивка по лекарствам удалённое лекарство не называет$")
    fun theBreakdownDoesNotNameIt() {
        val medicationId = world.medications.get(world.medications.last).id
        val period = AdherenceCalculator().of(
            world.history.slice(world.profiles.get(world.profiles.active).id, HistoryWindow.of(HISTORY_MONTH)),
            HistoryWindow.of(HISTORY_MONTH),
            world.clock.today(),
            world.clock.zone,
        )
        assertTrue(period.byMedication.none { it.medicationId == medicationId })
    }

    @Тогда("^отчёт врачу удалённое лекарство не называет$")
    fun theReportDoesNotNameIt() {
        val report = world.history.buildReport(HISTORY_MONTH)
        assertNotNull(report)
        val deletedIds = world.medications.all().filter { it.isDeleted }.map { it.id }.toSet()
        assertTrue(
            requireNotNull(report).medications.none { it.medicationId in deletedIds },
            "ПР-14: the doctor report must not carry deleted records",
        )
    }

    @Тогда("^будущих ожидающихся приёмов у лекарства «([^»]+)» не осталось$")
    fun noFuturePendingIsLeft(medication: String) {
        val now = world.clock.nowLocal()
        assertTrue(
            world.intakes.of(medication).none { it.status == IntakeStatus.PENDING && it.scheduledAt.isAfter(now) },
            "ПР-14: future pending intakes must be revoked",
        )
    }

    @Тогда("^новых приёмов у лекарства «([^»]+)» не появилось$")
    fun noNewIntakesWereBorn(medication: String) {
        val before = intakesBeforePlan[medication] ?: 0
        assertEquals(before, world.intakes.of(medication).size, "ПР-14: a deleted medication plans nothing")
    }

    // --- Проверки: будильники и напоминания ------------------------------------------------

    @Тогда("^перепланирование прошло с причиной «([^»]+)»$")
    fun theRescheduleCarriesTheReason(phrase: String) {
        val expected = when (phrase) {
            "удаление лекарства" -> RescheduleReason.MEDICATION_DELETED
            "возврат удаления" -> RescheduleReason.MEDICATION_UNDELETED
            else -> error("unknown reschedule reason: $phrase")
        }
        val last = runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) }
        assertEquals(expected.code, requireNotNull(last).reason, "the journal must name the reason")
    }

    @Тогда("^ни один поставленный будильник не ведёт к приёму «([^»]+)»$")
    fun noAlarmPointsAtTheIntake(medication: String) {
        val medicationId = world.medications.get(medication).id
        val offending = world.reminders.scheduled().keys.filter { intakeId ->
            world.intakes.get(intakeId)?.medicationId == medicationId
        }
        assertTrue(offending.isEmpty(), "ПР-14: no alarm may stay on «$medication»: $offending")
    }

    @Тогда("^в плане напоминаний приёмов «([^»]+)» нет$")
    fun thePlanHasNoSuchIntakes(medication: String) {
        val medicationId = world.medications.get(medication).id
        val candidates = runBlocking {
            world.reminders.planSource().candidates(
                from = world.clock.nowLocal().minusDays(1),
                toInclusive = world.clock.nowLocal().plusDays(PLAN_HORIZON_DAYS),
            )
        }
        // Отбор — тем же правилом домена, что в продукте: кандидаты приходят как есть,
        // а удалённых отсеивает селектор (ПР-14).
        val planned = WindowIntakeSelector.remindable(candidates)
        assertTrue(planned.none { it.medicationId == medicationId }, "ПР-14: the plan excludes deleted records")
    }

    @Тогда("^карточка напоминания по «([^»]+)» не собирается$")
    fun noReminderCardIsBuilt(medication: String) {
        val medicationId = world.medications.get(medication).id
        val intake = world.intakes.of(medication).firstOrNull()
        val card = intake?.let { stored ->
            runBlocking {
                world.reminders.cardSource().card(
                    ReminderSlot(
                        profileId = world.profiles.get(world.profiles.active).id,
                        scheduledAt = stored.scheduledAt,
                    ),
                    mirroredToWatch = false,
                )
            }
        }
        assertTrue(card == null || card.items.none { it.medicationId == medicationId })
    }

    // --- Проверки: окно отмены ------------------------------------------------------------

    @Тогда("^лекарство «([^»]+)» снова активное$")
    fun theMedicationIsActiveAgain(medication: String) {
        val stored = world.medications.get(medication)
        assertFalse(stored.isDeleted, "the undo must clear the deletion moment")
    }

    @Тогда("^будущие приёмы лекарства «([^»]+)» снова запланированы$")
    fun futureIntakesAreBack(medication: String) {
        val now = world.clock.nowLocal()
        assertTrue(
            world.intakes.of(medication).any { it.status == IntakeStatus.PENDING && it.scheduledAt.isAfter(now) },
            "the undo must return future intakes",
        )
    }

    @Тогда("^живого окна отмены нет$")
    fun thereIsNoLiveWindow() {
        assertNull(world.undoActions.live())
    }

    @Тогда("^живое окно отмены говорит об удалении лекарства$")
    fun theLiveWindowTellsAboutDeletion() {
        assertTrue(world.undoActions.live() is UndoableDeletion, "the cell must carry the deletion")
    }

    @Тогда("^живое окно отмены говорит об отметке$")
    fun theLiveWindowTellsAboutMark() {
        assertTrue(world.undoActions.live() is UndoableMark, "the cell must carry the mark")
    }

    @Тогда("^оно не называет удаление лекарства$")
    fun theLiveWindowIsNotAboutDeletion() {
        assertFalse(world.undoActions.live() is UndoableDeletion)
    }

    // --- Проверки: перенос ------------------------------------------------------------------

    @Тогда("^в файле момент удаления лекарства «([^»]+)» непустой$")
    fun theExportedStateKeepsTheMoment(medication: String) {
        // Провод (поле JSON) держит настоящий кодек и `JsonTransferCodecTest`; выгрузка
        // домена несёт то же состояние, и здесь проверяется оно.
        val exported = requireNotNull(exportedSnapshot) { "export the file first" }
        val stored = exported.medications.firstOrNull { it.name == world.medications.get(medication).name }
        assertNotNull(stored?.deletedAt, "the exported state must keep the deletion moment")
    }

    @Тогда("^лекарство «([^»]+)» на устройстве остаётся удалённым$")
    fun theMedicationStaysDeletedOnDevice(medication: String) {
        val id = requireNotNull(deletedMedicationId) { "the scenario never deleted «$medication»" }
        assertTrue(requireNotNull(world.medications.byId(id)).isDeleted, "ПЕР-10: replace-all must not resurrect it")
    }

    @Тогда("^приёмы лекарства «([^»]+)» на устройстве сохранены$")
    fun theIntakesSurviveOnDevice(medication: String) {
        val id = requireNotNull(deletedMedicationId) { "the scenario never deleted «$medication»" }
        assertTrue(
            world.intakes.all().any { it.medicationId == id },
            "ПЕР-10: intake rows travel with the record",
        )
    }

    @Тогда("^лекарство «([^»]+)» на устройстве появляется удалённым$")
    fun theIncomingRecordIsDeleted(key: String) {
        val name = requireNotNull(fileMedicationName) { "the file medication was not built" }
        val stored = world.medications.all().firstOrNull { it.name == name }
        assertNotNull(stored, "the incoming record «$key» must arrive")
        assertTrue(requireNotNull(stored).isDeleted, "the incoming record must stay deleted")
    }

    @Тогда("^лекарство «([^»]+)» среди активных не показывается$")
    fun theKeyIsHiddenFromActive(key: String) {
        val name = requireNotNull(fileMedicationName) { "the file medication was not built" }
        val stored = requireNotNull(world.medications.all().firstOrNull { it.name == name })
        val active = runBlocking { world.repositories.medicationStore.observeActive(stored.profileId).first() }
        assertTrue(active.none { it.id == stored.id }, "the record must stay hidden: $key")
    }

    @Тогда("^лекарство «([^»]+)» после загрузки активно$")
    fun theMedicationIsActiveAfterLoad(medication: String) {
        val name = requireNotNull(fileMedicationName) { "the old-format file was not prepared" }
        val stored = world.medications.all().firstOrNull { it.name == name }
        assertNotNull(stored, "the loaded medication «$medication» must arrive")
        assertFalse(requireNotNull(stored).isDeleted, "an old file without the field leaves the record active")
    }

    // --- Проверки: аналитика и журнал -------------------------------------------------------

    @Тогда("^аналитика записала событие «([^»]+)» с действием «([^»]+)»$")
    fun theAnalyticsRecorded(eventName: String, action: String) {
        assertEquals("medication_action", eventName)
        val expected = when (action) {
            "deleted" -> MedicationActionType.DELETED
            "undo" -> MedicationActionType.UNDO
            else -> error("unknown action: $action")
        }
        val event = world.analytics.events.filterIsInstance<AnalyticsEvent.MedicationAction>().lastOrNull()
        assertEquals(expected, event?.action, "the event must follow the operation: ${world.analytics.events}")
    }

    @Тогда("^в записанных событиях нет ни названия лекарства, ни его идентификатора$")
    fun theEventsCarryNoMedicalData() {
        val forbidden = world.medications.all().flatMap { listOf(it.id, it.name) }
        world.analytics.events.forEach { event ->
            forbidden.forEach { value ->
                assertFalse(event.toString().contains(value), "the event carries «$value»: $event")
            }
        }
    }

    @Тогда("^в журнале приложения есть запись об удалении лекарства$")
    fun theJournalCarriesTheDeletion() {
        assertTrue(world.log.events.contains(OpEvent.MEDICATION_DELETED), "the deletion must leave a trace")
    }

    @Тогда("^в журнале приложения есть запись об отмене удаления$")
    fun theJournalCarriesTheUndo() {
        assertTrue(
            world.log.events.contains(OpEvent.MEDICATION_DELETED_UNDONE),
            "the undo must leave a trace",
        )
    }

    private fun medicationNamedById(medicationId: String): String = world.medications.all()
        .firstOrNull { it.id == medicationId }
        ?.let { world.medications.names().first { name -> world.medications.get(name).id == medicationId } }
        ?: error("unknown medication id: $medicationId")

    private companion object {
        const val MORNING_TEXT = "08:00"
        val MORNING: LocalTime = LocalTime.parse(MORNING_TEXT)
        const val MAGNESIUM = "Магний B6"

        /** Месяц сценариев SKL-99: «сейчас» приёмки — 25 сентября 2026. */
        val HISTORY_MONTH: YearMonth = YearMonth.of(2026, 9)

        /** Горизонт окна генерации: неделя вперёд, как у продукта (НАП-4). */
        const val PLAN_HORIZON_DAYS = 7L
    }
}
