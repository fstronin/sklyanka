package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.time.Duration
import java.time.LocalTime

/**
 * Доменные шаги приёмки SKL-35 — откладывание, переживающее перезагрузку (ОВ-75).
 *
 * Правила не копируются: откладывание идёт настоящим `SnoozeReminderUseCase`, пересборка —
 * настоящим `RescheduleRemindersUseCase` поверх хранилища отложенных в памяти, срабатывание —
 * `HandleFiredReminderUseCase`. «Перезагрузка» воспроизводится стиранием будильников при
 * живых записях ([ru.stohastika.sklyanka.acceptance.world.ScenarioReminders.simulateReboot]).
 *
 * Сценарии `@manual` (настоящая перезагрузка, выключение телефона, перелёт) здесь не
 * исполняются: их закрывает чек-лист устройства (`TDD.md §12.1`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class DeferredReminderSteps(private val world: AcceptanceWorld) {

    /** Лекарство, о котором говорит текущая проверка без имени. */
    private var medication: String = FIRST_MEDICATION

    /**
     * Приёмы, которым откладывание было назначено.
     *
     * Запоминаются в момент откладывания: к проверке «не переезжают» повтор уже состоялся,
     * и записи к тому времени сняты — сценарий проверяет, кому они принадлежали, а не
     * что они вечны.
     */
    private var deferredIntakeIds: Set<String> = emptySet()

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Ежедневный курс у чужого профиля с дублированием напоминаний владельцу (ПРФ-5, ПРФ-6).
     *
     * Дублирование задаётся данностью строки: строка Предыстории описывает, с чем сценарий
     * начинается, а не действие человека.
     */
    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) и включённым дублированием напоминаний$")
    fun theForeignProfileHasAMedicationWithMirroredReminders(profile: String, name: String, time: String) {
        world.profiles.get(profile)
        world.profiles.setMirrorReminders(profile, enabled = true)
        world.medications.add(world.profiles, profile, name, times = listOf(LocalTime.parse(time)))
        medication = name
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) ежедневно$")
    fun theProfileHasADailyMedication(profile: String, name: String, time: String) {
        world.medications.add(world.profiles, profile, name, times = listOf(LocalTime.parse(time)))
        medication = name
    }

    /**
     * Напоминание предъявлено — данность Предыстории, а не действие человека.
     *
     * Шаг заводит приём, собирает окно в текущий момент Предыстории и доставляет
     * срабатывание в названный момент — тем же путём, что и телефон.
     */
    @Допустим("^человек видел напоминание о приёме «([^»]+)» на (.+?), (\\d{2}:\\d{2})$")
    fun theReminderIsShown(name: String, date: String, time: String) {
        medication = name
        val moment = world.localMoment(date, time)
        val intake = world.intakes.pending(name, moment)
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        assertTrue(world.reminders.isNotificationShown(intake.id), "the reminder of $name must be shown")
    }

    @Допустим("^человек видел напоминания о приёме «([^»]+)» и о приёме «([^»]+)» на (.+?), (\\d{2}:\\d{2})$")
    fun theRemindersOfBothMedicationsAreShown(first: String, second: String, date: String, time: String) {
        medication = first
        val moment = world.localMoment(date, time)
        val intakes = listOf(first, second).map { world.intakes.pending(it, moment) }
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        intakes.forEach { intake ->
            assertTrue(world.reminders.isNotificationShown(intake.id), "the reminder of $intake must be shown")
        }
    }

    // --- Откладывание --------------------------------------------------------------------------

    @Когда("^в (\\d{2}:\\d{2}) человек откладывает напоминание на (\\d+) минут$")
    fun thePersonPressesSnooze(time: String, minutes: Int) {
        val intake = world.intakes.current()
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        assertTrue(runBlocking { world.reminders.snoozeReminder()(intake.id, minutes) }, "the snooze must take effect")
    }

    @Допустим("^в (\\d{2}:\\d{2}) человек отложил напоминание о приёме «([^»]+)» до (\\d{2}:\\d{2})$")
    fun thePersonSnoozedUntil(time: String, name: String, until: String) {
        medication = name
        val intake = todayIntake(name)
        val at = world.clock.today().atTime(LocalTime.parse(time))
        val target = world.clock.today().atTime(LocalTime.parse(until))
        world.clock.moveTo(at)
        val minutes = Duration.between(at, target).toMinutes().toInt()
        assertTrue(runBlocking { world.reminders.snoozeReminder()(intake.id, minutes) }, "the snooze must take effect")
        assertEquals(
            world.clock.momentOf(target),
            world.reminders.alarmOf(intake.id),
            "the alarm must be moved to the named moment",
        )
    }

    /**
     * Оба напоминания отложены на интервал настройки.
     *
     * Время нажатия строкой не названо: карточки показаны в 14:00, человек откладывает
     * их минутой позже — как в соседних сценариях, где нажатие названо «в 14:01»,
     * и повтор приходит в 14:16.
     */
    @Когда("^оба напоминания отложены на (\\d+) минут$")
    fun bothRemindersAreSnoozed(minutes: Int) {
        world.clock.moveTo(world.clock.nowLocal().plusMinutes(1))
        listOf(FIRST_MEDICATION, SECOND_MEDICATION).forEach { name ->
            val intake = todayIntake(name)
            assertTrue(
                runBlocking { world.reminders.snoozeReminder()(intake.id, minutes) },
                "the snooze of $name must take effect",
            )
        }
        deferredIntakeIds = world.reminders.deferredRecords().map { it.intakeId }.toSet()
    }

    // --- Восстановление плана ------------------------------------------------------------------

    // Строка без «дважды подряд» и строка с ним — одно действие: сценарий идемпотентности
    // проверяет именно повторный вызов, а второй шаг завёл бы вторую копию правила.
    @Когда("^приложение восстанавливает план напоминаний( дважды подряд)?$")
    fun theApplicationRestoresThePlan(twice: String?) {
        repeat(if (twice == null) 1 else 2) {
            world.reminders.reschedule(RescheduleReason.BOOT)
        }
    }

    // --- Проверки: повтор ----------------------------------------------------------------------

    @Тогда("^на (\\d{2}:\\d{2}) назначен ровно один повтор$")
    fun exactlyOneRepeatIsArmed(time: String) {
        val intake = world.intakes.current()
        val at = world.clock.momentOf(world.clock.today().atTime(LocalTime.parse(time)))
        assertEquals(at, world.reminders.alarmOf(intake.id), "the repeat must be armed for $time")
        assertEquals(1, world.reminders.deferredRecords().size, "one intake — one record")
    }

    @Тогда("^в (\\d{2}:\\d{2}) напоминание о приёме «([^»]+)» приходит снова$")
    fun theReminderOfThatMedicationComesAgain(time: String, name: String) {
        medication = name
        val before = cardsOf(name).size
        advanceTo(time)
        assertEquals(before + 1, cardsOf(name).size, "the reminder of $name must arrive again")
    }

    @Тогда("^в (\\d{2}:\\d{2}) человек получает ровно одно напоминание$")
    fun exactlyOneReminderIsReceived(time: String) {
        val before = cardsOf(medication).size
        advanceTo(time)
        assertEquals(before + 1, cardsOf(medication).size, "exactly one reminder must arrive")
    }

    @Тогда("^в (\\d{2}:\\d{2}) повторного напоминания не приходит$")
    fun noRepeatComesAt(time: String) {
        val before = cardsOf(medication).size
        advanceTo(time)
        assertEquals(before, cardsOf(medication).size, "no repeat of $medication may arrive")
    }

    // --- Проверки: отметка и отмена ------------------------------------------------------------

    @Когда("^в (\\d{2}:\\d{2}) человек отменяет эту отметку$")
    fun thePersonUndoesThatMark(time: String) {
        val intake = world.intakes.current()
        world.undo(listOf(intake.id), world.clock.today().atTime(LocalTime.parse(time)))
        assertEquals(UndoOutcome.Applied(undone = 1), world.lastUndoOutcome, "the undo must take effect")
    }

    // --- Проверки: профили ---------------------------------------------------------------------

    @Тогда("^в (\\d{2}:\\d{2}) приходят два повтора — о приёме «([^»]+)» и о приёме «([^»]+)»$")
    fun twoRepeatsArrive(time: String, first: String, second: String) {
        val before = mapOf(first to cardsOf(first).size, second to cardsOf(second).size)
        advanceTo(time)
        assertEquals(before.getValue(first) + 1, cardsOf(first).size, "the repeat of $first must arrive")
        assertEquals(before.getValue(second) + 1, cardsOf(second).size, "the repeat of $second must arrive")
    }

    @Тогда("^карточка повтора о приёме «([^»]+)» показана в контексте профиля «([^»]+)»$")
    fun theRepeatCardIsShownInTheContextOf(name: String, profile: String) {
        val card = requireNotNull(cardsOf(name).lastOrNull()) { "no card of $name was shown" }
        val expected = world.profiles.get(profile)
        assertEquals(expected.name, card.profileName, "the card must carry the name of its profile")
        assertEquals(expected.isSelf, card.profileIsSelf, "the card must carry the context of its profile")
    }

    @Тогда("^откладывания не переезжают из профиля в профиль$")
    fun theDeferralsDoNotMoveBetweenProfiles() {
        val intakes = listOf(FIRST_MEDICATION, SECOND_MEDICATION).map { todayIntake(it).id }.toSet()
        assertEquals(
            intakes,
            deferredIntakeIds,
            "each deferral must belong to the intake it was made for",
        )
    }

    // --- Проверки: граница суток ---------------------------------------------------------------

    @Тогда("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) считается пропущенным без отметки$")
    fun theNamedIntakeIsMissed(name: String, date: String, time: String) {
        val intake = world.intakes.at(name, world.localMoment(date, time))
        assertEquals(IntakeStatus.MISSED, intake.status, "intake of $name at $date $time")
        world.intakes.focusOn(intake)
    }

    @Тогда("^в (\\d{2}:\\d{2}) напоминания не приходит$")
    fun noReminderComes(time: String) {
        val before = world.reminders.shownCards.size
        advanceTo(time)
        assertEquals(before, world.reminders.shownCards.size, "no reminder may arrive")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /** Приём лекарства на сегодня по ближайшему времени его расписания. */
    private fun todayIntake(name: String): Intake {
        val scheduled = world.medications.get(name).times.minOrNull() ?: DEFAULT_TIME
        return world.intakes.pending(name, world.clock.today().atTime(scheduled))
    }

    /** Доставить срабатывания до названного момента — как это делает система. */
    private fun advanceTo(time: String) {
        val at = world.clock.today().atTime(LocalTime.parse(time))
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
    }

    /** Показанные карточки лекарства: по ним проверяются «приходит снова» и «не приходит». */
    private fun cardsOf(name: String): List<ReminderCard> {
        val id = world.medications.get(name).id
        return world.reminders.shownCards.filter { card -> card.items.any { it.medicationId == id } }
    }

    private companion object {
        const val FIRST_MEDICATION = "Л"
        const val SECOND_MEDICATION = "М"

        /** Время приёма, если расписание лекарства почему-то пусто. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(14, 0)
    }
}
