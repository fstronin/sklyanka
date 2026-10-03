package ru.stohastika.sklyanka.screen

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import ru.stohastika.sklyanka.MainActivity
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import java.io.File
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Основа экранных проверок устройства (SKL-96, TDD §4).
 *
 * Проверка запускает настоящую [MainActivity] с настоящим `SklyankaApp`: Hilt-граф,
 * ресурсы, шрифты и окно — платформенные, тестовой замены приложения нет. Это не
 * Robolectric: квалификаторы не задаются, конфигурацию даёт образ.
 *
 * Проверки не предполагают ни пустой базы, ни конкретного её содержимого: образ может
 * нести нагрузку SKL-27 или данные прошлого прогона. Утверждается только то, что
 * создала сама проверка.
 *
 * Каждый кадр — свидетельство (см. [ScreenCapture]); ни один не сверяется с эталоном.
 */
abstract class DeviceScreenTest {

    private val testName = TestName()

    protected val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(testName).around(compose)

    /** Контекст тестового APK: только его собственные ресурсы (строки стенда). */
    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    /** Контекст приложения под проверкой: его ресурсы, его внутренний каталог кадров. */
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Дождаться корня приложения.
     *
     * На чистой установке первый экран — разрешения (`MainActivity` показывает
     * `PermissionsRoute`, пока человек не ушёл с него): проверка нажимает «Пропустить»
     * и ждёт «Сегодня». Последующие запуски в тех же данных идут на «Сегодня» сразу.
     */
    protected fun awaitRoot() {
        compose.waitUntil(ROOT_TIMEOUT_MS) { exists(TAG_TODAY_SCREEN) || exists(TAG_PERMISSIONS_SCREEN) }
        if (exists(TAG_PERMISSIONS_SCREEN)) {
            compose.onNodeWithTag(TAG_SKIP, useUnmergedTree = true).performClick()
            compose.waitUntil(ROOT_TIMEOUT_MS) { exists(TAG_TODAY_SCREEN) }
        }
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

    protected fun exists(tag: String): Boolean = nodesWithTag(tag).isNotEmpty()

    protected fun textExists(text: String): Boolean =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForTag(tag: String, timeoutMs: Long = SCREEN_TIMEOUT_MS): SemanticsNodeInteraction {
        compose.waitUntil(timeoutMs) { exists(tag) }
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
    }

    protected fun click(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
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
        )
    }

    private fun nodesWithTag(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    companion object {
        /** Первый экран поднимается вместе с DataStore, профилем и планировщиком. */
        const val ROOT_TIMEOUT_MS = 30_000L

        /** Ожидание перехода между экранами и появления узла. */
        const val SCREEN_TIMEOUT_MS = 15_000L

        /** Каталог кадров во внутреннем хранилище приложения (см. screenshots.sh). */
        const val SCREENSHOT_DIR = "ui-screens"
    }
}
