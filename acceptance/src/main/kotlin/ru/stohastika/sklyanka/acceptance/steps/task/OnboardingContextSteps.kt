package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import java.math.BigDecimal

/**
 * Доменные шаги приёмки SKL-14: установка, онбординг, отзыв разрешений.
 *
 * Строки живут в общем артефакте, потому что говорят о мире сценария, а не об экране:
 * их читают и стенд экрана «Настроить надёжность» (`feature-onboarding`), и композиция `app`,
 * и прогон диагностики (`feature-reminder`). Сохраняются «видел экран» и три подтверждения
 * вендорских шагов (НАП-20, ОВ-106); состояния разрешений меняются в [AcceptanceWorld.system]
 * и читаются портом при каждом показе (НФТ-52).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class OnboardingContextSteps(private val world: AcceptanceWorld) {

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Чистая установка: экран разрешений человек ещё не видел, оба разрешения цепочки не выданы.
     *
     * На Android 13+ разрешение на уведомления при установке не выдано (ПЛ-13), а право
     * на точные будильники не выдаётся диалогом ни на одной версии — его выдают на экране
     * особого доступа (ПЛ-14). Именно поэтому цепочка начинается с двух разрешений,
     * а не с одного (ОВ-106).
     */
    @Допустим("^приложение только что установлено на телефон с прошивкой EMUI$")
    fun theAppWasJustInstalled() {
        assertFalse(runBlocking { world.onboarding.seen() }, "a fresh install has never seen the onboarding")
        world.system.deny(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)
    }

    // --- Прошивка, разрешения и вендорские подтверждения (SKL-84) -------------------------------

    @Допустим("^телефон человека — Huawei с прошивкой EMUI$")
    fun thePhoneIsHuawei() {
        world.onboarding.vendor = VendorProfile.EMUI
    }

    @Допустим("^разрешения выданы$")
    fun thePermissionsAreGranted() {
        theAndroidPermissionsAreGranted()
    }

    @Допустим("^все вендорские шаги подтверждены$")
    fun theVendorStepsAreConfirmed() {
        allVendorStepsAreConfirmed()
    }

    @Допустим("^разрешения Android выданы$")
    fun theAndroidPermissionsAreGranted() {
        world.system.grant(
            ReminderPermission.NOTIFICATIONS,
            ReminderPermission.NOTIFICATION_CHANNEL,
            ReminderPermission.EXACT_ALARMS,
            ReminderPermission.FULL_SCREEN_INTENT,
        )
    }

    @Допустим("^человек не подтверждал вендорные шаги$")
    fun theVendorStepsAreNotConfirmed() {
        runBlocking {
            world.onboarding.setAppStartupConfirmed(false)
            world.onboarding.setBatterySavingConfirmed(false)
            world.onboarding.setRecentsPinConfirmed(false)
        }
    }

    @Допустим("^все три вендорских шага подтверждены человеком$")
    fun allVendorStepsAreConfirmed() {
        runBlocking {
            world.onboarding.setAppStartupConfirmed(true)
            world.onboarding.setBatterySavingConfirmed(true)
            world.onboarding.setRecentsPinConfirmed(true)
        }
    }

    @Допустим("^человек подтвердил вендорские шаги: запуск приложений, энергосбережение и закрепление в недавних$")
    fun thePersonConfirmedTheVendorSteps() {
        allVendorStepsAreConfirmed()
    }

    /**
     * Ветка «выдано» уже описана шагом планировщика (SKL-13), ветка «не выдано» — шагом
     * заставки (SKL-21): свои не заводятся, иначе шаг станет неоднозначным.
     */
    @Допустим("^уведомления (включены|отключены)$")
    fun theNotificationsAre(state: String) {
        toggle(ReminderPermission.NOTIFICATIONS, granted = state == "включены")
    }

    @Допустим("^канал уведомлений (включён|выключен)$")
    fun theNotificationChannelIs(state: String) {
        toggle(ReminderPermission.NOTIFICATION_CHANNEL, granted = state == "включён")
    }

    @Допустим("^полноэкранные уведомления (выданы|не выданы)$")
    fun theFullScreenIntentIs(state: String) {
        toggle(ReminderPermission.FULL_SCREEN_INTENT, granted = state == "выданы")
    }

    @Допустим("^точные будильники (выданы|не выданы)$")
    fun theExactAlarmsAre(state: String) {
        toggle(ReminderPermission.EXACT_ALARMS, granted = state == "выданы")
    }

    @Допустим("^напоминания не приходили с (.+?), (\\d{2}:\\d{2})$")
    fun theRemindersAreOutdated(date: String, time: String) {
        world.outageSince = world.clock.momentOf(world.localMoment(date, time))
    }

    @Допустим("^человек пропустил онбординг(?: (.+?) в (\\d{2}:\\d{2}))?$")
    fun thePersonSkippedTheOnboarding(date: String?, time: String?) {
        runBlocking { world.onboarding.markSeen() }
        if (date != null && time != null) world.clock.moveTo(world.localMoment(date, time))
    }

    @Допустим("^человек прошёл онбординг (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonPassedTheOnboarding(date: String, time: String) {
        passOnboarding(date, time)
    }

    @Допустим("^он прошёл онбординг (.+?) в (\\d{2}:\\d{2})$")
    fun hePassedTheOnboarding(date: String, time: String) {
        passOnboarding(date, time)
    }

    @Допустим("^онбординг пройден (.+?) в (\\d{2}:\\d{2})$")
    fun theOnboardingIsPassed(date: String, time: String) {
        passOnboarding(date, time)
    }

    @Допустим("^ни одно из разрешений не выдано$")
    fun noneOfThePermissionsIsGranted() {
        // Разрешения цепочки: уведомления и точные будильники. Автозапуск — не разрешение
        // Android, и «не выдано» для него сказать нечего (НАП-20, ПЛ-18).
        world.system.deny(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)
    }

    @И("^телефон не применяет к приложению оптимизацию батареи$")
    fun thePhoneIgnoresBatteryOptimization() {
        world.onboarding.setBatteryOptimizationIgnored(true)
    }

    // --- Отзыв разрешений позже ----------------------------------------------------------------

    @Допустим("^(.+?) в (\\d{2}:\\d{2}) он отозвал разрешение на уведомления в системных настройках$")
    fun thePersonRevokedNotifications(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.system.deny(ReminderPermission.NOTIFICATIONS)
    }

    @Допустим("^(.+?) право на точные будильники было отозвано$")
    fun theExactAlarmsWereRevoked(date: String) {
        world.dateOf(date)
        world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    // --- Данности лекарства и отметки ----------------------------------------------------------

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с остатком (\\d+) таблеток$")
    fun theProfileHasAMedicationWithStock(profile: String, medication: String, stock: Int) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
        )
        world.medications.update(medication) { it.copy(stockLeft = BigDecimal(stock)) }
    }

    /**
     * Ручная отметка как путь человека без разрешений: приложение открыто, приём отмечен.
     *
     * Отметка идёт настоящей точкой входа домена (`MarkIntakeUseCase`); «открывает» здесь
     * означает, что человек пользуется приложением руками, а доступность экранов проверяет
     * шаг композиции. Время в строке — когда человек отметил, а не когда приём назначен
     * (НАП-31): приём заводится по расписанию лекарства.
     */
    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек сам открывает приложение и отмечает приём принятым$")
    fun thePersonOpensTheAppAndMarks(date: String, time: String) {
        val day = world.dateOf(date)
        val name = world.medications.last
        val medication = world.medications.get(name)
        val scheduled = day.atTime(requireNotNull(medication.times.minOrNull()) { "the scenario names no time" })
        val intake = world.intakes.pending(name, scheduled)
        world.rememberState()
        val moment = world.localMoment(date, time)
        world.clock.moveTo(moment)
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(moment))
        world.intakes.focusOn(intake)
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun passOnboarding(date: String, time: String) {
        runBlocking {
            world.onboarding.markSeen()
            // Прошедший цепочку подтвердил все вендорские шаги — иначе они не были бы
            // пройдены (НАП-20, ОВ-106).
            world.onboarding.setAppStartupConfirmed(true)
            world.onboarding.setBatterySavingConfirmed(true)
            world.onboarding.setRecentsPinConfirmed(true)
        }
        // И выдал оба разрешения: пройденная цепочка означает, что они были (НАП-19).
        world.system.grant(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)
        world.clock.moveTo(world.localMoment(date, time))
    }

    private fun toggle(permission: ReminderPermission, granted: Boolean) {
        if (granted) world.system.grant(permission) else world.system.deny(permission)
    }
}
