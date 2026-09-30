package ru.stohastika.sklyanka.acceptance.startup

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupFrames
import ru.stohastika.sklyanka.startup.TAG_SPLASH
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import kotlin.math.abs
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги кадров запуска (SKL-42, группа `@screen`).
 *
 * Каждый сценарий собирает настоящий [StartupFrames] с настоящим `TodayScreen` или
 * настоящим экраном разрешений за ним; кадр выбирает то же правило [startupFrame],
 * что зовёт корень приложения. Копии правила в стенде нет — иначе приёмка проверяла бы
 * пересказ, а не продукт.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class StartupFramesSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var destination: StartDestination? = null
    private var onboardingUnseen: Boolean = false
    private var today: TodayUiState? = null
    private var facts: FrameFacts? = null

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Устройство как данность.
     *
     * Стенд представляет его для всего, что видно без платформы: правило кадров,
     * композиция заставки и экранов. Системный кадр Android 12+ на JVM не существует —
     * он остаётся проверкой устройства (`@manual`).
     */
    @Допустим("^приложение установлено на устройстве Android 12 или новее$")
    fun theAppIsInstalledOnAndroid12OrNewer() = Unit

    @Допустим("^маршрут запуска решён: первый экран — «Сегодня»$")
    fun theRouteLeadsToToday() {
        destination = StartDestination.Today
    }

    @Допустим("^маршрут запуска решён: первый экран — экран разрешений$")
    fun theRouteLeadsToPermissions() {
        destination = StartDestination.Onboarding
    }

    @И("^«Сегодня» ещё не готово показать своё содержимое$")
    fun todayIsNotReady() {
        today = TodayUiState.Opening
    }

    @И("^«Сегодня» готово показать своё содержимое$")
    fun todayIsReady() {
        ensureOwner()
        today = TodayFromWorld.state(world)
    }

    @И("^человек этот экран ещё не видел$")
    fun thePersonHasNotSeenTheScreen() {
        onboardingUnseen = true
    }

    // --- Кадр ----------------------------------------------------------------------------------

    @Когда("^приложение отрисовывает свой кадр$")
    fun theAppDrawsItsFrame() {
        // Сценарию онбординга готовность «Сегодня» не названа и не нужна: правило
        // решает по маршруту раньше, чем смотрит на «Сегодня».
        val todayState = today ?: TodayUiState.Opening
        val frame = startupFrame(
            destination = destination,
            onboardingUnseen = onboardingUnseen,
            onboardingDone = false,
            today = todayState,
        )
        val permissionsFirst = destination == StartDestination.Onboarding && onboardingUnseen
        if (permissionsFirst) {
            // Экран разрешений показывает содержимое сразу, как и в корне приложения:
            // состояние читается настоящей моделью на мире сценария.
            OnboardingAppProbe.open(world, OnboardingSource.FIRST_LAUNCH)
        }
        var collected: FrameFacts? = null
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    StartupFrames(frame = frame) {
                        if (permissionsFirst) {
                            PermissionsFirstScreen()
                        } else {
                            TodayScreen(state = todayState, onIntent = {})
                        }
                    }
                }
            }
            collected = collectFacts()
            waitForIdle()
        }
        facts = requireNotNull(collected)
    }

    // --- Наблюдения ----------------------------------------------------------------------------

    @Тогда("^на экране заставка приложения$")
    fun theSplashIsOnScreen() {
        val observed = requireNotNull(facts)
        assertTrue(observed.splash, "the splash must be on screen")
        assertFalse(observed.todayScreen, "the first screen must not be shown under the splash")
    }

    @И("^на ней знак, название «Склянка», подпись «приём лекарств» и «Stohastika» у нижнего края$")
    fun theSplashCarriesTheFullComposition() {
        val observed = requireNotNull(facts)
        assertTrue(observed.mark, "the mark must be on the splash")
        assertTrue(observed.title, "the name must be on the splash")
        assertTrue(observed.tagline, "the tagline must be on the splash")
        assertTrue(observed.vendor, "the vendor must be on the splash")
        assertTrue(
            abs(observed.vendorGap - SklyankaSizes.SplashVendorBottom.value) <= VENDOR_TOLERANCE,
            "the vendor must stand ${SklyankaSizes.SplashVendorBottom} from the bottom, gap=${observed.vendorGap}",
        )
    }

    @Но("^пустого «Сегодня» на экране нет$")
    fun theEmptyTodayIsNotOnScreen() {
        assertFalse(
            requireNotNull(facts).todayScreen,
            "no «Сегодня» screen may be composed while the splash is held",
        )
    }

    @Тогда("^на экране «Сегодня»$")
    fun todayIsOnScreen() {
        val observed = requireNotNull(facts)
        assertTrue(observed.todayScreen, "«Сегодня» must be shown")
        assertFalse(observed.splash, "the splash must be gone")
    }

    @И("^заставки приложения на экране нет$")
    fun theSplashIsGone() {
        assertFalse(requireNotNull(facts).splash, "the splash must be gone")
    }

    @Тогда("^на экране экран разрешений$")
    fun thePermissionsScreenIsOnScreen() {
        val observed = requireNotNull(facts)
        assertTrue(observed.permissions, "the permissions screen must be shown")
        assertFalse(observed.splash, "the splash must be gone")
    }

    @Тогда("^на заставке приложения нет индикатора загрузки$")
    fun thereIsNoLoaderOnTheSplash() {
        assertEquals(
            0,
            requireNotNull(facts).loader,
            "no progress semantics may be on the held splash",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun ComposeUiTest.collectFacts(): FrameFacts {
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        val vendor = boundsOfText(context.getString(UiR.string.brand_vendor))
        return FrameFacts(
            splash = tagPresent(TAG_SPLASH),
            todayScreen = tagPresent(TAG_TODAY_SCREEN),
            permissions = tagPresent(TAG_PERMISSIONS_SCREEN),
            mark = onAllNodesWithContentDescription(context.getString(UiR.string.cd_app_mark))
                .fetchSemanticsNodes().isNotEmpty(),
            title = textPresent(context.getString(UiR.string.app_name)),
            tagline = textPresent(context.getString(UiR.string.brand_tagline).uppercase(InterfaceLocale.VALUE)),
            vendor = vendor != null,
            vendorGap = root.bottom - (vendor?.bottom ?: root.bottom),
            loader = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size + onAllNodesWithTag(PROGRESS_BAR_TAG).fetchSemanticsNodes().size,
        )
    }

    private fun ComposeUiTest.tagPresent(tag: String): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.textPresent(text: String): Boolean =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.boundsOfText(text: String): Rect? =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot

    @androidx.compose.runtime.Composable
    private fun PermissionsFirstScreen() {
        PermissionsScreen(
            state = OnboardingAppProbe.state(world),
            actions = PermissionsActions(
                onStepAction = {},
                onAutostartConfirmed = {},
                onNext = {},
                onSkip = {},
            ),
        )
    }

    /** Владелец телефона есть у приложения всегда (ПРФ); сценарий профиля не называет. */
    private fun ensureOwner() {
        if (world.profiles.self() == null) world.profiles.use(OWNER)
    }

    /** Кадры, снятые с композиции: что было на экране и где стоял издатель. */
    private data class FrameFacts(
        val splash: Boolean,
        val todayScreen: Boolean,
        val permissions: Boolean,
        val mark: Boolean,
        val title: Boolean,
        val tagline: Boolean,
        val vendor: Boolean,
        val vendorGap: Float,
        val loader: Int,
    )

    private companion object {
        const val OWNER = "Я"
        const val VENDOR_TOLERANCE = 3f
        const val PROGRESS_BAR_TAG = "progress_bar"
    }
}
