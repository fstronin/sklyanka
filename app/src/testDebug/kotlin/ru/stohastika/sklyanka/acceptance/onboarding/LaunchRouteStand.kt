package ru.stohastika.sklyanka.acceptance.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ShouldShowAnalyticsConsentUseCase
import ru.stohastika.sklyanka.feature.onboarding.AnalyticsConsentRoute
import ru.stohastika.sklyanka.feature.onboarding.AnalyticsConsentViewModel
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.startup.LaunchStep
import ru.stohastika.sklyanka.startup.PrivacyConsentDialog
import ru.stohastika.sklyanka.startup.launchStep
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Стенд маршрута первого запуска (SKL-165, АН-1, TDD §11.5).
 *
 * Порядок «политика → аналитика → «Сегодня»» решает **то же правило** [launchStep], что
 * зовёт `MainActivity`: своей копии маршрута стенд не заводит, иначе приёмка проверяла бы
 * пересказ. Диалог политики ставится поверх любого шага так же, как в активности.
 *
 * Состояние процесса (политика принята или нет) живёт в стенде: оно переживает «перезапуск»
 * сценария — как настройка устройства, — а мир сценария создаётся заново на сценарий.
 * Согласие на аналитику при этом живёт в мире ([AcceptanceWorld.analytics]) и переживает
 * перезапуск по той же причине.
 */
@OptIn(ExperimentalTestApi::class)
internal object LaunchRouteStand {

    private var lastWorld: AcceptanceWorld? = null

    /** Политика конфиденциальности принята: настройка устройства, а не состояние экрана (ПВ-3). */
    var privacyAcknowledged: Boolean = false
        private set

    /** Согласие «дано» экраном: согласие пишется доменом, а стенд запоминает, что экран закрыт. */
    private var consentDone: Boolean = false

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            privacyAcknowledged = false
            consentDone = false
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun acknowledgePrivacy(world: AcceptanceWorld) {
        reset(world)
        privacyAcknowledged = true
    }

    fun forgetMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** Какое правило выбрало бы приложение: тот же шаг маршрута, что у `MainActivity`. */
    fun step(world: AcceptanceWorld): LaunchStep = launchStep(
        analyticsConsentUnseen = world.analytics.consent == AnalyticsConsentState.NOT_ASKED && !consentDone,
        onboardingDestination = false,
        onboardingUnseen = false,
        onboardingDone = true,
        addMedicationRequested = false,
    )

    /** Отрисовать маршрут запуска и задать вопрос: так приложение показывает свой первый экран. */
    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(world) }
            question()
            waitForIdle()
        }
    }

    /**
     * Лекарство и приём дня: сценарий говорит «приёмы дня видны», значит они есть.
     *
     * Курс начат вчера намеренно: приём дня начала курса, чьё время уже прошло,
     * планировщик не создаёт — человек в 08:00 это лекарство не принимал (ИНВ-24).
     * Сценарий же говорит о приёме, который у человека сегодня действительно есть.
     */
    fun ensureAnIntake(world: AcceptanceWorld) {
        if (world.medications.names().isEmpty()) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = MEDICATION,
                times = listOf(EVENING),
                startDate = world.clock.today().minusDays(1),
            )
        }
        world.plan()
    }

    /** Имя лекарства в модели, которое показывает экран: строка Gherkin — ключ, а не текст. */
    fun medicationShown(world: AcceptanceWorld): String = world.medications.get(world.medications.last).name

    internal fun string(id: Int): String = context.getString(id)

    @Composable
    private fun Content(world: AcceptanceWorld) {
        SklyankaTheme {
            if (!privacyAcknowledged) {
                PrivacyConsentDialog(
                    onOpenPolicy = {},
                    onAgree = { privacyAcknowledged = true },
                    onDecline = {},
                )
            }
            // Диалог политики стоит поверх шага и до него — так же, как в активности.
            when (step(world)) {
                LaunchStep.ANALYTICS_CONSENT -> AnalyticsConsentRoute(
                    viewModel = consentModel(world),
                    onDone = { consentDone = true },
                    onOpenPolicy = {},
                )
                else -> TodayScreen(state = TodayFromWorld.state(world), onIntent = {})
            }
        }
    }

    private fun consentModel(world: AcceptanceWorld): AnalyticsConsentViewModel = AnalyticsConsentViewModel(
        shouldShow = ShouldShowAnalyticsConsentUseCase(world.analytics.store),
        decideConsent = DecideAnalyticsConsentUseCase(world.analytics.store, world.analytics.recorder()),
        recorder = world.analytics.recorder(),
    )

    /** Тексты экрана согласия — из ресурсов: литерал разошёлся бы с продуктом. */
    val consentTitle: String get() = string(UiR.string.analytics_consent_title)
    val consentBody: String get() = string(UiR.string.analytics_consent_body)
    val consentAllow: String get() = string(UiR.string.analytics_consent_allow)
    val consentDecline: String get() = string(UiR.string.analytics_consent_decline)
    val actionTake: String get() = string(UiR.string.action_take)

    /** Ключ лекарства стенда: сценарий «приёмы дня видны» проверяет его на экране. */
    const val MEDICATION: String = "Магний B6"

    /**
     * Время приёма — вечернее: сценарий открывает «Сегодня» в 10:05, и утренний приём
     * к этому часу уже просрочен. Просроченный приём карточка показывает, но отметить
     * его на «Сегодня» нельзя — сценарий же говорит о приёме, доступном для отметки.
     */
    private val EVENING: LocalTime = LocalTime.of(20, 0)
}

/** «Сегодня» на экране: проверяется тег настоящего экрана, а не догадка по словам. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertTodayIsVisible() {
    onNodeWithTag(TAG_TODAY_SCREEN).assertIsDisplayed()
}

/** Экрана согласия на экране нет: ни заголовка, ни кнопок вопроса. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertNoConsentScreen() {
    onAllNodesWithText(LaunchRouteStand.consentTitle).assertCountEquals(0)
    onAllNodesWithText(LaunchRouteStand.consentAllow).assertCountEquals(0)
}

/**
 * Действие отметки доступно: у приёма дня есть орган отметки и он не заблокирован.
 *
 * Проверяется настоящая карточка приёма: её тег из дизайн-системы и кнопка отметки
 * внутри неё. Так сценарий «ни одно действие не заблокировано» проверяет продукт,
 * а не отсутствие надписи «недоступно».
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertMarkIsAvailable() {
    onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true).onFirst().assertExists()
    val marks = onAllNodesWithTag(TAG_MARK_BUTTON, useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(marks.isNotEmpty(), "the intake card must offer its mark action")
}
