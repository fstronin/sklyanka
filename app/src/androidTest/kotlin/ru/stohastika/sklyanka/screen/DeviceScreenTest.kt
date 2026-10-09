package ru.stohastika.sklyanka.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.platform.startup.SystemStartupState
import ru.stohastika.sklyanka.screen.support.DeviceActivities
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.screen.support.DeviceTestStores
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Основа экранных проверок устройства (SKL-96, переработано SKL-97, TDD §7).
 *
 * Проверка идёт на тестовом Hilt-графе: приложение тестов (`DeviceTestApplication`),
 * Room в памяти, чистые DataStore, управляемые часы. Настоящая `MainActivity` запускается
 * ручным [DeviceActivities.launch], а не `ActivityScenario`: заставка и асинхронное
 * чтение DataStore не дают правилу дождаться простоя главного потока.
 *
 * `@Before` чистит хранилища и часы, снимает отметку о запуске и сеет владельца;
 * состояние, на которое проверка не смотрит (онбординг, согласие), сеет [seedDefaultState].
 * Каждый кадр — свидетельство (см. [ScreenCapture]); ни один не сверяется с эталоном.
 */
@RunWith(AndroidJUnit4::class)
abstract class DeviceScreenTest {

    private val testName = TestName()

    private val hilt = HiltAndroidRule(this)

    protected val compose = createEmptyComposeRule()

    /**
     * Уборка после проверки: `singleTask` и Hilt-граф на проверку не терпят живую
     * Activity прошлого сценария, поэтому все активности закрываются до того, как
     * Hilt снимет тестовый компонент (TDD §7.1).
     */
    private val lifecycle = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    runCatching { DeviceActivities.closeAll(compose) }
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(testName).around(hilt).around(compose).around(lifecycle)

    /** Контекст тестового APK: только его собственные ресурсы (строки стенда). */
    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    /** Контекст приложения под проверкой: его ресурсы, его внутренний каталог кадров. */
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Владелец, созданный сидом: лекарства сеются в его профиль. */
    protected lateinit var ownerProfile: Profile
        private set

    @Before
    fun setUpDeviceStand() {
        DeviceTestStores.clearAll(targetContext)
        DeviceTestClock.reset()
        clearLaunchMark()
        hilt.inject()
        ownerProfile =
            DeviceTestSeed.ownerProfile(targetContext, testContext.getString(R.string.device_test_profile_name))
        seedDefaultState()
    }

    /**
     * Состояние, на которое проверка не смотрит: онбординг пройден. Сценарий первого
     * запуска переопределяет этот сид; согласия в релизе нет (ПМ-31).
     */
    protected open fun seedDefaultState() {
        DeviceTestSeed.onboardingSeen(targetContext)
    }

    /** Запустить настоящую `MainActivity` и дождаться, что она возобновлена. */
    protected fun launchApp() {
        DeviceActivities.launch(targetContext)
        waitUntil(LAUNCH_TIMEOUT_MS, "запуска MainActivity") { DeviceActivities.resumed() != null }
    }

    /** Запустить экран-активность продукта его же намерением (SKL-98). */
    protected fun launchScreen(intent: Intent) {
        DeviceActivities.launchIntent(targetContext, intent)
    }

    /** Дождаться, что возобновлена активность указанного класса. */
    protected fun awaitActivity(clazz: Class<out Activity>) {
        waitUntil(LAUNCH_TIMEOUT_MS, "активности ${clazz.simpleName}") {
            clazz.isInstance(DeviceActivities.resumedActivity())
        }
    }

    /** Дождаться, что активность указанного класса больше не на экране. */
    protected fun awaitScreenClosed(clazz: Class<out Activity>) {
        waitUntil(LAUNCH_TIMEOUT_MS, "закрытия ${clazz.simpleName}") {
            !clazz.isInstance(DeviceActivities.resumedActivity())
        }
    }

