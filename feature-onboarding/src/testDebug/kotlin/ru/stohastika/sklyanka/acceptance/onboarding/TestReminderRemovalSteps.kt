package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.onboarding.TAG_AUTOSTART_CONFIRM
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_AUTOSTART
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS

/**
 * Экранные шаги приёмки удаления пробного напоминания (SKL-32, группа `@onboarding`).
 *
 * Шаги живут в `feature-onboarding`, потому что говорят об экране разрешений. Состояние
 * собирает тот же стенд [PermissionsStand], что у SKL-14: второй копии экрана в приёмке нет.
 * Проверки отсутствия называют удалённые строки и теги дословно: ресурсов у них больше нет,
 * и сценарий проверяет контракт, а не реализацию.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TestReminderRemovalSteps(private val world: AcceptanceWorld) {

    @Когда("^человек подтверждает, что настроил автозапуск$")
    fun thePersonConfirmsTheAutostart() {
        PermissionsStand.act(world) {
            onNodeWithTag(TAG_AUTOSTART_CONFIRM).performScrollTo().performClick()
        }
    }

    @Тогда("^карточка «Автозапуск без ограничений» считается выполненной$")
    fun theAutostartCardIsDone() {
        assertTrue(
            PermissionsStand.state(world).checklist?.autostart?.isResolved == true,
            "the manual confirmation must close the third step (НАП-20)",
        )
        PermissionsStand.ask(world) {
            onNodeWithTag(TAG_AUTOSTART_CONFIRM).assertIsOn()
        }
    }

    @И("^цепочка из трёх шагов считается пройденной$")
    fun theChainIsComplete() {
        assertTrue(
            PermissionsStand.state(world).checklist?.allResolved == true,
            "all three steps must be resolved",
        )
    }

    @Тогда("^на экране нет строки «Проверить можно сразу: пришлём тестовое напоминание через минуту.»$")
    fun thePromiseIsGone() {
        assertFalse(hasText(TEST_PROMISE), "the screen must not promise a test reminder (ОВ-50)")
        assertFalse(hasTag(TEST_HINT_TAG), "the plate of the removed promise must be gone")
    }

    @И("^нет элемента, которым запускается пробное напоминание$")
    fun theControlIsGone() {
        assertFalse(hasTag(TEST_REMINDER_TAG), "there must be no control starting a test reminder")
        assertFalse(hasText(TEST_REMINDER_LABEL), "the removed control must carry no label")
    }

    @Но("^три карточки шагов и подтверждение автозапуска вручную остаются на месте$")
    fun theStepsAndTheConfirmationStay() {
        PermissionsStand.ask(world) {
            onNodeWithTag(TAG_CARD_NOTIFICATIONS).assertIsDisplayed()
            onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
            onNodeWithTag(TAG_CARD_AUTOSTART).assertIsDisplayed()
            onNodeWithTag(TAG_AUTOSTART_CONFIRM).assertIsDisplayed()
        }
    }

    private fun hasTag(tag: String): Boolean {
        var found = false
        PermissionsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasText(text: String): Boolean {
        var found = false
        PermissionsStand.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private companion object {
        const val TEST_PROMISE = "Проверить можно сразу: пришлём тестовое напоминание через минуту."
        const val TEST_HINT_TAG = "permissions_test_hint"
        const val TEST_REMINDER_TAG = "permissions_test_reminder"
        const val TEST_REMINDER_LABEL = "Отправить пробное напоминание"
    }
}
