package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.profile.MirrorRemindersOutcome
import ru.heuri.sklyanka.domain.profile.OpenReminderProfileUseCase
import ru.heuri.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.heuri.sklyanka.domain.reminder.ReminderCard
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги SKL-15: напоминания профилей — группа `@reminders` (`ПРФ-6`, `ПРФ-8`, `ПРФ-20`,
 * `ПРФ-21`, `НАП-12`).
 *
 * Группа доменная (`SKL-26/TDD.md §8.1`): «зазвонит ли телефон» решает отбор приёмов
 * в окне будильников, а не разметка. Поэтому и «приходит напоминание» здесь значит
 * настоящее срабатывание: окно пересобирает `RescheduleRemindersUseCase`, карточку
 * показывает `HandleFiredReminderUseCase`, профиль открывает `OpenReminderProfileUseCase`,
 * отметку ставит `MarkIntakeUseCase` — копий правил в приёмке нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class ProfileReminderSteps(private val world: AcceptanceWorld) {

    /** Приём, о котором говорит последняя названная строка: «он» следующих строк. */
    private var remindedIntakeId: String? = null

    /** Момент, на котором будильники уже доставлены: повторный вопрос по тому же моменту не пересобирает окно. */
    private var deliveredAt: java.time.LocalDateTime? = null

    // --- Предыстория: дублирование напоминаний ---------------------------------

    @Допустим("^у профиля «([^»]+)» (включено|выключено) дублирование напоминаний$")
    fun theMirroringOfThatProfileIs(profile: String, state: String) {
        world.profiles.setMirrorReminders(profile, state == ENABLED)
    }

    @Допустим("^напоминание о приёме «([^»]+)» на (.+?), (\\d{2}:\\d{2}) запланировано$")
    fun theReminderIsScheduled(medication: String, date: String, time: String) {
        assertAlarm(medication, date, time, scheduled = true)
    }

    @Допустим("^напоминание о приёме «([^»]+)» на (.+?), (\\d{2}:\\d{2}) не запланировано$")
    fun theReminderIsNotScheduled(medication: String, date: String, time: String) {
        assertAlarm(medication, date, time, scheduled = false)
    }

    /**
     * Предыстория смены активного профиля: набор будильников обоих профилей до неё.
     *
     * Набор запоминается здесь же — им следующая проверка ловит лишнее перепланирование,
     * которое смена активного профиля обязана не делать (НАП-12).
     */
    @Допустим("^напоминания запланированы на приёмы обоих профилей (\\d{2}) и (\\d{2}) сентября (\\d{4})$")
    fun theRemindersOfBothProfilesAreScheduled(firstDay: String, secondDay: String, year: String) {
        settle()
        val days = listOf(firstDay, secondDay).map { LocalDate.of(year.toInt(), SEPTEMBER, it.toInt()) }
        days.forEach { day ->
            world.profiles.all().forEach { profile ->
                val ofDay = world.intakes.ofProfile(world.profiles, profile.name)
                    .filter { it.scheduledAt.toLocalDate() == day }
                assertTrue(ofDay.isNotEmpty(), "«${profile.name}» has no intakes on $day")
                assertTrue(
                    ofDay.any { world.reminders.alarmOf(it.id) != null },
                    "no reminder of «${profile.name}» on $day is scheduled",
                )
            }
        }
        world.reminders.rememberScheduled()
    }

    // --- Действия: дублирование напоминаний ------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек (включает|выключает) дублирование напоминаний профиля «([^»]+)»$")
    fun thePersonTogglesTheMirroring(date: String, time: String, action: String, profile: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val outcome = runBlocking {
            SetMirrorRemindersUseCase(world.repositories.profileStore, world.reminders.replanner, world.log)(
                world.profiles.get(profile).id,
                action == ENABLES,
            )
        }
        // Переключение обязано быть настоящим событием: флаг записан, окно пересобрано
        // сразу (НАП-11). Иначе следующая проверка «уже запланировано» прошла бы на старом
        // наборе, а человек не получил бы напоминаний до следующего якоря.
        assertTrue(
            outcome is MirrorRemindersOutcome.Applied,
            "the toggle must change the flag through the domain use case, got $outcome",
        )
    }

    // --- Действия и проверки: напоминание диктует профиль ----------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) приходит напоминание о приёме «([^»]+)» профиля «([^»]+)»$")
    fun theReminderComes(date: String, time: String, medication: String, profile: String) {
        world.clock.moveTo(world.localMoment(date, time))
        deliverReminders()
        val intake = requireIntake(medication, profile, date, time)
        remindedIntakeId = intake.id
        assertTrue(
            world.reminders.shownCards.any { card -> card.items.any { it.intakeId == intake.id } },
            "the reminder of «$medication» did not come",
        )
    }

    @Тогда("^приходит напоминание о приёме «([^»]+)» профиля «([^»]+)»$")
    fun theReminderOfThatIntakeComes(medication: String, profile: String) {
        val owner = world.profiles.get(profile)
        val name = world.medicationNamed(medication)
        assertEquals(owner.id, world.medications.get(name).profileId, "«$medication» must belong to «$profile»")
        deliverReminders()
        val intake = requireNotNull(world.intakes.of(name).firstOrNull { it.scheduledAt == world.clock.nowLocal() }) {
            "no intake of «$medication» at ${world.clock.nowLocal()}"
        }
        // Свой профиль напоминает всегда, чужой — по флагу дублирования (ПРФ-6).
        assertTrue(
            world.reminders.shownCards.any { card ->
                card.slot.profileId == owner.id && card.items.any { it.intakeId == intake.id }
            },
            "the reminder of «$medication» of «$profile» did not come",
        )
    }

    @Когда("^человек открывает напоминание$")
    fun thePersonOpensTheReminder() {
        val card = lastCard()
        val context = runBlocking {
            OpenReminderProfileUseCase(
                world.repositories.medicationStore,
                world.repositories.profileStore,
                world.profileSession.switch,
            )(requireNotNull(card.items.firstOrNull()) { "the reminder card has no rows" }.medicationId)
        }
        assertNotNull(context, "the reminder must open in the profile of its intake (ПРФ-20)")
    }

    @Тогда("^карточка напоминания показана в профиле «([^»]+)»$")
    fun theCardIsShownInThatProfile(profile: String) {
        val card = lastCard()
        assertEquals(profile, card.profileName, "the card must name the profile of its intake (ПРФ-8)")
        assertFalse(card.profileIsSelf, "«$profile» must not be shown as the own profile")
        assertEquals(
            profile,
            world.profileSession.context().name,
            "the reminder must switch the app into the profile of its intake (ПРФ-20)",
        )
    }

    @Тогда("^на ней видна индикация профиля «([^»]+)»$")
    fun theCardCarriesTheIndicationOfThatProfile(profile: String) {
        // Признаки чужого профиля на карточке несёт её профиль: `profileIsSelf` и имя —
        // то, из чего экран собирает индикацию (ПРФ-8, ПРФ-11).
        val card = lastCard()
        assertEquals(profile, card.profileName, "the indication must name the profile «$profile»")
        assertFalse(card.profileIsSelf, "the card of a foreign profile must demand the indication (ПРФ-8)")
    }

    @Когда("^человек нажимает «Принято» в (\\d{2}:\\d{2})$")
    fun thePersonPressesTaken(time: String) {
        val card = lastCard()
        val intakeId = requireNotNull(card.items.firstOrNull()) { "the reminder card has no rows" }.intakeId
        val at = world.clock.today().atTime(LocalTime.parse(time))
        world.rememberState()
        world.clock.moveTo(at)
        world.mark(intakeId, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(at))
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек отмечает приём «([^»]+)» принятым из напоминания$")
    fun thePersonMarksThatIntakeFromTheReminder(date: String, time: String, medication: String) {
        world.clock.moveTo(world.localMoment(date, time))
        deliverReminders()
        val intake = world.intakes.at(world.medicationNamed(medication), world.clock.nowLocal())
        remindedIntakeId = intake.id
        val card = requireNotNull(
            world.reminders.shownCards.lastOrNull { card -> card.items.any { it.intakeId == intake.id } },
        ) { "no reminder about the intake of «$medication» came" }
        // Профиль задаёт приём, а не экран: карточка открывает профиль приёма (ПРФ-20).
        runBlocking {
            OpenReminderProfileUseCase(
                world.repositories.medicationStore,
                world.repositories.profileStore,
                world.profileSession.switch,
            )(card.items.first().medicationId)
        }
        world.rememberState()
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(world.clock.nowLocal()))
    }

    @Тогда("^на экране видны признаки чужого профиля$")
    fun theSignsOfAForeignProfileAreVisible() {
        assertTrue(
            world.profileSession.context().showsForeignIndication,
            "after a reminder the app must stay in the profile of its intake and show its signs (ПРФ-21)",
        )
    }

    // --- Проверки: дублирование определяет звонок ------------------------------

    @Тогда("^напоминания о приёме «([^»]+)» не приходит$")
    fun noReminderOfThatIntakeComes(medication: String) {
        settle()
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        // Дублирование выключено: ни один приём профиля не попал в окно (ПРФ-6). Проверка
        // идёт до срабатывания — снятия будильников ею не увидеть.
        assertTrue(
            world.intakes.of(name).none { world.reminders.alarmOf(it.id) != null },
            "an intake of «$medication» is in the alarm window although mirroring is off",
        )
        world.reminders.deliverDue()
        assertFalse(
            world.reminders.shownCards.any { card -> card.items.any { it.medicationId == medicationId } },
            "a reminder of «$medication» came although mirroring is off",
        )
    }

    @Тогда("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) существует$")
    fun thatIntakeExists(medication: String, date: String, time: String) {
        // Учёт не зависит от дублирования: приём существует и истечёт сам (ПРФ-6).
        remindedIntakeId = world.intakes.at(world.medicationNamed(medication), world.localMoment(date, time)).id
    }

    @Тогда("^по истечении окна он переходит в пропущенные$")
    fun theIntakeExpiresByItself() {
        val intake = requiredIntake()
        val deadline = world.intakes.window.deadlineFor(intake.scheduledAt)
        world.rememberState()
        world.clock.moveTo(deadline)
        world.sweep(SweepReason.TIMER)
        assertEquals(
            IntakeStatus.MISSED,
            requireNotNull(world.intakes.get(intake.id)) { "intake ${intake.id} disappeared" }.status,
            "the intake had to expire by itself",
        )
    }

    @Тогда("^попадает в историю профиля «([^»]+)»$")
    fun itLandsInTheHistoryOfThatProfile(profile: String) {
        val intake = requiredIntake()
        assertEquals(
            world.profiles.get(profile).id,
            world.medications.byId(intake.medicationId)?.profileId,
            "the intake must belong to «$profile»",
        )
        assertFalse(intake.isPending, "a pending intake is not history yet")
    }

    // --- Проверки: набор будильников -------------------------------------------

    @Тогда("^набор запланированных напоминаний не меняется$")
    fun theScheduledSetDoesNotChange() {
        val before = requireNotNull(world.reminders.scheduledBefore()) {
            "the scenario never remembered the set of alarms"
        }
        assertEquals(
            before,
            world.reminders.scheduled(),
            "switching the active profile must not change the alarms (НАП-12)",
        )
    }

    @Тогда("^напоминание о приёме «([^»]+)» профиля «([^»]+)» на (.+?), (\\d{2}:\\d{2}) остаётся запланированным$")
    fun theReminderOfThatProfileRemainsScheduled(medication: String, profile: String, date: String, time: String) {
        val owner = world.profiles.get(profile).id
        val name = world.medicationNamed(medication)
        assertEquals(owner, world.medications.get(name).profileId, "«$medication» must belong to «$profile»")
        assertNotNull(
            world.reminders.alarmOf(world.intakes.at(name, world.localMoment(date, time)).id),
            "the reminder of the own profile must stay scheduled (ПРФ-6)",
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Пересобрать окно и доставить то, чему пришло время: окно зависит от флагов профилей. */
    private fun deliverReminders() {
        val at = world.clock.nowLocal()
        if (deliveredAt == at) return
        settle()
        world.reminders.deliverDue()
        deliveredAt = at
    }

    /** Окно будильников: приёмы порождает планировщик, напоминания — настоящая пересборка. */
    private fun settle() {
        world.plan()
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    private fun assertAlarm(medication: String, date: String, time: String, scheduled: Boolean) {
        settle()
        val alarm = world.reminders.alarmOf(
            world.intakes.at(world.medicationNamed(medication), world.localMoment(date, time)).id,
        )
        if (scheduled) {
            assertNotNull(alarm, "the reminder of «$medication» at $date, $time must be scheduled")
        } else {
            assertNull(alarm, "the reminder of «$medication» at $date, $time must not be scheduled")
        }
    }

    private fun requireIntake(medication: String, profile: String, date: String, time: String): Intake {
        val owner = world.profiles.get(profile).id
        val name = world.medicationNamed(medication)
        assertEquals(owner, world.medications.get(name).profileId, "«$medication» must belong to «$profile»")
        return world.intakes.at(name, world.localMoment(date, time))
    }

    private fun requiredIntake(): Intake = requireNotNull(
        world.intakes.get(requireNotNull(remindedIntakeId) { "the scenario never named the intake" }),
    ) { "the intake disappeared from the world" }

    private fun lastCard(): ReminderCard = requireNotNull(world.reminders.shownCards.lastOrNull()) {
        "no reminder was shown in this scenario"
    }

    private companion object {
        const val ENABLED = "включено"
        const val ENABLES = "включает"
        const val SEPTEMBER = 9
    }
}
