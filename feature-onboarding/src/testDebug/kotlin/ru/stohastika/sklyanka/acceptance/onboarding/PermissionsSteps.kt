package ru.stohastika.sklyanka.acceptance.onboarding

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.ManualStepState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepAction
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorSteps
import ru.stohastika.sklyanka.domain.onboarding.actionFor
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.onboarding.OnboardingProfileTexts
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.R
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_BATTERY_SAVING
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_RECENTS_PIN
import ru.stohastika.sklyanka.feature.onboarding.TAG_ALL_CONFIGURED
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_BATTERY_SAVING
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_RECENTS_PIN
import ru.stohastika.sklyanka.feature.onboarding.TAG_CONFIRMED_MARK
import ru.stohastika.sklyanka.feature.onboarding.TAG_CONFIRM_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_CONFIRM_BATTERY_SAVING
import ru.stohastika.sklyanka.feature.onboarding.TAG_CONFIRM_RECENTS_PIN
import ru.stohastika.sklyanka.feature.onboarding.TAG_DONE
import ru.stohastika.sklyanka.feature.onboarding.TAG_GRANTED_MARK
import ru.stohastika.sklyanka.feature.onboarding.TAG_HINT
import ru.stohastika.sklyanka.feature.onboarding.TAG_INSTRUCTIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_ISSUE
import ru.stohastika.sklyanka.feature.onboarding.TAG_NEXT
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.onboarding.TAG_UNVERIFIABLE
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.File
import java.time.Instant

