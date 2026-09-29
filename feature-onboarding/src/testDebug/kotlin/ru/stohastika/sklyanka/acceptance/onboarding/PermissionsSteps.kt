package ru.stohastika.sklyanka.acceptance.onboarding

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.R
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_AUTOSTART
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_ACTION_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_AUTOSTART_UNVERIFIABLE
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_AUTOSTART
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.File
import java.time.Instant

/**
 * Экранные шаги приёмки экрана разрешений (SKL-14, группа `@screen`).
 *
 * Шаги живут в `feature-onboarding`, потому что говорят об экране разрешений
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий `OnboardingViewModel` на мире
 * сценария ([PermissionsStand]): цепочка шагов, сохранённые факты и пробное напоминание —
 * те же доменные сценарии и порты, что у продукта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class PermissionsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Открытие экрана -----------------------------------------------------------------------

    @Допустим("^человек находится на экране разрешений$")
    fun thePersonIsOnThePermissions() {
        openFirstLaunch()
    }

    @Когда("^человек открывает экран разрешений (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensThePermissions(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openFirstLaunch()
    }

    // --- Карточки разрешений -------------------------------------------------------------------

    @Тогда("^карточка «Уведомления» отмечена «Есть»$")
    fun theNotificationsCardIsGranted() {
        assertCardGranted(TAG_CARD_NOTIFICATIONS)
    }

    @И("^на карточке «Уведомления» нет кнопки действия$")
    fun theNotificationsCardHasNoAction() {
        assertTrue(noTag(TAG_ACTION_NOTIFICATIONS), "a granted card must carry no action button (критерий 2)")
    }

    @Тогда("^карточка «Точные будильники» выделена$")
    fun theExactAlarmsCardIsHighlighted() {
        val denied = PermissionsStand.state(world)
        val checklist = requireNotNull(denied.checklist) { "the screen must have read the system" }
        val neutral = denied.copy(checklist = checklist.copy(exactAlarms = PermissionGrant.Granted(Instant.EPOCH)))
        assertTrue(
            accentPixels(denied) > accentPixels(neutral),
            "a missing permission must be highlighted with the accent border (критерий 3)",
        )
    }

    @И("^на ней подпись «Иначе Android сдвинет приём на удобное себе время — до получаса»$")
    fun theExactAlarmsCaptionIsShown() {
        assertTrue(
            showsText(string(R.string.onboarding_step_exact_alarms_caption)),
            "the card caption is from the mockup",
        )
    }

    @И("^кнопка «Разрешить»$")
    fun theAllowButtonIsShown() {
        assertTrue(
            hasNode(
                hasTestTag(TAG_ACTION_EXACT_ALARMS) and
                    hasAnyDescendant(hasText(string(R.string.onboarding_action_allow))),
            ),
            "the missing permission must offer «Разрешить» (критерий 3)",
        )
    }

    @Когда("^он нажимает «Разрешить» и выдаёт разрешение в системном запросе$")
    fun thePersonGrantsInTheSystemDialog() {
        pressExactAlarms()
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        PermissionsStand.reportPermissionResult(world, PermissionStep.EXACT_ALARMS, granted = true)
    }

    @Тогда("^карточка «Точные будильники» отмечена «Есть»$")
    fun theExactAlarmsCardIsGranted() {
        assertCardGranted(TAG_CARD_EXACT_ALARMS)
    }

    @И("^кнопка «Разрешить» с неё убирается$")
    fun theAllowButtonIsGone() {
        assertTrue(noTag(TAG_ACTION_EXACT_ALARMS), "a granted card must lose the action button (критерий 4)")
    }

    @Но("^человек остаётся на том же экране$")
    fun thePersonStaysOnTheSameScreen() {
        assertScreenIsOpen()
    }

    @Когда("^человек нажимает «Разрешить» и отказывает в системном запросе$")
    fun thePersonRefusesInTheSystemDialog() {
        pressExactAlarms()
        PermissionsStand.reportPermissionResult(world, PermissionStep.EXACT_ALARMS, granted = false)
    }

    @Тогда("^карточка «Точные будильники» остаётся невыданной$")
    fun theExactAlarmsCardStaysDenied() {
        assertTrue(!cardShowsGranted(TAG_CARD_EXACT_ALARMS), "a refused permission must stay missing")
        assertTrue(hasTag(TAG_ACTION_EXACT_ALARMS), "a refused permission must offer the action again")
    }

    @И("^человек остаётся на экране разрешений$")
    fun thePersonStaysOnThePermissions() {
        assertScreenIsOpen()
    }

    @И("^может пройти остальные шаги$")
    fun theOtherStepsAreStillPassable() {
        // Отказ на одном шаге не запирает экран: у остальных остаются их действия (НАП-18).
        assertTrue(hasTag(TAG_ACTION_NOTIFICATIONS), "the notifications step must still be passable")
        assertTrue(hasTag(TAG_ACTION_AUTOSTART), "the autostart step must still be passable")
    }

    // --- Автозапуск EMUI -----------------------------------------------------------------------

    @Тогда("^карточка «Автозапуск без ограничений» показана невыполненной$")
    fun theAutostartCardIsUnresolved() {
        assertAutostartUnresolved()
    }

    @И("^на ней подпись «Настройки → Батарея → Запуск приложений → Склянка → вручную»$")
    fun theAutostartCaptionIsShown() {
        assertTrue(showsText(string(R.string.onboarding_step_autostart_caption)), "the manual path is from the mockup")
    }

    @И("^кнопка «Открыть настройки»$")
    fun theOpenSettingsButtonIsShown() {
        assertTrue(
            hasNode(
                hasTestTag(TAG_ACTION_AUTOSTART) and
                    hasAnyDescendant(hasText(string(R.string.onboarding_action_open_settings))),
            ),
            "the autostart step must offer «Открыть настройки» (критерий 3)",
        )
    }

    @Тогда("^карточка «Автозапуск без ограничений» всё равно показана невыполненной$")
    fun theAutostartCardIsStillUnresolved() {
        assertAutostartUnresolved()
    }

    @И("^приложение прямо говорит, что проверить эту настройку оно не может$")
    fun theAppExplainsTheOnlyProof() {
        assertTrue(
            showsText(string(R.string.onboarding_autostart_unverifiable)),
            "the third step must say the app cannot check it (НАП-20)",
        )
    }

    // --- Границы цепочки -----------------------------------------------------------------------

    @Когда("^человек проходит все три шага (.+?) с (\\d{2}:\\d{2}) до (\\d{2}:\\d{2})$")
    @Suppress("LongParameterList")
    fun thePersonGoesThroughAllThreeSteps(date: String, from: String, to: String) {
        world.clock.moveTo(world.localMoment(date, from))
        openFirstLaunch()
        passStep(TAG_ACTION_NOTIFICATIONS, ReminderPermission.NOTIFICATIONS, PermissionStep.NOTIFICATIONS)
        passStep(TAG_ACTION_EXACT_ALARMS, ReminderPermission.EXACT_ALARMS, PermissionStep.EXACT_ALARMS)
        PermissionsStand.act(world) { onNodeWithTag(TAG_ACTION_AUTOSTART).performScrollTo().performClick() }
        PermissionsStand.model(world).onAutostartConfirmed(true)
        world.clock.moveTo(world.localMoment(date, to))
    }

    @Тогда("^доступ к политике «Не беспокоить» у него не запрашивается ни разу$")
    fun theDndAccessIsNeverRequested() {
        val state = PermissionsStand.state(world)
        assertTrue(state.checklist?.allResolved == true, "the scenario passes all three steps")
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
            listOf(RecordingNavigator.EXACT_ALARMS, RecordingNavigator.AUTOSTART),
            PermissionsStand.navigator.opened,
            "the flow opens only the screens of its own steps",
        )
        assertEquals(
            PermissionState.GRANTED,
            world.system.state(ReminderPermission.DND_POLICY_ACCESS),
            "the world state of the DND access must stay untouched",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun openFirstLaunch() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)
        assertTrue(hasTag(TAG_PERMISSIONS_SCREEN), "the permissions screen must be on the stand")
    }

    /** Нажать действие — значит уже стоять на экране: сценарий открытие может и не писать. */
    private fun ensureOpen() {
        if (PermissionsStand.state(world).checklist == null) openFirstLaunch()
    }

    private fun pressExactAlarms() {
        ensureOpen()
        PermissionsStand.act(world) { onNodeWithTag(TAG_ACTION_EXACT_ALARMS).performScrollTo().performClick() }
    }

    private fun passStep(actionTag: String, permission: ReminderPermission, step: PermissionStep) {
        PermissionsStand.act(world) { onNodeWithTag(actionTag).performScrollTo().performClick() }
        world.system.grant(permission)
        PermissionsStand.reportPermissionResult(world, step, granted = true)
    }

    private fun assertCardGranted(cardTag: String) {
        assertTrue(
            hasNode(hasTestTag(cardTag) and hasAnyDescendant(hasText(string(R.string.onboarding_state_granted)))),
            "the card must be marked «Есть» (критерий 2)",
        )
    }

    private fun cardShowsGranted(cardTag: String): Boolean = hasNode(
        hasTestTag(cardTag) and hasAnyDescendant(hasText(string(R.string.onboarding_state_granted))),
    )

    private fun assertAutostartUnresolved() {
        assertFalse(cardShowsGranted(TAG_CARD_AUTOSTART), "the autostart step never gets a granted mark (НАП-20)")
        assertTrue(hasTag(TAG_AUTOSTART_UNVERIFIABLE), "the step must say it is unverifiable")
        val state = PermissionsStand.state(world)
        assertFalse(
            state.checklist?.autostart?.confirmedByUser == true,
            "the scenario has not confirmed the autostart",
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
                            onAutostartConfirmed = {},
                            onNext = {},
                            onSkip = {},
                        ),
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

    private companion object {
        const val CAPTURE_WIDTH = 390
        const val CAPTURE_HEIGHT = 2400
        const val DND_MARKER = "DND"
    }
}