    /**
     * Довести приложение до корня — «Сегодня».
     *
     * В управляемом состоянии онбординг не показывается: отметка посеяна. Экрана
     * согласия в релизе нет вовсе (ПМ-31).
     */
    protected fun awaitRoot() {
        leaveOnboardingIfShown()
        waitUntil(ROOT_TIMEOUT_MS, "экрана «Сегодня»") { exists(TAG_TODAY_SCREEN) }
    }

    private fun leaveOnboardingIfShown() {
        waitUntil(ROOT_TIMEOUT_MS, "экрана разрешений или «Сегодня»") {
            exists(TAG_TODAY_SCREEN) || exists(TAG_PERMISSIONS_SCREEN)
        }
        if (!exists(TAG_PERMISSIONS_SCREEN)) return
        compose.onNodeWithTag(TAG_SKIP, useUnmergedTree = true).performClick()
        waitUntil(ROOT_TIMEOUT_MS, "экрана «Сегодня» после пропуска") { exists(TAG_TODAY_SCREEN) }
    }

    /** Открыть раздел «Лекарства» вкладкой нижней панели. */
    protected fun openMedications() {
        val label = targetContext.getString(UiR.string.tab_meds)
        compose.onNode(
            hasContentDescription(label) and hasAnyAncestor(hasTestTag(TAG_BOTTOM_BAR)),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(TAG_MEDICATION_LIST)
    }

    /** Открыть раздел «Настройки» вкладкой нижней панели. */
    protected fun openSettings() {
        val label = targetContext.getString(UiR.string.tab_settings)
        compose.onNode(
            hasContentDescription(label) and hasAnyAncestor(hasTestTag(TAG_BOTTOM_BAR)),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(TAG_SETTINGS_SCREEN)
    }

    /**
     * Ввести значение в поле по тегу-обёртке: поле адресуется действием ввода среди
     * потомков, потому что на экране не одно поле (TDD §8.3).
     *
     * `scroll = false` — для полотна, до которого прокручивать нечего (шторка стоит
     * на экране целиком): `performScrollTo` без родителя с действием прокрутки падает
     * («Semantic Node has no parent layout with a Scroll SemanticsAction»).
     */
    protected fun typeInto(fieldTag: String, value: String, scroll: Boolean = true) {
        val input = inputOf(fieldTag)
        if (scroll) input.performScrollTo()
        input.performTextInput(value)
        compose.waitForIdle()
    }

    /** Заменить значение поля: черновик правки уже заполнен прежним текстом. */
    protected fun replaceText(fieldTag: String, value: String) {
        val input = inputOf(fieldTag)
        input.performScrollTo()
        input.performTextClearance()
        input.performTextInput(value)
        compose.waitForIdle()
    }

    private fun inputOf(fieldTag: String): SemanticsNodeInteraction = compose.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag)),
        useUnmergedTree = true,
    )

    /** Прокрутить ленивый список до узла: далёкие строки не компонуются без прокрутки. */
    protected fun scrollTo(containerTag: String, nodeTag: String) {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(containerTag)),
            useUnmergedTree = true,
        ).performScrollToNode(hasTestTag(nodeTag))
        waitForTag(nodeTag)
    }

    /** Прокрутить к узлу и нажать: форма и карточка прокручиваются целиком. */
    protected fun scrollToAndClick(tag: String) {
        waitForTag(tag).performScrollTo().performClick()
    }

    /** Текст узла одной строкой: проверки сравнивают показанное, а не внутренние числа. */
    protected fun nodeText(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(" ") { it.text }

    /**
     * Сохранить лекарство в профиль владельца.
     *
     * Курс начинается вчера, а не сегодня: `ScheduleExpander` не порождает приём, время
     * которого на дне начала курса уже прошло (`skipPastTimesOnCourseStart`, SKL-06) —
     * человек, заводящий лекарство днём, утренний приём не пропускал. С вчерашним началом
     * посеянный приём сегодняшнего утра существует и уже наступил (TDD §6.4).
     *
     * Черновик берётся из домена: его правила те же, что у формы, и лекарство получается
     * валидным по инвариантам модели (ИНВ-4, ИНВ-6).
     */
    protected fun seedMedication(
        id: String,
        name: String,
        times: List<LocalTime>,
        startDate: LocalDate = DeviceTestClock.today.minusDays(1),
        endDate: LocalDate? = null,
        stockLeft: BigDecimal? = null,
        packageSize: BigDecimal? = null,
    ) {
        val draft = MedicationDraft.new(profileId = ownerProfile.id, today = startDate).copy(
            name = name,
            doseAmount = BigDecimal.ONE,
            times = times,
            endDate = endDate,
            stockLeft = stockLeft,
            packageSize = packageSize,
        )
        DeviceTestSeed.medication(targetContext, draft.toMedication(id))
    }

    /** Приёмы на окно вперёд по профилю владельца — явная замена стартовой генерации. */
    protected fun planIntakes() {
        DeviceTestSeed.planIntakes(targetContext, ownerProfile.id)
    }

    protected fun exists(tag: String): Boolean = nodesWithTag(tag).isNotEmpty()

    protected fun textExists(text: String): Boolean =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForTag(tag: String, timeoutMs: Long = SCREEN_TIMEOUT_MS): SemanticsNodeInteraction {
        waitUntil(timeoutMs, "узла «$tag»") { exists(tag) }
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
    }

    protected fun click(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    /** Ожидание условия; по таймауту — дерево семантики в logcat и понятная ошибка. */
    protected fun waitUntil(timeoutMs: Long = SCREEN_TIMEOUT_MS, what: String = "условия", condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMs) { condition() }
        } catch (e: ComposeTimeoutException) {
            dumpTree()
            throw AssertionError(
                "Не дождались $what за $timeoutMs мс (дерево — в logcat, тег ${DeviceActivities.LOG_TAG})",
                e,
            )
        }
    }

    /**
     * Снять кадр окна приложения после того, как состояние уже утверждено.
     *
     * По умолчанию снимается корень окна; диалог снимается своим корнем
     * (`onNode(isDialog())`), потому что у него отдельное окно.
     */
    protected fun capture(step: String, node: SemanticsNodeInteraction = compose.onRoot(useUnmergedTree = true)) {
        compose.waitForIdle()
        ScreenCapture.write(
            target = File(targetContext.filesDir, SCREENSHOT_DIR),
            step = step,
            test = "${javaClass.simpleName}.${testName.methodName}",
            node = node,
            // Повтор после разового таймаута RenderThread ждёт простоя, а не спит.
            awaitRetry = { compose.waitForIdle() },
        )
    }

    /**
     * Снять отметку о запуске: без неё `StartupRouter` считает запуск первым, и маршрут
     * первого экрана зависел бы от порядка проверок. Имена — из `SystemStartupState`.
     */
    private fun clearLaunchMark() {
        targetContext.getSharedPreferences(SystemStartupState.STARTUP_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun dumpTree() {
        runCatching {
            compose
                .onAllNodes(isRoot(), useUnmergedTree = true)
                .printToLog(DeviceActivities.LOG_TAG, maxDepth = Int.MAX_VALUE)
        }
    }

    private fun nodesWithTag(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    companion object {
        /** Запуск Hilt-графа и первого экрана: заставка держится до готовности данных. */
        const val LAUNCH_TIMEOUT_MS = 30_000L

        /** Первый экран поднимается вместе с DataStore, профилем и планировщиком. */
        const val ROOT_TIMEOUT_MS = 30_000L

        /** Ожидание перехода между экранами и появления узла. */
        const val SCREEN_TIMEOUT_MS = 15_000L

        /** Каталог кадров во внутреннем хранилище приложения (см. screenshots.sh). */
        const val SCREENSHOT_DIR = "ui-screens"
    }
}