/**
 * Экранные шаги приёмки экрана «Настроить надёжность» (SKL-14, группа `@screen`).
 *
 * Шаги живут в `feature-onboarding`, потому что говорят об экране
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий `OnboardingViewModel` на мире
 * сценария ([PermissionsStand]): цепочка шагов и сохранённые факты — те же доменные
 * сценарии и порты, что у продукта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class PermissionsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Карточка, о которой шла речь в последней проверке.
     *
     * Нужна шагам, чей текст не называет карточку («кнопка «Открыть настройки»»): сценарий
     * говорит о ней предыдущей строкой, и пересказ названия в шаге был бы вторым источником
     * правды о том, какую карточку проверяют.
     */
    private var currentCard: String? = null

    /**
     * Вендорский шаг, о котором шла речь в последней проверке (SKL-112).
     *
     * Нужен шагам, чей текст называет шаг словами прошивки («инструкция называет пункты
     * меню этой прошивки», «общего списка… не показывается»): сценарий называет шаг
     * предыдущей строкой, и пересказ здесь был бы вторым источником правды.
     */
    private var currentStep: PermissionStep? = null

    // --- Открытие экрана -----------------------------------------------------------------------

    @Допустим("^человек находится на экране «Настроить надёжность»$")
    fun thePersonIsOnTheSetup() {
        openFirstLaunch()
    }

    @Допустим("^человек находится на экране «Настроить надёжность» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonIsOnTheSetupAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openFirstLaunch()
    }

    @Когда("^человек открывает экран «Настроить надёжность» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheSetup(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openFirstLaunch()
    }

    @Когда("^1 октября 2026 в 11:08 человек открывает экран «Настроить надёжность»$")
    fun thePersonOpensTheSetupOnOctoberFirst() {
        world.clock.moveTo(world.localMoment("1 октября 2026", "11:08"))
        openFirstLaunch()
    }

    // --- Карточки разрешений -------------------------------------------------------------------

    @Тогда("^карточка «Уведомления» отмечена «Есть»$")
    fun theNotificationsCardIsGranted() {
        assertCardGranted(TAG_CARD_NOTIFICATIONS)
    }

    @И("^на карточке «Уведомления» нет кнопки действия$")
    fun theNotificationsCardHasNoAction() {
        assertTrue(noTag(TAG_ACTION_NOTIFICATIONS), "a granted card must carry no action button (критерий 3)")
    }

    @Тогда("^карточка «Точные будильники» выделена$")
    fun theExactAlarmsCardIsHighlighted() {
        currentCard = TAG_CARD_EXACT_ALARMS
        assertCardIsHighlighted(PermissionStep.EXACT_ALARMS)
    }

    @И("^на ней подпись «Иначе Android сдвинет приём на удобное себе время — до получаса»$")
    fun theExactAlarmsCaptionIsShown() {
        assertTrue(
            showsText(string(R.string.setup_step_exact_alarms_caption)),
            "the card caption is from the mockup",
        )
    }

    @И("^кнопка «Разрешить»$")
    fun theAllowButtonIsShown() {
        assertTrue(
            hasNode(
                hasTestTag(TAG_ACTION_EXACT_ALARMS) and
                    hasAnyDescendant(hasText(string(R.string.setup_action_allow))),
            ),
            "the missing permission must offer «Разрешить» (критерий 3)",
        )
    }

    @Когда("^он нажимает «Разрешить» и выдаёт право на системном экране особого доступа$")
    fun thePersonGrantsOnTheSpecialAccessScreen() {
        // Приложение только приводит на экран особого доступа (ПЛ-14); карточка меняется
        // по возвращении — перечитыванием ответа системы, а не ответом диалога.
        pressExactAlarms()
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        PermissionsStand.model(world).refresh()
    }

    @Тогда("^карточка «Точные будильники» отмечена «Есть»$")
    fun theExactAlarmsCardIsGranted() {
        assertCardGranted(TAG_CARD_EXACT_ALARMS)
    }

    @И("^кнопка «Разрешить» с неё убирается$")
    fun theAllowButtonIsGone() {
        assertTrue(noTag(TAG_ACTION_EXACT_ALARMS), "a granted card must lose the action button (критерий 3)")
    }

    @Но("^человек остаётся на том же экране$")
    fun thePersonStaysOnTheSameScreen() {
        assertScreenIsOpen()
    }

    @Когда("^человек нажимает «Разрешить» и не выдаёт право на системном экране$")
    fun thePersonDoesNotGrantOnTheSpecialAccessScreen() {
        pressExactAlarms()
        PermissionsStand.model(world).refresh()
    }

    @Тогда("^карточка «Точные будильники» остаётся невыданной$")
    fun theExactAlarmsCardStaysDenied() {
        assertTrue(!cardShowsGranted(TAG_CARD_EXACT_ALARMS), "a refused permission must stay missing")
        assertTrue(hasTag(TAG_ACTION_EXACT_ALARMS), "a refused permission must offer the action again")
    }

    @И("^человек остаётся на экране «Настроить надёжность»$")
    fun thePersonStaysOnTheSetup() {
        assertScreenIsOpen()
    }

    @И("^может пройти остальные шаги$")
    fun theOtherStepsAreStillPassable() {
        // Отказ на одном шаге не запирает экран: у остальных остаются их действия (НАП-18).
        assertTrue(hasTag(TAG_ACTION_NOTIFICATIONS), "the notifications step must still be passable")
        assertTrue(hasTag(TAG_ACTION_APP_STARTUP), "the app startup step must still be passable")
        assertTrue(hasTag(TAG_ACTION_BATTERY_SAVING), "the battery saving step must still be passable")
    }

    // --- Вендорские шаги -----------------------------------------------------------------------

    @Тогда("^карточка «Запуск приложений» показана невыполненной$")
    fun theAppStartupCardIsUnresolved() {
        currentCard = TAG_CARD_APP_STARTUP
        assertVendorUnresolved(PermissionStep.APP_STARTUP, TAG_CARD_APP_STARTUP)
    }

    @Тогда("^карточка «Запуск приложений» всё равно показана невыполненной$")
    fun theAppStartupCardIsStillUnresolved() {
        currentCard = TAG_CARD_APP_STARTUP
        assertVendorUnresolved(PermissionStep.APP_STARTUP, TAG_CARD_APP_STARTUP)
    }

    @Тогда("^карточка «Энергосбережение» показана невыполненной$")
    fun theBatterySavingCardIsUnresolved() {
        currentCard = TAG_CARD_BATTERY_SAVING
        assertVendorUnresolved(PermissionStep.BATTERY_SAVING, TAG_CARD_BATTERY_SAVING)
    }

    @Тогда("^карточка «Энергосбережение» всё равно показана невыполненной$")
    fun theBatterySavingCardIsStillUnresolved() {
        theBatterySavingCardIsUnresolved()
    }

    @Тогда("^карточка «Энергосбережение» снова показана неподтверждённой$")
    fun theBatterySavingCardIsUnconfirmedAgain() {
        theBatterySavingCardIsUnresolved()
    }

    @Тогда("^карточка «Закрепление в недавних» показана невыполненной$")
    fun theRecentsPinCardIsUnresolved() {
        currentCard = TAG_CARD_RECENTS_PIN
        assertVendorUnresolved(PermissionStep.RECENTS_PIN, TAG_CARD_RECENTS_PIN)
    }

    @И("^на ней подпись-путь «Настройки → Батарея → Запуск приложений → Склянка → Управлять вручную»$")
    fun theAppStartupPathIsShown() {
        assertTrue(showsText(string(R.string.setup_step_app_startup_caption)), "the manual path is from the spec")
    }

    @И("^пояснение «Включите «Автозапуск», «Косвенный запуск» и «Работу в фоне»»$")
    fun theAppStartupActionIsShown() {
        assertTrue(showsText(string(R.string.setup_step_app_startup_action)), "the actions to enable are from the spec")
    }

    @И("^кнопка «Открыть настройки»$")
    fun theOpenSettingsButtonIsShown() {
        val card = requireNotNull(currentCard) { "the scenario must name the card first" }
        val actionTag = actionTagOf(card)
        assertTrue(
            hasNode(
                hasTestTag(actionTag) and
                    hasAnyDescendant(hasText(string(R.string.setup_action_open_settings))),
            ),
            "the vendor step must offer «Открыть настройки» (критерий 6)",
        )
    }

    @И("^на ней кнопка «Открыть настройки»$")
    fun theCardOffersOpenSettings() {
        theOpenSettingsButtonIsShown()
    }

    @И("^кнопки «Открыть настройки» на ней нет$")
    fun theCardHasNoOpenSettings() {
        val card = requireNotNull(currentCard) { "the scenario must name the card first" }
        assertTrue(noTag(actionTagOf(card)), "the recents pin has no system screen to open (ОВ-106)")
    }

    @И(
        "^на ней подпись-инструкция «Откройте список недавних приложений, потяните карточку «Склянки» вниз и нажмите появившийся замок»$",
    )
    fun theRecentsPinInstructionIsShown() {
        assertTrue(
            showsText(string(R.string.setup_step_recents_pin_caption)),
            "the recents pin explains the gesture from the spec",
        )
    }

    @И("^переключатель «([^»]+)»$")
    fun theSwitchIsShown(label: String) {
        val card = requireNotNull(currentCard) { "the scenario must name the card first" }
        assertTrue(
            hasNode(
                hasTestTag(confirmTagOf(card)) and hasAnyDescendant(hasText(label)),
            ),
            "the vendor step must carry the confirmation switch «$label» (НАП-20)",
        )
    }

    @Тогда("^косвенный признак на ней назван косвенным и не подменяет подтверждение$")
    fun theHintIsIndirectAndDoesNotReplaceTheConfirmation() {
        assertTrue(showsText(string(R.string.setup_hint_battery)), "the hint must be shown word for word (НАП-20)")
        assertTrue(
            string(R.string.setup_hint_battery).startsWith("Косвенный признак"),
            "the word «косвенный» is the point of the hint",
        )
        assertFalse(
            cardShowsGranted(TAG_CARD_BATTERY_SAVING),
            "a hint never turns the step green (НАП-20)",
        )
    }

    @И("^приложение прямо говорит, что проверить эти настройки оно не может$")
    fun theAppExplainsItCannotCheck() {
        assertTrue(
            showsText(string(R.string.setup_unverifiable_app_startup)),
            "the vendor step must say the app cannot check it (НАП-20)",
        )
        assertTrue(
            showsText(string(R.string.setup_unverifiable_battery_saving)),
            "the battery step must say the app cannot check it (НАП-20)",
        )
    }

    // --- Подтверждение вендорских шагов --------------------------------------------------------

    @Когда("^человек подтверждает выполнение шага «Запуск приложений» переключателем$")
    fun thePersonConfirmsAppStartup() {
        ensureOpen()
        toggleSwitch(TAG_CONFIRM_APP_STARTUP)
    }

    @Допустим("^человек подтвердил выполнение шага «Энергосбережение»$")
    fun thePersonConfirmedBatterySaving() {
        ensureOpen()
        PermissionsStand.model(world).onManualConfirmed(PermissionStep.BATTERY_SAVING, true)
    }

    /**
     * Переключатель вендорского шага по его подписи.
     *
     * Подпись называет сценарий, а не тег: слова переключателя берутся у профиля
     * (`OnboardingProfileTexts.confirmLabel`), и «Я настроил автозапуск» на Xiaomi —
     * тот же шаг, что «Я настроил запуск приложений» на EMUI.
     */
    @Когда("^он включает переключатель «([^»]+)»$")
    fun thePersonTurnsOnTheSwitch(label: String) {
        val (_, step) = vendorStepBy(label) { candidate, profile ->
            string(OnboardingProfileTexts.confirmLabel(candidate, profile))
        }
        currentStep = step
        ensureOpen()
        val tag = confirmTagOf(cardTagOf(step))
        assertTrue(
            hasNode(hasTestTag(tag) and hasAnyDescendant(hasText(label))),
            "the vendor step must carry the confirmation switch «$label» (НАП-20)",
        )
        toggleSwitch(tag)
    }

    @Когда("^он выключает тот же переключатель$")
    fun thePersonTurnsOffTheSameSwitch() {
        toggleSwitch(TAG_CONFIRM_APP_STARTUP)
    }

    @Когда("^он снимает переключатель «Я снял ограничения энергосбережения»$")
    fun thePersonTurnsOffBatterySavingSwitch() {
        ensureOpen()
        toggleSwitch(TAG_CONFIRM_BATTERY_SAVING)
    }

    @Тогда("^карточка «Запуск приложений» получает обычную рамку$")
    fun theAppStartupCardGetsTheNeutralBorder() {
        currentCard = TAG_CARD_APP_STARTUP
        // Обычная рамка — состояние «шаг пройден»; кнопка пути в системный раздел остаётся
        // (НАП-20), а отметка сменяется на слово человека (критерий 4).
        assertTrue(
            PermissionsStand.state(world).checklist?.appStartup?.isResolved == true,
            "the confirmed step must be counted as resolved (НАП-20)",
        )
        assertTrue(
            hasNode(
                hasTestTag(TAG_CARD_APP_STARTUP) and
                    hasAnyDescendant(hasTestTag(TAG_CONFIRMED_MARK)),
            ),
            "the confirmed step must be marked «Подтверждено вами»",
        )
    }

    @И("^на ней отметка «Подтверждено вами», а не «Есть»$")
    fun theAppStartupCardIsConfirmedByUser() {
        assertTrue(
            hasNode(
                hasTestTag(TAG_CARD_APP_STARTUP) and
                    hasAnyDescendant(hasText(string(R.string.setup_confirmed_by_user))),
            ),
            "the vendor step is marked «Подтверждено вами» (НАП-20)",
        )
        assertFalse(
            cardShowsGranted(TAG_CARD_APP_STARTUP),
            "«Есть» stays only for what the system read (НАП-20)",
        )
    }

    @Тогда("^карточка «([^»]+)» отмечена «Подтверждено вами»$")
    fun theVendorCardIsMarkedConfirmed(title: String) {
        val (_, step) = vendorStepBy(title) { candidate, profile ->
            string(OnboardingProfileTexts.title(candidate, profile))
        }
        assertTrue(
            hasNode(
                hasTestTag(cardTagOf(step)) and
                    hasAnyDescendant(hasTestTag(TAG_CONFIRMED_MARK)),
            ),
            "the card «$title» must be marked «Подтверждено вами» (НАП-20)",
        )
    }

    @Тогда("^карточка «Запуск приложений» снова показана невыполненной$")
    fun theAppStartupIsUnresolvedAgainAfterSwitch() {
        assertVendorUnresolved(PermissionStep.APP_STARTUP, TAG_CARD_APP_STARTUP)
    }

    @И("^приложение не утверждает, что этот шаг настроен$")
    fun theAppDoesNotClaimTheStepIsConfigured() {
        assertFalse(
            cardShowsGranted(TAG_CARD_BATTERY_SAVING),
            "the app must not claim a withdrawn vendor step is configured (НАП-20)",
        )
        assertFalse(
            hasNode(
                hasTestTag(TAG_CARD_BATTERY_SAVING) and
                    hasAnyDescendant(hasText(string(R.string.setup_confirmed_by_user))),
            ),
            "the confirmation must be gone from the card",
        )
    }

    // --- Границы цепочки -----------------------------------------------------------------------

    @Когда("^человек проходит все пять шагов (.+?) с (\\d{2}:\\d{2}) до (\\d{2}:\\d{2})$")
    @Suppress("LongParameterList")
    fun thePersonGoesThroughAllFiveSteps(date: String, from: String, to: String) {
        world.clock.moveTo(world.localMoment(date, from))
        openFirstLaunch()
        passPermissionStep(TAG_ACTION_NOTIFICATIONS, ReminderPermission.NOTIFICATIONS, PermissionStep.NOTIFICATIONS)
        passPermissionStep(TAG_ACTION_EXACT_ALARMS, ReminderPermission.EXACT_ALARMS, PermissionStep.EXACT_ALARMS)
        PermissionsStand.act(world) { onNodeWithTag(TAG_ACTION_APP_STARTUP).performScrollTo().performClick() }
        PermissionsStand.act(world) { onNodeWithTag(TAG_ACTION_BATTERY_SAVING).performScrollTo().performClick() }
        PermissionsStand.model(world).onManualConfirmed(PermissionStep.APP_STARTUP, true)
        PermissionsStand.model(world).onManualConfirmed(PermissionStep.BATTERY_SAVING, true)
        PermissionsStand.model(world).onManualConfirmed(PermissionStep.RECENTS_PIN, true)
        world.clock.moveTo(world.localMoment(date, to))
    }

    @Тогда("^доступ к политике «Не беспокоить» у него не запрашивается ни разу$")
    fun theDndAccessIsNeverRequested() {
        val state = PermissionsStand.state(world)
        assertTrue(state.checklist?.allResolved == true, "the scenario passes all five steps")
        // «Не беспокоить» не входит в цепочку (НАП-17): её нет ни среди шагов домена,
        // ни среди запрошенных диалогов, ни среди системных переходов, и состояние
        // доступа мир не трогал вовсе.
        assertTrue(
            PermissionStep.entries.none { step -> step.name.contains(DND_MARKER) },
            "the onboarding chain must not contain a DND step (НАП-17)",
        )
        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS),
            PermissionsStand.requested,
            "the flow asks the system dialog only for notifications, never for the DND access",
        )
        assertEquals(
            listOf(RecordingNavigator.EXACT_ALARMS, RecordingNavigator.APP_STARTUP, RecordingNavigator.BATTERY_SAVING),
            PermissionsStand.navigator.opened,
            "the flow opens only the screens of its own steps",
        )
        assertEquals(
            PermissionState.GRANTED,
            world.system.state(ReminderPermission.DND_POLICY_ACCESS),
            "the world state of the DND access must stay untouched",
        )
    }

    // --- SKL-84: один экран на все входы и закрытая цепочка ------------------------------------

    @Когда("^1 октября 2026 в 11:08 (?:он|человек) открывает «Пройти настройку заново» на «Проверке напоминаний»$")
    fun heOpensTheSetupFromTheCheckScreen() {
        world.clock.moveTo(world.localMoment("1 октября 2026", "11:08"))
        // «Пройти настройку заново» ведёт в PermissionsActivity с источником SETTINGS (ОВ-48).
        PermissionsStand.open(world, OnboardingSource.SETTINGS)
    }

    @Тогда("^открывается экран с заголовком «([^»]+)»$")
    fun theSetupScreenWithTitleOpens(title: String) {
        assertTrue(hasTag(TAG_PERMISSIONS_SCREEN), "the setup screen must be on the stand")
        assertTrue(showsText(title), "the screen must carry the title «$title»")
    }

    @И("^пояснение «([^»]+)»$")
    fun theIntroIsShown(intro: String) {
        assertTrue(showsText(intro), "the screen must carry the intro word for word")
    }

    @И("^пять карточек в порядке: «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)»$")
    @Suppress("LongParameterList")
    fun fiveCardsInOrder(first: String, second: String, third: String, fourth: String, fifth: String) {
        val cards = listOf(
            first to TAG_CARD_NOTIFICATIONS,
            second to TAG_CARD_EXACT_ALARMS,
            third to TAG_CARD_APP_STARTUP,
            fourth to TAG_CARD_BATTERY_SAVING,
            fifth to TAG_CARD_RECENTS_PIN,
        )
        cards.forEach { (title, tag) ->
            assertTrue(hasTag(tag), "the card «$title» must be on the screen")
        }
        // Порядок — по положению карточек на экране, а не по порядку объявления в разметке.
        val tops = cards.map { (_, tag) ->
            var top = 0f
            PermissionsStand.ask(world) {
                top = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            }
            top
        }
        assertEquals(tops.sorted(), tops, "the cards must follow the fixed order of ОВ-106")
    }

    @И("^внизу одна кнопка «([^»]+)»$")
    fun theOnlyFooterButtonIs(label: String) {
        assertTrue(
            hasNode(hasTestTag(TAG_DONE) and hasAnyDescendant(hasText(label))),
            "a re-entry carries the quiet «$label»",
        )
        assertTrue(noTag(TAG_NEXT), "the onboarding footer must be gone")
        assertTrue(noTag(TAG_SKIP), "the skip button belongs to the first launch only")
    }

    @Тогда("^на экране «Настроить надёжность» есть только карточки «([^»]+)» и «([^»]+)»$")
    fun onlyTheCommonCardsAreShown(first: String, second: String) {
        assertTrue(hasTag(TAG_CARD_NOTIFICATIONS), "the notifications card must be there")
        assertTrue(hasTag(TAG_CARD_EXACT_ALARMS), "the exact alarms card must be there")
        assertEquals("Уведомления", first)
        assertEquals("Точные будильники", second)
    }

    @И("^карточек «([^»]+)», «([^»]+)» и «([^»]+)» на нём нет$")
    fun theVendorCardsAreAbsent(third: String, fourth: String, fifth: String) {
        val absent = listOf(
            third to TAG_CARD_APP_STARTUP,
            fourth to TAG_CARD_BATTERY_SAVING,
            fifth to TAG_CARD_RECENTS_PIN,
        )
        absent.forEach { (title, tag) ->
            assertTrue(noTag(tag), "the vendor card «$title» must not be shown on a non-Huawei phone")
        }
    }

    @И("^плашек «Проверить это приложение не может» и переключателей вендорских шагов на нём нет$")
    fun theVendorPlaquesAndSwitchesAreAbsent() {
        assertTrue(noTag(TAG_UNVERIFIABLE), "no vendor plaque may be shown")
        assertTrue(noTag(TAG_CONFIRM_APP_STARTUP), "no vendor switch may be shown")
        assertTrue(noTag(TAG_CONFIRM_BATTERY_SAVING), "no vendor switch may be shown")
        assertTrue(noTag(TAG_CONFIRM_RECENTS_PIN), "no vendor switch may be shown")
    }

    @Тогда("^карточка «Уведомления» показывает строку-причину «([^»]+)»$")
    fun theNotificationsCardShowsTheIssue(issue: String) {
        currentCard = TAG_CARD_NOTIFICATIONS
        assertTrue(hasTag(TAG_ISSUE), "the card must carry the issue line")
        assertTrue(showsText(issue), "the issue must be named «$issue» — the words of the banner and diagnostics")
    }

    @И("^предлагает действие «([^»]+)»$")
    fun theCardOffersTheAction(label: String) {
        val card = requireNotNull(currentCard) { "the scenario must name the card first" }
        assertTrue(
            hasNode(hasTestTag(actionTagOf(card)) and hasAnyDescendant(hasText(label))),
            "the card must offer «$label»",
        )
    }

    @И("^строки-причины и кнопки действия на ней нет$")
    fun theNotificationsCardHasNoIssueAndNoAction() {
        assertTrue(noTag(TAG_ISSUE), "a granted card must carry no issue line")
        assertTrue(noTag(TAG_ACTION_NOTIFICATIONS), "a granted card must carry no action")
    }

    @Тогда("^карточки «Запуск приложений», «Энергосбережение» и «Закрепление в недавних» показаны невыполненными$")
    fun theVendorCardsAreUnresolved() {
        val steps = listOf(
            PermissionStep.APP_STARTUP to TAG_CARD_APP_STARTUP,
            PermissionStep.BATTERY_SAVING to TAG_CARD_BATTERY_SAVING,
            PermissionStep.RECENTS_PIN to TAG_CARD_RECENTS_PIN,
        )
        steps.forEach { (step, tag) ->
            assertFalse(cardShowsGranted(tag), "a vendor step never gets a granted mark (НАП-20)")
            assertFalse(
                PermissionsStand.state(world).checklist?.state(step)?.isResolved == true,
                "the vendor step $step must stay unresolved",
            )
        }
        assertTrue(hasTag(TAG_UNVERIFIABLE), "the vendor steps must say they are unverifiable")
    }

    @И("^на «Энергосбережении» виден косвенный признак «([^»]+)»$")
    fun theBatteryHintIsShown(hint: String) {
        currentCard = TAG_CARD_BATTERY_SAVING
        assertTrue(showsText(hint), "the indirect hint must be shown word for word")
    }

    @Но("^выполненным шаг всё равно не отмечен$")
    fun theVendorStepStaysUnresolved() {
        assertFalse(
            PermissionsStand.state(world).checklist?.batterySaving?.isResolved == true,
            "a hint never resolves a vendor step (НАП-20)",
        )
        assertFalse(cardShowsGranted(TAG_CARD_BATTERY_SAVING), "«Есть» stays only for what the system read")
    }

    @Тогда("^над списком стоит плашка «([^»]+)»$")
    fun theConfiguredPlaqueIsShown(plaque: String) {
        assertTrue(hasTag(TAG_ALL_CONFIGURED), "the neutral plaque must be above the list")
        assertTrue(showsText(plaque), "the plaque must read «$plaque»")
    }

    @И("^у карточек «Уведомления» и «Точные будильники» отметка «Есть»$")
    fun bothPermissionCardsAreGranted() {
        assertCardGranted(TAG_CARD_NOTIFICATIONS)
        assertCardGranted(TAG_CARD_EXACT_ALARMS)
    }

    @И("^у вендорских карточек отметка «Подтверждено вами»$")
    fun theVendorCardsAreConfirmedByUser() {
        val cards = listOf(TAG_CARD_APP_STARTUP, TAG_CARD_BATTERY_SAVING, TAG_CARD_RECENTS_PIN)
        cards.forEach { tag ->
            assertTrue(
                hasNode(hasTestTag(tag) and hasAnyDescendant(hasTestTag(TAG_CONFIRMED_MARK))),
                "the vendor card must carry the person's confirmation",
            )
        }
    }

    @И("^ни на одной карточке нет ни кнопок, ни переключателей$")
    fun noControlsAnywhere() {
        val controls = listOf(
            TAG_ACTION_NOTIFICATIONS,
            TAG_ACTION_EXACT_ALARMS,
            TAG_ACTION_APP_STARTUP,
            TAG_ACTION_BATTERY_SAVING,
            TAG_CONFIRM_APP_STARTUP,
            TAG_CONFIRM_BATTERY_SAVING,
            TAG_CONFIRM_RECENTS_PIN,
        )
        controls.forEach { assertTrue(noTag(it), "the control $it must be gone in the configured state") }
    }

    // --- SKL-112: профиль прошивки, её слова и пересмотр разрешений ----------------------------

    @Тогда("^карточки идут в порядке: (.+)$")
    fun theCardsFollowTheProfileOrder(cards: String) {
        val checklist = requireNotNull(PermissionsStand.state(world).checklist) { "the screen must be open" }
        val profile = checklist.vendor
        val expected = cardNamesOf(cards)
        // Состав и порядок — из домена: сколько шагов у профиля и в каком порядке, решает
        // `VendorSteps`, а экран рисует то, что дал домен.
        val shown = checklist.steps.map { (step, _) -> string(OnboardingProfileTexts.title(step, profile)) }
        assertEquals(expected, shown, "the profile's cards must follow the fixed order of НАП-19")
        // И на экране они стоят тем же порядком: слова профиля совпадают, а положение —
        // единственный способ проверить порядок, не пересказывая разметку.
        val tops = checklist.steps.map { (step, _) -> topOf(cardTagOf(step)) }
        assertEquals(tops.sorted(), tops, "the cards must stand in the fixed order of НАП-19")
    }

    @Тогда("^вендорские карточки профиля показаны невыполненными$")
    fun theVendorCardsOfTheProfileAreUnresolved() {
        val steps = vendorStepsOfThePhone()
        assertTrue(steps.isNotEmpty(), "the scenario's profile must carry vendor steps")
        steps.forEach { step ->
            val card = cardTagOf(step)
            assertTrue(hasTag(card), "the vendor card of $step must be on the screen")
            assertVendorUnresolved(step, card)
        }
    }

    @Но("^косвенный признак снятой оптимизации батареи ни одну из них не закрывает$")
    fun theBatteryHintClosesNoVendorStep() {
        assertTrue(hasTag(TAG_HINT), "the battery saving step must carry its indirect hint (НАП-20)")
        assertTrue(showsText(string(R.string.setup_hint_battery)), "the hint is shown word for word (НАП-20)")
        val checklist = requireNotNull(PermissionsStand.state(world).checklist) { "the screen must be open" }
        vendorStepsOfThePhone().forEach { step ->
            assertFalse(checklist.state(step).isResolved, "a hint never closes a vendor step (НАП-20)")
        }
    }

    @Тогда("^у вендорского шага «([^»]+)» есть кнопка «Открыть настройки» и запасная инструкция$")
    fun theVendorStepHasTheButtonAndTheFallback(title: String) {
        val (profile, step) = vendorStepBy(title) { candidate, candidateProfile ->
            string(OnboardingProfileTexts.title(candidate, candidateProfile))
        }
        currentStep = step
        // Запасная инструкция показывается тем же путём, что при неудаче открытия: раздел
        // профиля закрылся — приложение называет путь словами этой прошивки (НФТ-8).
        // Экран открывается до подмены исхода: стенд сбрасывает переходы при входе в мир.
        ensureOpen()
        PermissionsStand.navigator.failFor(step)
        pressStepAction(step)
        assertTrue(
            hasNode(
                hasTestTag(actionTagOf(cardTagOf(step))) and
                    hasAnyDescendant(hasText(string(R.string.setup_action_open_settings))),
            ),
            "the vendor step must keep offering «Открыть настройки» (НАП-20)",
        )
        assertTrue(
            showsText(fallbackTextOf(step, profile)),
            "the vendor step must show the fallback instruction of its own firmware",
        )
    }

    @И("^инструкция называет пункты меню этой прошивки, а не EMUI$")
    fun theInstructionNamesThisFirmware() {
        val step = requireNotNull(currentStep) { "the scenario must name the step first" }
        val profile = world.onboarding.vendor
        val text = fallbackTextOf(step, profile)
        assertTrue(showsText(text), "the instruction of this firmware must be shown")
        // Чужая прошивка не рассказывается: текст отличается от инструкции каждого другого
        // профиля — сравнение идёт теми же ресурсами, без литералов.
        VendorProfile.entries.filter { it != profile }.forEach { other ->
            OnboardingProfileTexts.fallback(step, other)?.let { otherRes ->
                assertFalse(
                    string(otherRes) == text,
                    "the instruction of $other must not be shown as this firmware's (§профиль)",
                )
            }
        }
        OnboardingProfileTexts.caption(step, VendorProfile.EMUI)?.let { emuiPath ->
            assertFalse(string(emuiPath) == text, "the general EMUI path must not stand in for this firmware's one")
        }
    }

    @Допустим("^ни один системный раздел этой прошивки для шага «([^»]+)» не открывается$")
    fun noVendorSectionOpens(stepTitle: String) {
        val (_, step) = vendorStepBy(stepTitle) { candidate, candidateProfile ->
            string(OnboardingProfileTexts.title(candidate, candidateProfile))
        }
        currentStep = step
        // Экран открывается до подмены исхода: стенд сбрасывает записанные переходы
        // при входе в новый мир сценария, и подменённый исход иначе был бы стёрт.
        ensureOpen()
        PermissionsStand.navigator.failFor(step)
    }

    @Когда("^на шаге «([^»]+)» человек нажимает «Открыть настройки»$")
    fun thePersonPressesOpenOnTheStep(stepTitle: String) {
        val (profile, step) = vendorStepBy(stepTitle) { candidate, candidateProfile ->
            string(OnboardingProfileTexts.title(candidate, candidateProfile))
        }
        currentStep = step
        ensureOpen()
        pressStepAction(step)
        assertEquals(
            profile to step,
            PermissionsStand.navigator.vendorCalls.lastOrNull(),
            "the navigator must receive this phone's profile and this step, not a generic screen",
        )
    }

    @Тогда("^он видит запасную инструкцию этой прошивки$")
    fun theFallbackInstructionOfThisFirmwareIsShown() {
        val step = requireNotNull(currentStep) { "the scenario must name the step first" }
        assertTrue(hasTag(TAG_INSTRUCTIONS), "the fallback instruction must be on the screen")
        assertTrue(
            showsText(fallbackTextOf(step, world.onboarding.vendor)),
            "the shown instruction must be the words of this firmware",
        )
    }

    @И("^после возврата в приложение инструкция остаётся видимой$")
    fun theInstructionStaysAfterReturning() {
        // Возврат в приложение — это перечитывание состояния системы (`PermissionsActivity.onResume`).
        val step = requireNotNull(currentStep) { "the scenario must name the step first" }
        PermissionsStand.model(world).refresh()
        assertTrue(hasTag(TAG_INSTRUCTIONS), "the instruction must survive the return (критерий 6)")
        assertTrue(showsText(fallbackTextOf(step, world.onboarding.vendor)), "the same words must stay on the screen")
    }

    @И("^переключатель «([^»]+)» стоит ниже неё$")
    fun theSwitchStandsBelowTheInstruction(label: String) {
        val instructionTop = topOf(TAG_INSTRUCTIONS)
        val switchTop = topOfText(label)
        assertTrue(switchTop > instructionTop, "the confirmation switch «$label» must stand below the instruction")
    }

    @Но("^общего списка энергосбережения Android вместо раздела прошивки не показывается$")
    fun noGenericAndroidBatteryListIsShown() {
        val step = requireNotNull(currentStep) { "the scenario must name the step first" }
        val profile = world.onboarding.vendor
        // Приложение привело в раздел профиля (или ничего не открыло), а не в общий экран
        // Android: открытого «успеха» нет ни в навигаторе, ни на экране — человеку названа
        // инструкция его прошивки (НАП-19, «стандартный экран Android не выдаётся за вендорский»).
        val openedNoGeneric = requireNotNull(PermissionsStand.navigator.vendorCalls.lastOrNull())
        assertEquals(profile to step, openedNoGeneric, "no generic screen is opened")
        assertEquals(
            listOf(openConstantOf(step)),
            PermissionsStand.navigator.opened,
            "the app must not open a generic Android screen in place of the firmware's section",
        )
        assertTrue(hasTag(TAG_INSTRUCTIONS), "the firmware's own instruction stays on the screen")
        assertTrue(noTag(TAG_ALL_CONFIGURED), "a failed open must not look like a passed step")
    }

    @Тогда("^у шага «Закрепление в недавних» есть инструкция и переключатель$")
    fun theRecentsPinStepCarriesInstructionAndSwitch() {
        val profile = world.onboarding.vendor
        currentStep = PermissionStep.RECENTS_PIN
        ensureOpen()
        assertTrue(
            showsText(string(R.string.setup_step_recents_pin_caption)),
            "the recents pin must explain the gesture (НАП-20)",
        )
        val label = string(OnboardingProfileTexts.confirmLabel(PermissionStep.RECENTS_PIN, profile))
        assertTrue(
            hasNode(hasTestTag(TAG_CONFIRM_RECENTS_PIN) and hasAnyDescendant(hasText(label))),
            "the recents pin must carry its confirmation switch",
        )
    }

    @Но("^кнопки «Открыть настройки» у него нет$")
    fun theRecentsPinHasNoOpenButton() {
        assertTrue(noTag(TAG_ACTION_RECENTS_PIN), "the recents pin has no system screen to open (НАП-20)")
    }

    @Допустим("^система отвечает, что право на точные будильники (выдано|не выдано)$")
    fun theSystemAnswersAboutExactAlarms(state: String) {
        if (state == "выдано") {
            world.system.grant(ReminderPermission.EXACT_ALARMS)
        } else {
            world.system.deny(ReminderPermission.EXACT_ALARMS)
        }
    }

    @Тогда("^карточка «Точные будильники» без отметки$")
    fun theExactAlarmsCardIsUnmarked() {
        assertFalse(cardShowsGranted(TAG_CARD_EXACT_ALARMS), "a denied permission carries no «Есть» (НАП-16)")
        assertTrue(
            noTagOnCard(TAG_CARD_EXACT_ALARMS, TAG_GRANTED_MARK),
            "a denied permission carries neither glyph nor word",
        )
        assertTrue(
            PermissionsStand.state(world).checklist?.exactAlarms is PermissionGrant.Denied,
            "the step follows the system's answer, not the manifest (§право)",
        )
    }

    @И("^действие на ней (действия нет|«Разрешить» ведёт на экран особого доступа)$")
    fun theExactAlarmsActionIs(action: String) {
        val state = requireNotNull(PermissionsStand.state(world).checklist?.exactAlarms) { "the screen must be open" }
        if (action == "действия нет") {
            assertEquals(null, actionFor(PermissionStep.EXACT_ALARMS, state), "a granted step offers no action")
            assertTrue(noTag(TAG_ACTION_EXACT_ALARMS), "a granted step carries no button")
        } else {
            assertEquals(
                StepAction.OPEN_SYSTEM_SETTINGS,
                actionFor(PermissionStep.EXACT_ALARMS, state),
                "the exact alarm right is granted on the special access screen (ПЛ-14)",
            )
            assertTrue(
                hasNode(
                    hasTestTag(TAG_ACTION_EXACT_ALARMS) and
                        hasAnyDescendant(hasText(string(R.string.setup_action_allow))),
                ),
                "the missing right must offer «Разрешить»",
            )
        }
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Разрешить» на шаге «Точные будильники»$")
    fun thePersonPressesAllowOnTheExactAlarmsStep(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        ensureOpen()
        pressExactAlarms()
    }

    @Тогда("^открывается системный экран особого доступа «([^»]+)»$")
    fun theSpecialAccessScreenOpens(label: String) {
        assertEquals(
            listOf(RecordingNavigator.EXACT_ALARMS),
            PermissionsStand.navigator.opened,
            "the action must lead to the «$label» special access screen and nothing else",
        )
    }

    @И("^собственного диалога разрешения приложение не показывает$")
    fun theAppShowsNoOwnPermissionDialog() {
        assertTrue(
            PermissionsStand.requested.isEmpty(),
            "the app must not ask the exact alarm right with a dialog on any Android version (ПЛ-14)",
        )
    }

    @И("^пока право не выдано, карточка остаётся без отметки$")
    fun theCardStaysUnmarkedWhileNotGranted() {
        assertFalse(cardShowsGranted(TAG_CARD_EXACT_ALARMS), "a refused right must stay missing")
        assertTrue(hasTag(TAG_ACTION_EXACT_ALARMS), "the step must offer the action again")
    }

    @Когда("^он выдаёт право на системном экране и возвращается в приложение$")
    fun thePersonGrantsOnTheSystemScreenAndReturns() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        // Возврат — чтение системы заново, а не перезапуск экрана (НФТ-52).
        PermissionsStand.model(world).refresh()
    }

    @Тогда("^карточка «Точные будильники» отмечена «Есть» без перезапуска приложения$")
    fun theExactAlarmsCardIsGrantedWithoutRestart() {
        assertCardGranted(TAG_CARD_EXACT_ALARMS)
    }

    @И("^право перечитано у системы, а не взято из сохранённого состояния$")
    fun theRightIsRereadFromTheSystem() {
        val granted = PermissionsStand.state(world).checklist?.exactAlarms
        assertTrue(granted is PermissionGrant.Granted, "the state must be a fresh read of the system (НФТ-52)")
        assertEquals(
            world.clock.now(),
            (granted as PermissionGrant.Granted).readAt,
            "the read moment must be the current moment of the world, not a stored value",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun openFirstLaunch() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)
        assertTrue(hasTag(TAG_PERMISSIONS_SCREEN), "the setup screen must be on the stand")
    }

    /** Нажать действие — значит уже стоять на экране: сценарий открытие может и не писать. */
    private fun ensureOpen() {
        if (PermissionsStand.state(world).checklist == null) openFirstLaunch()
    }

    private fun pressExactAlarms() {
        ensureOpen()
        PermissionsStand.act(world) { onNodeWithTag(TAG_ACTION_EXACT_ALARMS).performScrollTo().performClick() }
    }

    private fun toggleSwitch(tag: String) {
        PermissionsStand.act(world) { onNodeWithTag(tag).performScrollTo().performClick() }
    }

    private fun passPermissionStep(actionTag: String, permission: ReminderPermission, step: PermissionStep) {
        PermissionsStand.act(world) { onNodeWithTag(actionTag).performScrollTo().performClick() }
        world.system.grant(permission)
        PermissionsStand.reportPermissionResult(world, step, granted = true)
    }

    private fun assertCardGranted(cardTag: String) {
        assertTrue(
            hasNode(hasTestTag(cardTag) and hasAnyDescendant(hasText(string(R.string.onboarding_state_granted)))),
            "the card must be marked «Есть» (критерий 3)",
        )
    }

    private fun cardShowsGranted(cardTag: String): Boolean = hasNode(
        hasTestTag(cardTag) and hasAnyDescendant(hasText(string(R.string.onboarding_state_granted))),
    )

    private fun assertVendorUnresolved(step: PermissionStep, cardTag: String) {
        assertFalse(cardShowsGranted(cardTag), "a vendor step never gets a granted mark (НАП-20)")
        assertFalse(
            hasNode(hasTestTag(cardTag) and hasAnyDescendant(hasText(string(R.string.setup_confirmed_by_user)))),
            "the scenario has not confirmed this vendor step",
        )
        assertTrue(hasTag(TAG_UNVERIFIABLE), "the step must say it is unverifiable")
        val state = PermissionsStand.state(world)
        assertFalse(state.checklist?.state(step)?.isResolved == true, "the vendor step must stay unresolved")
    }

    /** Карточка с акцентной рамкой: сравниваются два состояния одного экрана (ОВ-106). */
    private fun assertCardIsHighlighted(step: PermissionStep) {
        val shown = PermissionsStand.state(world)
        val resolved = shown.copy(checklist = shown.checklist?.resolved(step))
        assertTrue(
            accentPixels(shown) > accentPixels(resolved),
            "a missing step must be highlighted with the accent border (критерий 3)",
        )
    }

    private fun assertScreenIsOpen() {
        assertTrue(hasTag(TAG_PERMISSIONS_SCREEN), "the screen must stay open")
        assertEquals(null, PermissionsStand.exit(world), "the person must not have left the screen")
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun hasTag(tag: String): Boolean {
        var found = false
        PermissionsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun noTag(tag: String): Boolean = !hasTag(tag)

    /** Названия карточек из строки документа: в кавычках-ёлочках, в порядке появления. */
    private fun cardNamesOf(text: String): List<String> =
        Regex("«([^»]+)»").findAll(text).map { it.groupValues[1] }.toList()

    /** Вендорские шаги прошивки сценария: профиль — данность мира, а не экрана. */
    private fun vendorStepsOfThePhone(): List<PermissionStep> = VendorSteps.of(world.onboarding.vendor)

    /**
     * Найти вендорский шаг прошивки сценария по его словам — названию карточки или подписи.
     *
     * Слова шага задаёт профиль, а не литерал шага: так один и тот же шаг находится и как
     * «Автозапуск» на Xiaomi, и как «Запуск приложений» на EMUI (НАП-19).
     */
    private fun vendorStepBy(
        name: String,
        textOf: (PermissionStep, VendorProfile) -> String,
    ): Pair<VendorProfile, PermissionStep> {
        val profile = world.onboarding.vendor
        val step = VendorSteps.of(profile).firstOrNull { candidate -> textOf(candidate, profile) == name }
        return profile to requireNotNull(step) { "the profile $profile has no vendor step «$name»" }
    }

    /** Инструкция профиля по шагу — тем же ресурсом, что рисует экран. */
    private fun fallbackTextOf(step: PermissionStep, profile: VendorProfile): String =
        string(requireNotNull(OnboardingProfileTexts.fallback(step, profile)) { "the step $step has no fallback" })

    private fun pressStepAction(step: PermissionStep) {
        PermissionsStand.act(world) {
            onNodeWithTag(actionTagOf(cardTagOf(step))).performScrollTo().performClick()
        }
    }

    private fun cardTagOf(step: PermissionStep): String = when (step) {
        PermissionStep.NOTIFICATIONS -> TAG_CARD_NOTIFICATIONS
        PermissionStep.EXACT_ALARMS -> TAG_CARD_EXACT_ALARMS
        PermissionStep.APP_STARTUP -> TAG_CARD_APP_STARTUP
        PermissionStep.BATTERY_SAVING -> TAG_CARD_BATTERY_SAVING
        PermissionStep.RECENTS_PIN -> TAG_CARD_RECENTS_PIN
    }

    /** Постоянная системного экрана шага в записи навигатора (SKL-112). */
    private fun openConstantOf(step: PermissionStep): String = when (step) {
        PermissionStep.APP_STARTUP -> RecordingNavigator.APP_STARTUP
        PermissionStep.BATTERY_SAVING -> RecordingNavigator.BATTERY_SAVING
        else -> error("$step has no vendor screen to open")
    }

    private fun topOf(tag: String): Float {
        var top = 0f
        PermissionsStand.ask(world) {
            top = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }
        return top
    }

    private fun topOfText(text: String): Float {
        var top = 0f
        PermissionsStand.ask(world) {
            top = onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .first()
                .boundsInRoot
                .top
        }
        return top
    }

    private fun noTagOnCard(cardTag: String, markTag: String): Boolean =
        !hasNode(hasTestTag(cardTag) and hasAnyDescendant(hasTestTag(markTag)))

    private fun showsText(text: String): Boolean {
        var found = false
        PermissionsStand.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasNode(matcher: SemanticsMatcher): Boolean {
        var found = false
        PermissionsStand.ask(world) {
            found = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    /**
     * Акцентные пиксели экрана: в семантике цвет не живёт (приём SKL-20 и SKL-18).
     *
     * Сравниваются два состояния одного экрана: у невыданного шага акцентной рамки больше.
     */
    private fun accentPixels(state: OnboardingUiState): Int {
        val target = File.createTempFile("onboarding-accent", ".png")
        try {
            captureRoboImage(
                file = target,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
                roborazziComposeOptions = RoborazziComposeOptions {
                    size(widthDp = CAPTURE_WIDTH, heightDp = CAPTURE_HEIGHT)
                },
            ) {
                SklyankaTheme {
                    PermissionsScreen(
                        state = state,
                        actions = PermissionsActions(
                            onStepAction = {},
                            onManualConfirmed = { _, _ -> },
                            onNext = {},
                            onSkip = {},
                            onDone = {},
                        ),
                        toLocal = { instant -> instant.atZone(world.clock.time.zone()).toLocalDateTime() },
                    )
                }
            }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "the screen was not rendered" }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val accent = lightColors().accent.toArgb()
            return pixels.count { it == accent }
        } finally {
            target.delete()
        }
    }

    /** Копия чеклиста с закрытым шагом — второе состояние для сравнения рамки. */
    private fun PermissionChecklist.resolved(step: PermissionStep): PermissionChecklist = when (step) {
        PermissionStep.NOTIFICATIONS -> copy(
            notifications = notifications.copy(permission = PermissionGrant.Granted(Instant.EPOCH)),
        )
        PermissionStep.EXACT_ALARMS -> copy(exactAlarms = PermissionGrant.Granted(Instant.EPOCH))
        PermissionStep.APP_STARTUP -> copy(appStartup = ManualStepState(true))
        PermissionStep.BATTERY_SAVING -> copy(batterySaving = ManualStepState(true))
        PermissionStep.RECENTS_PIN -> copy(recentsPin = ManualStepState(true))
    }

    private fun actionTagOf(cardTag: String): String = when (cardTag) {
        TAG_CARD_NOTIFICATIONS -> TAG_ACTION_NOTIFICATIONS
        TAG_CARD_EXACT_ALARMS -> TAG_ACTION_EXACT_ALARMS
        TAG_CARD_APP_STARTUP -> TAG_ACTION_APP_STARTUP
        TAG_CARD_BATTERY_SAVING -> TAG_ACTION_BATTERY_SAVING
        else -> TAG_ACTION_RECENTS_PIN
    }

    private fun confirmTagOf(cardTag: String): String = when (cardTag) {
        TAG_CARD_APP_STARTUP -> TAG_CONFIRM_APP_STARTUP
        TAG_CARD_BATTERY_SAVING -> TAG_CONFIRM_BATTERY_SAVING
        else -> TAG_CONFIRM_RECENTS_PIN
    }

    private companion object {
        const val CAPTURE_WIDTH = 390
        const val CAPTURE_HEIGHT = 2400
        const val DND_MARKER = "DND"
    }
}
