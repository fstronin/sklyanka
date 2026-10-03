package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.profile.SwitchOutcome
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.watch.WatchLinkState
import java.io.File
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

    /**
     * Часы подключены как данность Предыстории.
     *
     * Строка часов из продукта снята решением `ADR-0009`, и ни один исполнимый сценарий
     * этой задачи её не проверяет: структура `@watch` остаётся `UNDEFINED`. Предпосылка
     * сохраняется в мире, чтобы сценарий читался человеком и не превращался в неправду.
     */
    @Допустим("^часы HUAWEI Watch GT 6 Pro подключены$")
    fun theWatchIsConnected() {
        world.system.setWatch(WatchLinkState.Connected("GT 6 Pro"))
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
     * Настройки наружу ничего не отправляют — утверждение о сборке, а не о настроении.
     *
     * Сеть в продукте есть, но только аналитическим контуром (ПЛ-3, НФТ-15, ПМ-19,
     * SKL-82): `INTERNET` объявлен единственным сетевым разрешением, до согласия
     * SDK не активируется и запросов нет, а разрешения состояния сети запрещены
     * и проверяются конвейером. Разрешение, приехавшее из чужого манифеста, снимается
     * явно (`tools:node="remove"`); та же проверка, что у истории и журнала.
     */
    @Тогда("^приложение не делает ни одного сетевого обращения$")
    fun theAppNeverReachesTheNetwork() {
        val manifest = File(RepositoryLayout.root, MANIFEST).readText()
        val declarations = Regex(USES_PERMISSION).findAll(manifest)
            .map { it.value }
            .filterNot { it.contains(REMOVE_DIRECTIVE) }
            .toList()
        FORBIDDEN_NETWORK_PERMISSIONS.forEach { permission ->
            assertFalse(
                declarations.any { it.contains(permission) },
                "the application manifest must not declare $permission (НФТ-15)",
            )
        }
        assertFalse(
            declarations.any { it.contains(ANALYTICS_PERMISSION) },
            "the application manifest must not declare $ANALYTICS_PERMISSION (ПМ-31: релиз без сети)",
        )
    }

    private companion object {
        const val FOUR = 4

        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val USES_PERMISSION = "<uses-permission[^>]*>"
        const val REMOVE_DIRECTIVE = "tools:node=\"remove\""
        const val ANALYTICS_PERMISSION = "android.permission.INTERNET"

        val FORBIDDEN_NETWORK_PERMISSIONS = listOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )

        val OWN_INTAKE_TIME: LocalTime = LocalTime.of(12, 0)
        val FOREIGN_INTAKE_TIME: LocalTime = LocalTime.of(20, 0)
    }
}
