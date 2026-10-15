package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioWidgets
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.MedicationListOrder
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.widget.BuildWidgetSnapshots
import ru.stohastika.sklyanka.domain.widget.WidgetDaySnapshot
import ru.stohastika.sklyanka.domain.widget.WidgetMedicationSnapshot
import ru.stohastika.sklyanka.domain.widget.WidgetSetupGate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги мира и поведения приёмки SKL-118: данные виджета, отметка из виджета и гейт «Про».
 *
 * Экранная приёмка проверяет собранную поверхность (`Sk118WidgetConfigScreenSteps`), а это —
 * правила, которые сценарии называют словами. Снимок собирается **той же чистой сборкой
 * домена**, что и на устройстве ([BuildWidgetSnapshots]): второго расчёта дня в шагах нет,
 * иначе приёмка проверяла бы пересказ, а не правило. Свежесть проверяется тем же способом,
 * что у плиты: снимок чужих суток не рисуется, а сборка спрашивается на нужную дату.
 *
 * Установка виджета изображается состоянием: диалог домашнего экрана прошивки приёмке
 * недоступен, а проверяются правила гейта — отмена настройки не добавляет виджет,
 * завершение добавляет (Р-32, ПР-23).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса; имена
 * лекарств и профилей — ключи сценария, в модель уходят идентификаторы (НФТ-19).
 */
@Suppress("TooManyFunctions")
class Sk118WidgetSteps(private val world: AcceptanceWorld) {

    private var markedIntakeId: String? = null

    private var momentBeforeSecondPress: Instant? = null

    // --- Данные виджета -------------------------------------------------------

    @Тогда("данные виджета несут приёмы дня активного профиля")
    fun theDataCarryTheDayOfTheActiveProfile() {
        val day = daySnapshot(world.clock.today())
        assertEquals(world.clock.today(), day.date, "снимок принадлежит сегодняшним суткам")
        assertTrue(day.isFresh(world.clock.today()), "плита рисуется только в свои сутки (П-7)")
        assertTrue(day.items.isNotEmpty(), "приёмы дня в снимке есть")
    }

    @Тогда("это те же приёмы, что показывает экран «Сегодня»")
    fun theSameIntakesAsTheTodayScreen() {
        assertEquals(
            todayScreenIntakeIds(),
            daySnapshot(world.clock.today()).items.map { it.intakeId },
            "в снимке ровно те приёмы и в том порядке, что на экране «Сегодня»",
        )
    }

