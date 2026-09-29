package ru.stohastika.sklyanka.acceptance.today

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.onboarding.OnboardingFlow
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.feature.onboarding.R as OnboardingR

/**
 * Экранные шаги композиции предупреждения о ненадёжности напоминаний (SKL-29, группа
 * `@composition`).
 *
 * Прогон идёт в `app`, потому что сценарию нужны «Сегодня» и экран разрешений сразу:
 * они живут в разных модулях, и одновременно существуют только здесь
 * (`SKL-26/TDD.md §4.3`). «Сегодня» собирается из мира сценария тем же путём, что
 * в прогоне SKL-08 ([TodayFromWorld]); нажатие «Настроить» отдаёт намерение, а приёмник —
 * тот же экран разрешений с источником `TODAY_WARNING`, что включает `MainActivity`.
 *
 * Ровно то, ради чего задача существует: отозванное разрешение видно на «Сегодня»
 * без перезапуска, и из предупреждения человек возвращается к разрешениям (НФТ-52, НАП-21).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class TodayReliabilityCompositionSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Пропуск онбординга и возврат к разрешениям (сценарий 4) ----------------

    @Когда("^он открывает экран «Сегодня»$")
    fun heOpensToday() {
        // Продукт при открытии проходит по просроченным приёмам (НАП-34) и строит повестку
        // дня; состояние разрешений читает собранный экран при каждом показе (НФТ-52).
        world.sweep(SweepReason.APP_START)
        TodayFromWorld.state(world)
    }

    @Тогда("^он видит предупреждение, что напоминания сейчас не приходят$")
    fun heSeesTheWarning() {
        assertTodayWarning(R.string.today_reliability_notifications_off)
    }

    @И("^из предупреждения можно вернуться на экран разрешений$")
    fun theWarningLeadsBackToPermissions() {
        // Нажатие идёт по собранному экрану: отдаётся то же намерение, что разбирает
        // маршрут продукта (`TodayIntent.OpenPermissions`).
        val intents = collectTodayIntents { test ->
            test.onNodeWithText(context.getString(R.string.today_reliability_action)).performClick()
        }
        assertTrue(
            intents.any { it is TodayIntent.OpenPermissions },
            "«Настроить» must ask for the permissions screen: $intents",
        )
        // Приёмник — экран разрешений с источником TODAY_WARNING: индикатора шагов
        // на нём нет, как при входе из настроек (НАП-21, критерий приёмки 4).
        OnboardingAppProbe.open(world, OnboardingSource.TODAY_WARNING)
        OnboardingAppProbe.askPermissions(world) {
            assertTrue(
                tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_PERMISSIONS_SCREEN),
                "the permissions screen must open",
            )
            val steps = context.getString(
                OnboardingR.string.cd_onboarding_steps,
                OnboardingFlow.PERMISSIONS_STEP,
                OnboardingFlow.TOTAL_STEPS,
            )
            onAllNodesWithContentDescription(steps, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Отзыв разрешения позже (сценарий 5) ------------------------------------

    @Когда("^он возвращается в приложение (.+?) в (\\d{2}:\\d{2})$")
    fun heReturnsToTheApp(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.sweep(SweepReason.APP_START)
        // Состояние разрешений перечитывается из системы при каждом возвращении на передний
        // план (НФТ-52): и карточка разрешений, и предупреждение говорят правду о сейчас,
        // а не о том, каким был мир, когда онбординг проходили.
        OnboardingAppProbe.model(world).refresh()
        TodayFromWorld.state(world)
    }

    @Тогда("^карточка «Уведомления» показана невыданной$")
    fun theNotificationsCardIsShownDenied() {
        OnboardingAppProbe.open(world, OnboardingSource.TODAY_WARNING)
        OnboardingAppProbe.askPermissions(world) {
            onNodeWithTag(TAG_CARD_NOTIFICATIONS, useUnmergedTree = true).assertIsDisplayed()
            onAllNodes(
                hasTestTag(TAG_CARD_NOTIFICATIONS) and
                    hasAnyDescendant(hasText(context.getString(OnboardingR.string.onboarding_state_granted))),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        }
    }

    @И("^на экране «Сегодня» видно предупреждение, что напоминания сейчас не приходят$")
    fun theWarningIsVisibleOnToday() {
        assertTodayWarning(R.string.today_reliability_notifications_off)
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Собрать «Сегодня» из мира и ответить на вопрос его композиции. */
    private fun collectTodayIntents(action: (ComposeUiTest) -> Unit): List<TodayIntent> {
        val intents = mutableListOf<TodayIntent>()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    TodayScreen(state = TodayFromWorld.state(world), onIntent = intents::add)
                }
            }
            action(this)
            waitForIdle()
        }
        return intents
    }

    private fun assertTodayWarning(text: Int) {
        OnboardingAppProbe.askToday(world) { test, _ ->
            val banner = test.onNodeWithTag(TAG_RELIABILITY_BANNER, useUnmergedTree = true)
            val texts = textsOf(banner.fetchSemanticsNode())
            assertTrue(
                context.getString(text) in texts,
                "the Today warning must read «${context.getString(text)}»: $texts",
            )
        }
    }

    private fun tagsIn(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.TestTag).orEmpty().let {
            if (it.isEmpty()) emptyList() else listOf(it)
        } +
            node.children.flatMap(::tagsIn)

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::textsOf)
}
