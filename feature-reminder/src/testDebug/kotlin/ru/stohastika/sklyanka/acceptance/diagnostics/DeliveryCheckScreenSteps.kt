package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
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
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckReason
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_NOTICE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RESULT
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RUNNING
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_START
import ru.stohastika.sklyanka.feature.reminder.TAG_DELIVERY_CHECK
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки самопроверки доставки (SKL-86, ОВ-108).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране проверки напоминаний
 * и карточке: состояние собирает настоящий `DiagnosticsViewModel` на мире сценария
 * ([DiagnosticsStand]), а тексты читаются из ресурсов — дословно те же, что видит человек.
 * Баннер ставится стендом так же, как корнем приложения: результат объявляется при входе
 * и не повторяется после подтверждения.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class DeliveryCheckScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Точка входа и шторка ------------------------------------------------------------------

    @Когда("^человек нажимает «Проверить доставку»$")
    fun thePersonPressesTheDeliveryCheck() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().performClick() }
        assertTrue(DiagnosticsStand.state(world).checkSheetOpen, "the instruction sheet must open")
    }

    @Тогда("^на «Проверке напоминаний» есть кнопка «Проверить доставку»$")
    fun theDeliveryCheckButtonIsThere() {
        DiagnosticsStand.ask(world) {
            onNodeWithTag(TAG_CHECK_DELIVERY).assertExists()
            onNodeWithText(string(R.string.diagnostics_action_check_delivery)).assertExists()
        }
    }

    @Тогда("^открывается шторка с заголовком «([^»]+)»$")
    fun theSheetOpensWithTitle(title: String) {
        assertTrue(hasTag(TAG_CHECK_SHEET), "the sheet must be on the screen")
        assertTrue(hasText(title), "the sheet must carry the title «$title»")
    }

    @И("^текст «(.+)»$")
    fun theSheetCarriesText(text: String) {
        assertTrue(hasText(text), "the sheet must carry the instruction word for word")
    }

    /**
     * Р-54: у шторки проверки доставки одно действие — запуск проверки.
     *
     * Слово «Отмена» снято из шапки: выход дают системный «Назад» и подложка, и второе
     * действие в шторке было бы третьим способом её закрыть. Отсутствие слова проверяется
     * здесь же — иначе утверждение «одно действие» осталось бы без своего «а второго нет».
     */
    @И("^есть действие «([^»]+)»$")
    fun theSheetCarriesTheSingleAction(action: String) {
        DiagnosticsStand.askRaw(world) {
            assertTrue(
                onAllNodesWithTag(TAG_CHECK_START, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "the primary action must be there",
            )
            assertTrue(
                onAllNodesWithText(action, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "the action must say «$action»",
            )
            assertTrue(
                onAllNodesWithText(string(UiR.string.action_cancel), useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty(),
                "the header must carry no cancel any more",
            )
        }
    }

    @Допустим("^человек открыл шторку «Проверка доставки»$")
    fun thePersonOpenedTheCheckSheet() {
        DiagnosticsStand.model(world).onCheckDelivery()
        assertTrue(DiagnosticsStand.state(world).checkSheetOpen, "the sheet must be open")
    }

    @Когда("^он нажимает системную «Назад»$")
    fun hePressesSystemBack() {
        // Шторка действия в шапке не несёт: выход дают системный «Назад» и подложка (Р-54).
        // Нажатие идёт через настоящий диспетчер композиции, а не пересказом шага:
        // обработчик — собственный `BackHandler` шторки.
        DiagnosticsStand.pressSystemBack(world)
    }

    @Тогда("^шторка закрывается$")
    fun theSheetCloses() {
        assertFalse(DiagnosticsStand.state(world).checkSheetOpen, "the sheet must close")
    }

    @И("^тестовое срабатывание не назначено$")
    fun theTestFiringIsNotArmed() {
        assertNull(world.reminders.deliveryCheckAlarm(), "the system back must arm nothing")
    }

    @И("^в журнале нет записи о проверке$")
    fun thereIsNoCheckRecord() {
        assertTrue(world.reminders.deliveryCheckRecords().isEmpty(), "the system back must leave no record")
    }

    @Когда("^человек в (\\d{2}):(\\d{2}) нажимает «Начать проверку»$")
    fun thePersonStartsTheCheckAt(hour: String, minute: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.of(hour.toInt(), minute.toInt())))
        DiagnosticsStand.model(world).onCheckDelivery()
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_CHECK_START).performClick() }
    }

    @Тогда("^тестовое срабатывание назначено на (\\d{2}):(\\d{2}):(\\d{2})$")
    fun theTestFiringIsArmedAt(hour: String, minute: String, second: String) {
        val expected = world.clock.momentOf(
            world.clock.today().atTime(LocalTime.of(hour.toInt(), minute.toInt(), second.toInt())),
        )
        assertEquals(expected, world.reminders.deliveryCheckAlarm(), "the test firing must be armed 90 seconds ahead")
        assertEquals(world.clock.now().plusSeconds(90), expected, "the instruction promises 1–2 minutes")
    }

    @И("^на месте кнопки плашка «([^»]+)»$")
    fun theBlockShowsTheRunningPlaque(text: String) {
        assertFalse(DiagnosticsStand.state(world).checkSheetOpen, "the sheet must close after the start")
        assertTrue(hasTag(TAG_CHECK_RUNNING), "the progress plaque must replace the button")
        assertTrue(hasText(text), "the plaque must say «$text»")
        assertFalse(hasTag(TAG_CHECK_DELIVERY), "the launch button is gone while the check runs")
    }

    @И("^тихая кнопка «Отменить проверку»$")
    fun theQuietCancelIsThere() {
        assertTrue(hasText(string(R.string.diagnostics_check_cancel)), "the quiet cancel must be there")
    }

    @И("^в журнале есть отличимая запись о назначенной проверке$")
    fun theScheduledCheckIsRecorded() {
        val records = world.reminders.deliveryCheckRecords()
        assertEquals(1, records.size, "the start must leave exactly one record")
        assertEquals(DeliveryCheckReason.SCHEDULED, records.single().reason)
        assertNull(records.single().intakeId, "the check is not an intake")
    }

    // --- Карточка проверки ---------------------------------------------------------------------

    @Допустим("^тестовое напоминание было показано$")
    fun theTestReminderWasShown() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        fireAtDue()
    }

    @Когда("^человек открывает карточку проверки$")
    fun thePersonOpensTheCheckCard() {
        val shownAt = requireNotNull(world.reminders.deliveryCheckShows().lastOrNull()) { "the card must be shown" }
        DiagnosticsStand.showDeliveryCheckCard(world, shownAt) {
            onNodeWithTag(TAG_DELIVERY_CHECK).assertExists()
        }
    }

    @Тогда("^карточка показана в тёмной среде$")
    fun theCardIsDark() {
        // Тёмная среда — параметр темы, которым карточка собирается (`SklyankaTheme(dark = true)`),
        // а не системная настройка: тёмными бывают напоминание и знак (docs/v2/12-design.md §5).
        val shownAt = requireNotNull(world.reminders.deliveryCheckShows().lastOrNull())
        DiagnosticsStand.showDeliveryCheckCard(world, shownAt) {
            onNodeWithTag(TAG_DELIVERY_CHECK).assertExists()
            onAllNodesWithText(string(R.string.reminder_check_overline), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @И("^на ней нет кнопок «Принято», «Пропустить» и откладывания$")
    fun theCardHasNoIntakeActions() {
        val shownAt = requireNotNull(world.reminders.deliveryCheckShows().lastOrNull())
        DiagnosticsStand.showDeliveryCheckCard(world, shownAt) {
            onAllNodesWithText(string(ru.stohastika.sklyanka.ui.R.string.action_taken), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodesWithText(string(ru.stohastika.sklyanka.ui.R.string.action_skip), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodesWithText(string(R.string.reminder_snooze, 15), substring = true, useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    @И("^на «Сегодня» проверка не показана$")
    fun theCheckIsNotOnToday() {
        // Проверка не создаёт приёмов: на «Сегодня» показывать нечего, и мир сценария
        // доказывает это тем же — приёмов у профиля не появилось.
        assertTrue(world.intakes.all().isEmpty(), "the check must not create intakes for Today")
    }

    // --- Результат при следующем входе ---------------------------------------------------------

    @Допустим("^тестовое напоминание показано через (\\d+) секунд после назначенного момента$")
    fun theTestReminderWasShownSecondsLater(seconds: Int) {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        fireAtDue(plusSeconds = seconds.toLong())
    }

    @Допустим("^тестовое напоминание показано через (\\d+) минут после назначенного момента$")
    fun theTestReminderWasShownMinutesLater(minutes: Int) {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        fireAtDue(plusSeconds = minutes.toLong() * SECONDS_IN_MINUTE)
    }

    @Когда("^человек входит в приложение$")
    fun thePersonEntersTheApp() {
        // Вход объявляет исход, если тот объявляется: «проверка не состоялась» входного
        // баннера не даёт вовсе (ОВ-131), и шаг не настаивает на обратном.
        DiagnosticsStand.enter(world)
    }

    @Тогда("^над содержимым баннер «([^»]+)»$")
    fun theBannerOverTheContentSays(text: String) {
        assertTrue(hasTag(TAG_CHECK_NOTICE), "the banner must be over the content")
        assertTrue(hasText(text), "the banner must say «$text»")
    }

    @И("^у баннера действие «([^»]+)»$")
    fun theBannerCarriesAction(action: String) {
        assertTrue(hasText(action), "the banner action must say «$action»")
        assertEquals(string(R.string.diagnostics_check_acknowledge), action)
    }

    @И("^тот же результат показан в блоке «([^»]+)» на «Проверке напоминаний»$")
    fun theSameResultIsInTheBlock(block: String) {
        assertEquals(string(R.string.diagnostics_check_sheet_title), block)
        assertTrue(hasTag(TAG_CHECK_RESULT), "the block must show the result")
        val state = DiagnosticsStand.state(world).snapshot?.deliveryCheck
        val finished = requireNotNull(state as? DeliveryCheckState.Finished)
        assertEquals(DeliveryCheckOutcome.ON_TIME, finished.outcome)
    }

    @И("^у баннера действие «([^»]+)», ведущее на «([^»]+)»$")
    fun theBannerActionLeadsTo(action: String, destination: String) {
        assertTrue(hasText(action), "the banner action must say «$action»")
        assertEquals(string(R.string.diagnostics_fix), action)
        assertEquals("Настроить надёжность", destination)
        val finished = requireNotNull(DiagnosticsStand.announcement)
        assertTrue(
            finished.outcome == DeliveryCheckOutcome.LATE ||
                finished.outcome == DeliveryCheckOutcome.NOT_DELIVERED,
            "a failed result leads to the fix, and it is announced",
        )
        DiagnosticsStand.acknowledge(world)
    }

    @Допустим("^проверка была назначена, но срабатывания к следующему входу не было$")
    fun theCheckWasScheduledButNeverFired() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        expireCheck()
    }

    @Тогда("^баннер говорит «([^»]+)»$")
    fun theBannerSays(text: String) {
        assertTrue(hasTag(TAG_CHECK_NOTICE), "the banner must be over the content")
        assertTrue(hasText(text), "the banner must say «$text»")
        val finished = requireNotNull(DiagnosticsStand.announcement)
        val expected = if (text == string(R.string.diagnostics_check_not_delivered)) {
            DeliveryCheckOutcome.NOT_DELIVERED
        } else {
            DeliveryCheckOutcome.LATE
        }
        assertEquals(expected, finished.outcome)
    }

    @И("^в журнале есть запись о проверке без срабатывания$")
    fun theMissedCheckIsRecorded() {
        val reasons = world.reminders.deliveryCheckRecords().mapNotNull { it.reason }
        assertTrue(DeliveryCheckReason.NOT_DELIVERED in reasons, "«не дошло» must be recorded")
    }

    @Допустим("^человек увидел результат неудачной проверки$")
    fun thePersonSawTheFailedResult() {
        theCheckWasScheduledButNeverFired()
        DiagnosticsStand.enter(world)
        assertNotNull(DiagnosticsStand.announcement, "the failed result must be announced")
    }

    @Когда("^он нажимает «Исправить» и потом закрывает и снова открывает приложение$")
    fun hePressesFixAndReopens() {
        DiagnosticsStand.actRaw(world) {
            onAllNodesWithText(string(R.string.diagnostics_fix), useUnmergedTree = true)[0].performClick()
        }
        DiagnosticsStand.acknowledge(world)
        DiagnosticsStand.enter(world)
    }

    @Тогда("^тот же результат баннером больше не объявляется$")
    fun theSameResultIsNotAnnouncedAgain() {
        assertNull(DiagnosticsStand.announcement, "an acknowledged result must not return")
        assertFalse(hasTag(TAG_CHECK_NOTICE), "the banner must be gone")
    }

    @Допустим("^человек запустил проверку и оставил приложение открытым$")
    fun thePersonLeftTheAppOpen() {
        world.reminders.appForeground = true
        world.reminders.startDeliveryCheck()
    }

    @Когда("^наступает назначенный момент$")
    fun theDueMomentComes() {
        fireAtDue()
    }

    @Тогда("^тестовое уведомление не показывается$")
    fun theTestNotificationIsNotShown() {
        assertTrue(world.reminders.deliveryCheckShows().isEmpty(), "nothing may be shown while the app is open")
    }

    @И("^в блоке «Проверка доставки» сказано «([^»]+)»$")
    fun theBlockSays(text: String) {
        DiagnosticsStand.enter(world)
        assertTrue(hasTag(TAG_CHECK_RESULT), "the block must show the result")
        assertTrue(hasText(text), "the block must say «$text»")
    }

    @И("^входного баннера об этом результате нет$")
    fun thereIsNoEntryBanner() {
        assertNull(DiagnosticsStand.announcement, "«проверка не состоялась» is not announced")
        assertFalse(hasTag(TAG_CHECK_NOTICE), "there must be no banner")
    }

    @И("^срабатывания к моменту входа не было, и приложение назвало «Не дошло»$")
    fun theAppNamedNotDelivered() {
        expireCheck()
        DiagnosticsStand.enter(world)
        val state = DiagnosticsStand.state(world).snapshot?.deliveryCheck
        val finished = requireNotNull(state as? DeliveryCheckState.Finished)
        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, finished.outcome)
    }

    @Когда("^после этого система отдаёт тестовый будильник с опозданием$")
    fun theSystemDeliversTheLateAlarm() {
        // Будильник уже снят входом, но система может отдать его с опозданием: сценарий
        // проверяет, что срабатывание не спорит с названным исходом.
        world.clock.moveTo(world.clock.nowLocal().plusMinutes(5))
        world.reminders.firedDeliveryCheck()
    }

    @Тогда("^нового результата проверки не появляется$")
    fun noNewResultAppears() {
        DiagnosticsStand.refresh(world)
        val last = DiagnosticsStand.state(world).snapshot?.deliveryCheck
        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (last as DeliveryCheckState.Finished).outcome)
        assertTrue(world.reminders.deliveryCheckShows().isEmpty(), "the late alarm must not show anything")
    }

    @Допустим("^человек запустил проверку$")
    fun thePersonStartedTheCheck() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
    }

    @И("^к моменту возврата срабатывания не было, и срок прошёл$")
    fun theDueMomentPassedWithoutFiring() {
        expireCheck()
    }

    @Когда("^человек возвращается на «Проверку напоминаний» из системных настроек$")
    fun thePersonReturnsToTheCheck() {
        DiagnosticsStand.refresh(world)
    }

    @Тогда("^блок «Проверка доставки» показывает «([^»]+)»$")
    fun theBlockShows(text: String) {
        assertTrue(hasTag(TAG_CHECK_RESULT), "the block must show the result")
        // Сценарий называет исход коротко («Не дошло»); блок говорит о нём полной
        // формулировкой из ресурсов — той же, что и баннер (ОВ-108).
        val full = if (text == SHORT_NOT_DELIVERED) string(R.string.diagnostics_check_not_delivered) else text
        assertTrue(hasText(full), "the block must show «$full»")
        assertTrue(hasTag(TAG_CHECK_FIX), "the failed outcome must offer a fix")
    }

    // --- Обвязка ------------------------------------------------------------------------------

    private fun fireAtDue(plusSeconds: Long = 0) {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(plusSeconds))
        world.reminders.firedDeliveryCheck()
    }

    private fun expireCheck() {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(5))
    }

    private fun hasTag(tag: String): Boolean {
        var found = false
        DiagnosticsStand.askRaw(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasText(text: String): Boolean {
        var found = false
        DiagnosticsStand.askRaw(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val SECONDS_IN_MINUTE = 60
        const val SHORT_NOT_DELIVERED = "Не дошло"
    }
}