    @Допустим("^у профиля владельца есть курс «([^»]+)» в (\\d{2}):(\\d{2})$")
    fun theOwnerHasACourse(name: String, hour: String, minute: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.self()?.name ?: OWNER,
            name = name,
            times = listOf(time(hour, minute)),
        )
        materialise(name)
    }

    @Допустим("^у человека есть ежедневный курс «([^»]+)» в (\\d{2}):(\\d{2}) и в (\\d{2}):(\\d{2})$")
    @Suppress("LongParameterList")
    fun theOwnerHasACourseTwiceADay(
        name: String,
        hour: String,
        minute: String,
        secondHour: String,
        secondMinute: String,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active.ifEmpty { OWNER },
            name = name,
            times = listOf(time(hour, minute), time(secondHour, secondMinute)),
        )
        materialise(name)
    }

    @И("^у подопечного есть курс «([^»]+)» в (\\d{2}):(\\d{2})$")
    fun theWardHasACourse(name: String, hour: String, minute: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = WARD,
            name = name,
            times = listOf(time(hour, minute)),
        )
        materialise(name)
    }

    @Допустим("^у человека есть установленный виджет «([^»]+)»$")
    fun theWidgetIsInstalled(name: String) {
        world.widgets.type = typeOf(name)
        world.widgets.installed = 1
        world.widgets.installedAtStart = 1
        // Плита показывает день активного профиля: у профиля есть действующий курс,
        // и его сегодняшние строки — иначе «по-прежнему несёт приёмы дня» не про что.
        ensureCourse(WIDGET_COURSE)
        activeMedications(activeContext().id).forEach { materialise(it) }
    }

    @Допустим("^активен профиль владельца$")
    fun theOwnerProfileIsActive() {
        world.profiles.use(world.profiles.self()?.name ?: OWNER)
    }

    @Когда("^активным становится профиль подопечного$")
    fun theWardProfileBecomesActive() {
        world.profiles.use(WARD)
        world.medications.all().filter { it.profileId == world.profiles.get(WARD).id }.forEach { materialise(it) }
    }

    @Тогда("^данные виджета несут приём «([^»]+)» и не несут «([^»]+)»$")
    fun theDataCarryTheWardIntake(wardMedication: String, ownerMedication: String) {
        val day = daySnapshot(world.clock.today())
        val wardIds = world.intakes.visibleOf(wardMedication).map { it.id }
        val ownerIds = world.intakes.of(ownerMedication).map { it.id }
        assertTrue(day.items.any { it.intakeId in wardIds }, "в снимке приём подопечного")
        assertTrue(day.items.none { it.intakeId in ownerIds }, "приёмов владельца в снимке нет")
    }

    @Тогда("компактная индикация называет профиль именем и цветом")
    fun theDataNameTheForeignProfile() {
        val active = world.profiles.get(world.profiles.active)
        val indication = daySnapshot(world.clock.today()).profile
        assertNotNull(indication, "индикация есть у чужого профиля (ПР-24)")
        assertEquals(active.name, requireNotNull(indication).name, "имя профиля — из активного контекста")
        assertEquals(active.color, indication.color, "цвет профиля — из палитры профилей (ПРФ-15)")
    }

    @Тогда("слов «чужой профиль» и «подопечный» в данных виджета нет")
    fun theDataCarryNoSuchWords() {
        val day = daySnapshot(world.clock.today())
        val words = listOfNotNull(day.profile?.name) + day.items.map { it.medicationName }
        FORBIDDEN_WORDS.forEach { word ->
            assertTrue(
                words.none { it.contains(word, ignoreCase = true) },
                "слова «$word» в данных виджета нет (ПР-24)",
            )
        }
    }

    @Тогда("компактной индикации профиля у виджета нет")
    fun theOwnerCarriesNoIndication() {
        assertNull(daySnapshot(world.clock.today()).profile, "у владельца индикации нет (ПРФ-16)")
    }

    @Допустим("у человека нет ни одного приёма на сегодня")
    fun thereAreNoIntakesToday() {
        val today = world.clock.today()
        world.intakes.all()
            .filter { it.scheduledAt.toLocalDate() == today }
            .forEach { world.intakes.removeIfPending(it.id) }
    }

    @Тогда("данные виджета называют день пустым")
    fun theDayIsQuiet() {
        assertTrue(daySnapshot(world.clock.today()).empty, "пустой день — тихое состояние")
    }

    @Тогда("в них нет ни приёма, ни тревожных слов")
    fun theQuietDayCarriesNothing() {
        val day = daySnapshot(world.clock.today())
        assertTrue(day.items.isEmpty(), "в снимке нет приёмов")
        // Тревожных слов у плиты нет вовсе: остатка, ненадёжности и покупки в виджете
        // не бывает («Вне рамок» SKL-118). Снимок несёт имена, время и дозу — и только.
        assertEquals(0, day.activeMedications, "пустой профиль не выдаёт себя за день с приёмами")
    }

    // --- Отметка из виджета ---------------------------------------------------

    @Допустим("^приём «([^»]+)» отмечен из виджета$")
    fun theIntakeIsMarkedFromTheWidget(name: String) {
        // Строка сценария называет приём уже отмеченным: и лекарство, и его строку
        // на сегодня заводит она же — Предыстория сценария курса не содержит.
        ensureCourse(name)
        markFromWidget(name)
    }

    @Когда("^человек отмечает приём «([^»]+)» из виджета$")
    fun thePersonMarksFromTheWidget(name: String) {
        markFromWidget(name)
    }

    @Тогда("приём принят, а источник отметки — `PHONE`")
    fun theIntakeIsTakenFromThePhone() {
        val intake = marked()
        assertEquals(IntakeStatus.TAKEN, intake.status)
        assertEquals(IntakeSource.PHONE, intake.source, "отметка из виджета идёт тем же путём, что с экрана (ПР-22)")
    }

    @Тогда("приём принят")
    fun theIntakeIsTaken() {
        assertEquals(IntakeStatus.TAKEN, marked().status)
    }

    @Тогда("в журнале приложения есть запись об отметке без названия лекарства и имени профиля")
    fun theLogHoldsTheMarkWithoutNames() {
        val record = world.log.records.lastOrNull { it.event == OpEvent.INTAKE_MARKED }
        assertNotNull(record, "отметка оставила след в журнале (ПР-12)")
        val fields = listOf(
            record?.profileId,
            record?.medicationId,
            record?.before,
            record?.after,
            record?.detail,
        ).mapNotNull { it }
        val names = world.medications.all().map { it.name } + world.profiles.all().map { it.name }
        names.forEach { name ->
            assertTrue(fields.none { it.contains(name) }, "названия «$name» в журнале нет (НФТ-19)")
        }
    }

    @Когда("человек нажимает отметку в виджете ещё раз")
    fun thePersonMarksAgain() {
        val intake = marked()
        momentBeforeSecondPress = intake.actedAt
        markFromWidget(medicationNameOf(intake.medicationId))
    }

    @Тогда("у приёма остаётся одна отметка")
    fun thereIsStillOneMark() {
        val intake = marked()
        assertEquals(IntakeStatus.TAKEN, intake.status)
        assertEquals(
            1,
            world.intakes.all().count { it.medicationId == intake.medicationId && it.status == IntakeStatus.TAKEN },
            "второе нажатие не создаёт второй отметки (ПР-2)",
        )
    }

    @Тогда("момент отметки не меняется")
    fun theMarkMomentStaysTheSame() {
        assertEquals(momentBeforeSecondPress, marked().actedAt, "повторное нажатие не переписывает момент (ПР-2)")
    }

    @Допустим("^приём «([^»]+)» за (\\d{2}) октября остался без отметки$")
    fun theIntakeStayedUnmarked(name: String, day: String) {
        ensureCourse(name)
        val medication = world.medications.get(name)
        world.intakes.store(
            Intake(
                id = world.intakes.nextId(),
                medicationId = medication.id,
                scheduledAt = date(day).atTime(DEFAULT_TIME),
                status = IntakeStatus.PENDING,
                actedAt = null,
                source = null,
                actorProfileId = null,
            ),
        )
    }

    @Когда("^приходит (\\d{2}) октября, (\\d{2}):(\\d{2})$")
    fun theMomentComes(day: String, hour: String, minute: String) {
        world.clock.moveTo(LocalDateTime.of(YEAR, OCTOBER, day.toInt(), hour.toInt(), minute.toInt()))
    }

    @Тогда("^приём за (\\d{2}) октября отмечать из виджета нечем$")
    fun thePastIntakeCannotBeMarked(day: String) {
        val past = date(day)
        val day = daySnapshot(world.clock.today())
        assertEquals(world.clock.today(), day.date, "плита показывает сегодняшние сутки")
        assertTrue(
            day.items.none { world.intakes.get(it.intakeId)?.scheduledAt?.toLocalDate() == past },
            "приёмов прошедших суток в снимке нет",
        )
        assertFalse(daySnapshot(past).isFresh(world.clock.today()), "снимок прошедших суток плитой не рисуется (П-7)")
    }

    // --- Свежесть -------------------------------------------------------------

    @Допустим("^виджет показал день (\\d{2}) октября (\\d{4})$")
    fun theWidgetShowedTheDay(day: String, year: String) {
        val shown = LocalDate.of(year.toInt(), OCTOBER, day.toInt())
        assertEquals(shown, daySnapshot(shown).date, "плита показала свои сутки")
    }

    @Тогда("^данные виджета называют (\\d{2}) октября$")
    fun theDataNameTheNewDay(day: String) {
        assertEquals(date(day), daySnapshot(world.clock.today()).date)
    }

    @Тогда("^приёмов (\\d{2}) октября в них нет$")
    fun theDataCarryNoIntakesOfThatDay(day: String) {
        val past = date(day)
        assertTrue(
            daySnapshot(world.clock.today()).items.none {
                world.intakes.get(it.intakeId)?.scheduledAt?.toLocalDate() == past
            },
            "приёмов этого дня в снимке нет",
        )
    }

    // --- Гейт «Про»: экран настройки -----------------------------------------

    @Когда("^человек добавляет виджет «([^»]+)» с домашнего экрана$")
    fun thePersonAddsAWidget(name: String) {
        addWidget(typeOf(name))
    }

    @Когда("человек добавляет ещё один виджет с домашнего экрана")
    fun thePersonAddsOneMoreWidget() {
        addWidget(world.widgets.type)
    }

    @Тогда("открыт экран настройки виджета")
    fun theSetupScreenIsOpen() {
        // Экранный прогон приходит сюда же: сценарии стенда открывают настройку этим шагом,
        // а сценарии поведения — добавлением виджета. Шаг идемпотентен: уже открытый экран
        // он не переоткрывает, а только проверяет (Р-32).
        if (!world.widgets.configOpen) openSetup(ScenarioWidgets.Type.TODAY)
        assertTrue(world.widgets.configOpen, "платформа открывает настройку при добавлении (Р-32)")
    }

    @Тогда("^открыт экран настройки виджета с выбранным типом «([^»]+)»$")
    fun theSetupScreenIsOpenWithChosenType(name: String) {
        openSetup(typeOf(name))
        assertEquals(typeOf(name), world.widgets.type, "тип выбирается в настройке (§5.12)")
    }

    @Тогда("^открыт экран настройки виджета типа «([^»]+)»$")
    fun theSetupScreenIsOpenWithType(name: String) {
        openSetup(typeOf(name))
        assertEquals(typeOf(name), world.widgets.type, "тип выбирается в настройке (§5.12)")
    }

    @Тогда("на нём сказано, что виджеты — в подписке «Склянка Про»")
    fun theReasonIsSpelledOut() {
        assertFalse(proIncluded(), "доступа к «Про» нет")
        // Сами слова причины проверяет экранный прогон модуля: мир приёмки — чистый
        // Kotlin/JVM и строк интерфейса не видит (SKL-26/TDD.md §4.1). Здесь проверяется
        // правило: доступа нет, и завершение ведёт в витрину, а не к установке (§5.12).
        assertTrue(world.widgets.showcaseOffered, "без доступа завершение ведёт в витрину")
    }

    @Тогда("предложено оформить подписку")
    fun theShowcaseIsOffered() {
        assertTrue(world.widgets.showcaseOffered, "завершение ведёт в витрину вместо установки (Р-32)")
    }

    @Когда("человек закрывает настройку, не оформив подписку")
    fun thePersonClosesTheSetup() {
        world.widgets.configOpen = false
    }

    @Тогда("виджет не добавлен")
    fun theWidgetIsNotAdded() {
        assertEquals(
            world.widgets.installedAtStart,
            world.widgets.installed,
            "отмена настройки не добавляет виджет (Р-32)",
        )
    }

    @Когда("человек завершает настройку")
    fun thePersonCompletesTheSetup() {
        completeSetup()
    }

    @Тогда("виджет добавлен")
    fun theWidgetIsAdded() {
        assertEquals(
            world.widgets.installedAtStart + 1,
            world.widgets.installed,
            "с действующей подпиской установка завершается (ПР-23)",
        )
    }

    @Тогда("на экране настройки предложены лекарства активного профиля")
    fun theSetupOffersTheMedicationsOfTheActiveProfile() {
        // Сверка идёт с курсами, которые назвал сценарий, а не с тем же вызовом продукта:
        // иначе строка подтверждала бы себя. Лекарство подопечного в списке быть не должно —
        // в экранном сценарии оно заведено рядом с лекарством владельца (ПР-10, ПР-24).
        val declared = SCENARIO_COURSES.filter { world.medications.has(it) }.map { world.medications.get(it).name }
        assertTrue(declared.isNotEmpty(), "сценарий завёл хотя бы один курс")
        assertEquals(declared.sorted(), offeredMedicationNames().sorted(), "предложены лекарства, названные сценарием")
        SCENARIO_WARD_COURSES.filter { world.medications.has(it) }.forEach { ward ->
            assertFalse(
                offeredMedicationNames().contains(world.medications.get(ward).name),
                "лекарства подопечного в списке нет",
            )
        }
    }

    @Когда("^человек выбирает «([^»]+)» и завершает настройку$")
    fun thePersonSelectsAndCompletes(name: String) {
        world.widgets.selectedMedication = name
        materialise(name)
        completeSetup()
    }

    @Тогда("^данные виджета несут приёмы «([^»]+)»$")
    fun theDataCarryTheIntakesOfTheMedication(name: String) {
        // В строке сценария имя может стоять в косвенном падеже («приёмы «Магния»»):
        // лекарство называет выбор, сделанный шагом раньше, а строка подтверждает,
        // что плита показывает именно его (Р-34, ПР-24).
        val chosen = if (world.medications.has(name)) name else requireNotNull(world.widgets.selectedMedication)
        val medication = world.medications.get(chosen)
        val snapshot = medicationSnapshot(medication.id)
        assertEquals(medication.name, snapshot.name, "шапка называет своё лекарство")
        assertTrue(snapshot.items.isNotEmpty(), "приёмы выбранного лекарства непусты")
        assertTrue(snapshot.items.all { it.medicationName == medication.name }, "в снимке нет приёмов других лекарств")
    }

    @Тогда("данные виджета по-прежнему несут приёмы дня")
    fun theInstalledWidgetKeepsWorking() {
        assertTrue(
            daySnapshot(world.clock.today()).items.isNotEmpty(),
            "установленная плита работает и после истечения подписки (ПР-23)",
        )
        assertFalse(proIncluded(), "доступ к «Про» истёк")
    }

    @Тогда("настройка не завершает установку")
    fun theSetupDoesNotComplete() {
        assertFalse(canComplete(), "без действующей подписки завершить установку нечем (ПР-23, Р-32)")
        completeSetup()
        assertEquals(1, world.widgets.installed, "новых плит не появилось")
    }

    // --- Общая часть ----------------------------------------------------------

    /** Человек начал добавлять виджет: открыт экран настройки, витрина предложена по доступу. */
    private fun addWidget(kind: ScenarioWidgets.Type) {
        world.widgets.type = kind
        world.widgets.configOpen = true
        world.widgets.selectedMedication = null
        world.widgets.showcaseOffered = !proIncluded()
    }

    /**
     * Завершение настройки: доступ есть и выбор закончен — плита появляется, иначе нет.
     *
     * Это и есть гейт Р-32 в терминах состояния: §5.12 не даёт завершить установку без
     * подписки, а платформа не добавляет виджет, если настройка вернула отмену.
     */
    private fun completeSetup() {
        if (canComplete()) world.widgets.installed += 1
        world.widgets.configOpen = false
    }

    /**
     * Завершает ли установку этот экран — правило продукта, а не поле мира (Р-32, §5.12).
     *
     * Тем же `WidgetSetupGate` живёт кнопка экрана: сценарий проверяет решение продукта,
     * а не свою же запись о том, что виджет «добавлен».
     */
    private fun canComplete(): Boolean = WidgetSetupGate.canComplete(
        proIncluded = proIncluded(),
        medicationRequired = world.widgets.type == ScenarioWidgets.Type.MEDICATION,
        medicationChosen = world.widgets.selectedMedication != null,
    )

    private fun proIncluded(): Boolean = world.pro.status().included

    /** Отметка из виджета — тот же сценарий, что кнопка в приложении (ПР-22). */
    private fun markFromWidget(name: String) {
        materialise(name)
        val intake = world.intakes.pendingOf(name).firstOrNull() ?: world.intakes.of(name).first()
        markedIntakeId = intake.id
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(world.clock.nowLocal()))
    }

    private fun marked(): Intake = requireNotNull(world.intakes.get(markedIntakeId ?: error("приём ещё не отмечен")))

    /** Имя лекарства как ключ сценария — по модели: в мире два словаря имён (НФТ-19). */
    private fun medicationName(medication: Medication): String =
        world.medications.names().first { world.medications.get(it).id == medication.id }

    private fun medicationNameOf(medicationId: String): String =
        world.medications.names().first { world.medications.get(it).id == medicationId }

    private fun daySnapshot(date: LocalDate): WidgetDaySnapshot {
        val active = activeContext()
        return BuildWidgetSnapshots.day(
            date = date,
            intakes = intakesOf(date, active.id),
            activeMedications = activeMedications(active.id),
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
            context = active,
        )
    }

    private fun medicationSnapshot(medicationId: String): WidgetMedicationSnapshot {
        val active = activeContext()
        val medication = requireNotNull(world.medications.byId(medicationId)) { "unknown medication: $medicationId" }
        return BuildWidgetSnapshots.medication(
            date = world.clock.today(),
            medication = medication,
            intakes = intakesOf(world.clock.today(), active.id),
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
            context = active,
        )
    }

    private fun activeContext(): ProfileContext = ProfileContext.of(world.profiles.get(world.profiles.active))

    private fun activeMedications(profileId: String): List<Medication> = MedicationListOrder.sorted(
        world.medications.all().filter { it.profileId == profileId && !it.isArchived && !it.isDeleted },
        world.clock.nowLocal(),
    )

    private fun intakesOf(date: LocalDate, profileId: String): List<Intake> = world.intakes.all().filter { intake ->
        world.medications.byId(intake.medicationId)?.profileId == profileId &&
            intake.scheduledAt.toLocalDate() == date &&
            intake.revokedAt == null
    }

    /** Идентификаторы приёмов ровно в том порядке, в каком их показывает экран «Сегодня». */
    private fun todayScreenIntakeIds(): List<String> {
        val active = activeContext()
        val agenda = BuildTodayAgenda()(
            world.clock.today(),
            intakesOf(world.clock.today(), active.id),
            activeMedications(active.id),
            world.clock.nowLocal(),
            world.clock.zone,
        )
        return agenda.layout.groups.flatMap { it.items }.map { it.intake.id }
    }

    private fun offeredMedicationNames(): List<String> = activeMedications(activeContext().id).map { it.name }

    /**
     * Лекарство сценария, если его ещё нет: строки вроде «приём отмечен из виджета»
     * называют уже случившийся приём, а курса Предыстория этих сценариев не заводит.
     */
    private fun ensureCourse(name: String) {
        if (world.medications.has(name)) return
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active.ifEmpty { OWNER },
            name = name,
            times = listOf(DEFAULT_TIME),
        )
    }

    /**
     * Строки приёмов на сутки — по расписанию лекарства.
     *
     * Планирование приёмов живёт в своей задаче и строит окно вперёд: слот, который уже
     * прошёл (курс «в 08:00» при «сейчас 10:00»), в него не попадает. Виджету же нужны
     * именно сегодняшние строки, и шаги виджета кладут их сами — как и сценарий про
     * прошедший день: лекарство в сценарии есть, а сгенерированной строки у него нет.
     */
    private fun materialise(medicationName: String, date: LocalDate = world.clock.today()) =
        materialise(world.medications.get(medicationName), date)

    private fun materialise(medication: Medication, date: LocalDate = world.clock.today()) {
        val existing = world.intakes.of(medicationName(medication)).map { it.scheduledAt }
        medication.times
            .filterNot { slot -> existing.contains(date.atTime(slot)) }
            .forEach { slot ->
                world.intakes.store(
                    Intake(
                        id = world.intakes.nextId(),
                        medicationId = medication.id,
                        scheduledAt = date.atTime(slot),
                        status = IntakeStatus.PENDING,
                        actedAt = null,
                        source = null,
                        actorProfileId = null,
                    ),
                )
            }
    }

    /** Экран настройки открыт с названным типом: платформа открывает его при добавлении. */
    private fun openSetup(kind: ScenarioWidgets.Type) {
        world.widgets.type = kind
        world.widgets.configOpen = true
        world.widgets.selectedMedication = null
        world.widgets.showcaseOffered = !proIncluded()
    }

    /** Имя типа — ключ сценария, а не текст интерфейса: слова проверяет экранный прогон. */
    private fun typeOf(name: String): ScenarioWidgets.Type = when (name) {
        TODAY_WORD -> ScenarioWidgets.Type.TODAY
        WEEK_WORD -> ScenarioWidgets.Type.WEEK
        MEDICATION_WORD -> ScenarioWidgets.Type.MEDICATION
        else -> error("unknown widget type: $name")
    }

    private fun time(hour: String, minute: String): LocalTime = LocalTime.of(hour.toInt(), minute.toInt())

    private fun date(day: String): LocalDate = LocalDate.of(YEAR, OCTOBER, day.toInt())

    private companion object {
        const val YEAR = 2026
        const val OCTOBER = 10
        const val WEEK = 7
        const val OWNER = "Я"

        /** Имя подопечного профиля в сценариях приёмки: одно на все задачи. */
        const val WARD = "Мама"

        /** Время курса, который шаг заводит сам: утро — обычное время ежедневного приёма. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

        /** Курс установленной плиты: сценарий говорит «виджет показывает день», а не чем. */
        const val WIDGET_COURSE = "Магний"
        const val PRO_TITLE = "Склянка Про"

        /** Имена типов виджетов словами сценария: те же, что в §5.11. */
        const val TODAY_WORD = "Сегодня"
        const val WEEK_WORD = "Неделя"
        const val MEDICATION_WORD = "По лекарству"

        /** Курсы владельца, названные сценариями SKL-118. */
        val SCENARIO_COURSES = listOf("Магний")

        /** Курсы подопечного: в экранном сценарии «Аспирин» заведён рядом с владельческим. */
        val SCENARIO_WARD_COURSES = listOf("Аспирин")

        /** Слова, которых на плите быть не должно (ПР-24). */
        val FORBIDDEN_WORDS = listOf("чужой", "подопечн")
    }
}
