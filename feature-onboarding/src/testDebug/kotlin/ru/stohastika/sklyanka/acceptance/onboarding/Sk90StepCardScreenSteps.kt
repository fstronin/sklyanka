package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreenFixtures
import ru.stohastika.sklyanka.feature.onboarding.R
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_BATTERY_SAVING
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_RECENTS_PIN
import ru.stohastika.sklyanka.feature.onboarding.TAG_CONFIRMED_MARK
import ru.stohastika.sklyanka.feature.onboarding.TAG_GRANTED_MARK
import ru.stohastika.sklyanka.feature.onboarding.TAG_STEPS_LIST
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
class Sk90StepCardScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var scale: Float = 1f

    @Допустим("^экран «Настроить надёжность» собран на стенде SKL-90$")
    fun theSetupScreenIsOnTheStand() = Unit

    @И("^системный шрифт шага увеличен до (\\d+) %$")
    fun theStepFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^отметка «Подтверждено вами» стоит ниже заголовка шага$")
    fun theConfirmedMarkGoesUnderTheText() = probe(scale) {
        onNodeWithTag(TAG_STEPS_LIST).performScrollToNode(hasTestTag(TAG_CARD_RECENTS_PIN))
        val title = onNodeWithText(context.getString(R.string.setup_step_recents_pin), useUnmergedTree = true)
            .fetchSemanticsNode()
        val mark = onNode(markOf(TAG_CARD_RECENTS_PIN, TAG_CONFIRMED_MARK), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "the confirmed mark must stand under the text at ${scale}x",
            mark.boundsInRoot.top >= title.boundsInRoot.bottom - TOLERANCE,
        )
    }

    @Тогда("^отметка «Есть» шага «Уведомления» стоит справа от заголовка$")
    fun theGrantedMarkStaysInTheRow() = probe(1f) {
        val title = onNodeWithText(context.getString(R.string.setup_step_notifications), useUnmergedTree = true)
            .fetchSemanticsNode()
        val mark = onNode(markOf(TAG_CARD_NOTIFICATIONS, TAG_GRANTED_MARK), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "the mark must stay to the right of the text",
            mark.boundsInRoot.left >= title.boundsInRoot.right - TOLERANCE,
        )
    }

    @И("^ни одно слово заголовка шага не разорвано переносом$")
    fun noStepTitleIsBroken() = probe(scale) {
        STEP_TITLES.forEach { (card, titleId) ->
            onNodeWithTag(TAG_STEPS_LIST).performScrollToNode(hasTestTag(card))
            val title = onNodeWithText(context.getString(titleId), useUnmergedTree = true).fetchSemanticsNode()
            val lines = layoutLines(title)
            assertTrue(
                "the step title «${context.getString(titleId)}» is broken inside a word: $lines",
                !brokenInsideWord(lines),
            )
        }
    }

    private fun probe(scale: Float = 1f, question: ComposeUiTest.() -> Unit) {
        runComposeUiTest {
            setContent {
                Scaled(scale) {
                    SklyankaTheme {
                        PermissionsScreen(
                            state = PermissionsScreenFixtures.chainComplete(),
                            actions = PermissionsActions(
                                onStepAction = {},
                                onManualConfirmed = { _, _ -> },
                                onNext = {},
                                onSkip = {},
                                onDone = {},
                            ),
                            toLocal = { LocalDateTime.ofInstant(it, ZoneId.of("Europe/Moscow")) },
                        )
                    }
                }
            }
            question(this)
        }
    }

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private fun markOf(cardTag: String, markTag: String) = hasTestTag(markTag) and hasAnyAncestor(hasTestTag(cardTag))

    private fun brokenInsideWord(lines: List<String>): Boolean = lines.indices.drop(1).any { index ->
        val tail = lines[index - 1].lastOrNull()
        val head = lines[index].firstOrNull()
        tail != null && head != null && tail.isLetterOrDigit() && head.isLetterOrDigit()
    }

    private fun layoutLines(node: SemanticsNode): List<String> {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.firstOrNull() ?: return emptyList()
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        }
    }

    private companion object {
        const val PERCENT = 100f
        const val TOLERANCE = 1f

        val STEP_TITLES = listOf(
            TAG_CARD_NOTIFICATIONS to R.string.setup_step_notifications,
            TAG_CARD_EXACT_ALARMS to R.string.setup_step_exact_alarms,
            TAG_CARD_APP_STARTUP to R.string.setup_step_app_startup,
            TAG_CARD_BATTERY_SAVING to R.string.setup_step_battery_saving,
            TAG_CARD_RECENTS_PIN to R.string.setup_step_recents_pin,
        )
    }
}
