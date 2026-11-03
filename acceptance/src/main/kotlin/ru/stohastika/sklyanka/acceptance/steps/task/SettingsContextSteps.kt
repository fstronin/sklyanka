package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.NetworkContourFacts
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.profile.SwitchOutcome
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import java.time.LocalTime

/**
 * Предыстория SKL-20 «Настройки» и доменные сценарии задачи.
 *
 * Строки живут в общем артефакте, потому что они доменные: заводят профили, лекарства
 * и состояние системы в мире сценария, который читают и экранный прогон настроек,
 * и прогон диагностики, и композиция `app` (`SKL-26/TDD.md §4.5`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class SettingsContextSteps(private val world: AcceptanceWorld) {

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Свой профиль и его подпись.
     *
     * Лекарство с приёмом в 12:00 заводится здесь намеренно: сценарий переключения
     * называет «приёмы профиля «Я»» как то, чего на экране «Сегодня» быть не должно,
     * — значит, у своего профиля обязаны быть приёмы, иначе проверять нечего.
     */
    @Допустим("^активен свой профиль «(.+?)» с подписью «(.+?)»$")
    fun theOwnProfileIsActiveWithCaption(name: String, caption: String) {
        val profile = world.profiles.get(name)
        assertTrue(profile.isSelf, "«$name» must be the phone owner, it is declared first")
        world.profiles.use(name)
        assertEquals("Мои лекарства", caption, "the caption of the own profile is ПРФ-26")
        world.medications.add(
            profile = world.profiles,
            profileName = name,
            name = "лекарство владельца",
            times = listOf(OWN_INTAKE_TIME),
        )
    }

    @Допустим("^есть профиль «(.+?)» с четырьмя лекарствами, чьи напоминания дублируются владельцу телефона$")
    fun theForeignProfileHasFourMedications(name: String) {
        val profile = world.profiles.get(name)
        assertFalse(profile.isSelf, "«$name» must not be the phone owner")
        repeat(FOUR) { index ->
            world.medications.add(
                profile = world.profiles,
                profileName = name,
                name = "лекарство профиля ${index + 1}",
                times = listOf(FOREIGN_INTAKE_TIME),
            )
        }
        world.profiles.setMirrorReminders(name, true)
        assertTrue(world.profiles.get(name).mirrorReminders, "the premise says the reminders are mirrored")
    }

    @Допустим("^разрешение на уведомления выдано, а точные будильники не разрешены$")
    fun theNotificationsAreGrantedAndExactAlarmsAreNot() {
        world.system.grant(ReminderPermission.NOTIFICATIONS)
        world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^человек открыл вкладку «Настройки»$")
    fun thePersonOpenedTheSettingsTab() = Unit

    // --- Активный профиль переживает перезапуск ------------------------------------------------

    @Допустим("^человек переключился на профиль «(.+?)» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonSwitchedToTheProfile(name: String, date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val outcome = world.profileSession.switchTo(name, SwitchReason.USER_SETTINGS)
        assertTrue(outcome is SwitchOutcome.Switched, "the switch must happen: $outcome")
    }

    @Когда("^приложение закрывают и открывают заново в (\\d{2}:\\d{2})$")
    fun theAppRestartsAt(time: String) {
        // Перезапуск — пересборка поставщика контекста над тем же записанным значением;
        // выживание значения на устройстве — чек-лист SKL-15 (ADR-0007).
        world.profileSession.restart()
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
    }

    @Тогда("^активным остаётся профиль «(.+?)»$")
    fun theSameProfileStaysActive(name: String) {
        val expected = world.profiles.get(name).id
        assertEquals(expected, runBlocking { world.profileSession.activeStore.current() })
    }

    @Тогда("^автоматического возврата в свой профиль не произошло$")
    fun thereWasNoSilentReturnToTheOwnProfile() {
        val self = requireNotNull(world.profiles.self()) { "the scenario must have an own profile" }
        assertNotEquals(self.id, runBlocking { world.profileSession.activeStore.current() })
    }

    // --- Приватность ---------------------------------------------------------------------------

    /**
     * Настройки наружу не ходят: сеть сборки — только контур аналитики (Р-93, `ADR-0017`).
     *
     * Утверждение о **сборке**, и читаются в нём те же данные, что у гейта состава:
     * список сетевых контуров и манифесты всех модулей. Прежде шаг смотрел один
     * `app/src/main/AndroidManifest.xml`; после SKL-165 `INTERNET` приходит из манифеста
     * контура, и такая проверка зеленела бы при любом поведении.
     */
    @Тогда("^сеть сборки объявлена только контуром аналитики$")
    fun theBuildNetworkBelongsToTheAnalyticsContour() {
        NetworkContourFacts.assertDeclaredOnlyByContours()
        NetworkContourFacts.assertShippedContourIsAnalyticsOnly()
        NetworkContourFacts.assertNoForbiddenDeclarations()
    }

    private companion object {
        const val FOUR = 4

        val OWN_INTAKE_TIME: LocalTime = LocalTime.of(12, 0)
        val FOREIGN_INTAKE_TIME: LocalTime = LocalTime.of(20, 0)
    }
}
