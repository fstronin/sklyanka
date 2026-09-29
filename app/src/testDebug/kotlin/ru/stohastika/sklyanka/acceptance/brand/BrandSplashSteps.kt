package ru.stohastika.sklyanka.acceptance.brand

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.settings.SettingsAppProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.SplashScreen
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartReason
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.preview.MarkGallery
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.FLAT_MARK_SHAPES
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.MarkVariant
import ru.stohastika.sklyanka.ui.token.SklyankaMarkTokens
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.glassMarkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.markVariantFor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шаги приёмки заставки, применений знака и доступности (SKL-21).
 *
 * Проверки читают настоящую композицию `SplashScreen`, настоящий каталог знака
 * (`MarkGallery`) и настоящую тему; решение запуска — тот же `SplashRoute`, что
 * в `MainActivity`. Экраны, где знак стоит иллюстрацией, собираются из мира сценария
 * так же, как в прогонах SKL-08 и SKL-20.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class BrandSplashSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var systemThemeLight: Boolean = true
    private var fontScale: Double = 1.0
    private var conditions: StartupConditions? = null
    private var expectedReason: StartReason? = null
    private var splash: SplashFacts? = null
    private var splashImage: Bitmap? = null
    private var labels: Labels? = null
    private var ratios: Map<String, Double> = emptyMap()
    private var illustration: IllustrationFacts? = null
    private var illustrationImage: Bitmap? = null
    private var illustrationColour: Int = 0

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Эталонное устройство как данность.
     *
     * JVM-стенд представляет его для всего, что видно без платформы: ресурсы, композиция,
     * решение запуска. Проверки, которым нужна сама платформа — маски иконки, шторка,
     * время холодного старта, — остаются `@manual` и на стенде не изображаются.
     */
    @Допустим("^релизная сборка «Склянки» установлена на эталонное устройство Huawei с EMUI 15$")
    fun theReleaseBuildIsInstalled() = Unit

    @Допустим("^системная тема устройства — светлая, если в сценарии не сказано иное$")
    fun theSystemThemeIsLight() {
        systemThemeLight = true
    }

    // --- Заставка ------------------------------------------------------------------------------

    /**
     * Запуск приложения: заставка собирается при текущем системном шрифте.
     *
     * Строка одна на три сценария — вид заставки, уход с неё и крупный шрифт, — и во всех
     * трёх собирается настоящая композиция; отличается только масштаб шрифта.
     */
    @Когда("^человек запускает приложение$")
    fun thePersonLaunchesTheApp() {
        splash = splashFacts()
        splashImage = bitmapOf(widthDp = SCREEN_WIDTH, heightDp = SCREEN_HEIGHT) {
            Scaled { SplashScreen() }
        }
    }

    @Тогда("^на тёмном полотне по центру показан знак в полной стеклянной отрисовке$")
    fun theGlassMarkIsCenteredOnTheDark() {
        val facts = requireNotNull(splash)
        assertEquals(
            MarkVariant.Glass,
            markVariantFor(SklyankaSizes.SplashMark),
            "the splash mark must be the glass drawing",
        )
        assertTrue(
            abs(facts.mark.width.toFloat() - SklyankaSizes.SplashMark.value * DENSITY) <= SIZE_TOLERANCE,
            "the splash mark must occupy the size from the mockup, got ${facts.mark.width}",
        )
        assertTrue(
            abs(facts.mark.center.x - facts.root.center.x) <= CENTER_TOLERANCE,
            "the mark must stand in the centre: ${facts.mark} vs ${facts.root}",
        )
        val corner = requireNotNull(splashImage).colourAt(2, 2)
        assertEquals(
            darkColors().background.toArgb(),
            corner,
            "the splash canvas must be dark: ${String.format("#%06X", corner and 0xFFFFFF)}",
        )
        assertTrue(
            requireNotNull(splashImage).hasColourNear(facts.mark, darkColors().accent.toArgb(), ACCENT_TOLERANCE),
            "the glass drawing must carry the warm arc of the accent",
        )
    }

    @И("^под ним название «Склянка»$")
    fun theNameIsUnderTheMark() {
        val facts = requireNotNull(splash)
        assertTrue(facts.title.top >= facts.mark.bottom, "the name must stand under the mark")
        assertEquals(context.getString(UiR.string.app_name), facts.titleText)
    }

    @И("^под названием подпись «приём лекарств»$")
    fun theTaglineIsUnderTheName() {
        val facts = requireNotNull(splash)
        assertTrue(facts.tagline.top >= facts.title.bottom, "the tagline must stand under the name")
        assertEquals(
            context.getString(UiR.string.brand_tagline).uppercase(InterfaceLocale.VALUE),
            facts.taglineText,
        )
    }

    @И("^у нижнего края — «Stohastika»$")
    fun theVendorIsAtTheBottom() {
        val facts = requireNotNull(splash)
        assertEquals(context.getString(UiR.string.brand_vendor), facts.vendorText)
        val fromBottom = facts.root.bottom - facts.vendor.bottom
        assertTrue(
            abs(fromBottom - SklyankaSizes.SplashVendorBottom.value * DENSITY) <= EDGE_TOLERANCE,
            "the vendor must stand ${SklyankaSizes.SplashVendorBottom} from the bottom, got $fromBottom px",
        )
    }

    @Но("^индикатора загрузки на заставке нет$")
    fun thereIsNoLoaderOnTheSplash() {
        val facts = requireNotNull(splash)
        assertEquals(0, facts.loaderNodes, "no progress bar node may be on the splash")
        assertTrue(facts.hasNoLoader, "no progress semantics may be on the splash")
    }

    // --- Куда уходит заставка ------------------------------------------------------------------

    @Допустим(
        "^(приложение запускается впервые|разрешение на уведомления не выдано|точные будильники не разрешены|все разрешения выданы и приложение уже запускалось)$",
    )
    fun theLaunchCondition(condition: String) {
        val (state, reason) = when (condition) {
            "приложение запускается впервые" ->
                StartupConditions(firstLaunch = true, notificationsAllowed = false, exactAlarmsAllowed = false) to
                    StartReason.FirstLaunch

            "разрешение на уведомления не выдано" ->
                StartupConditions(firstLaunch = false, notificationsAllowed = false, exactAlarmsAllowed = true) to
                    StartReason.NotificationsDenied

            "точные будильники не разрешены" ->
                StartupConditions(firstLaunch = false, notificationsAllowed = true, exactAlarmsAllowed = false) to
                    StartReason.ExactAlarmsDenied

            else ->
                StartupConditions(firstLaunch = false, notificationsAllowed = true, exactAlarmsAllowed = true) to
                    StartReason.Ready
        }
        conditions = state
        expectedReason = reason
        // Состояние мира приводится к названному условию. Строка «разрешение
        // на уведомления не выдано» стоит и в сценарии предупреждения на «Сегодня»
        // (SKL-29), которому важно не описание запуска, а настоящее состояние системы:
        // без этого шаг отвечал бы на вопрос, которого сценарий не задавал.
        if (!state.notificationsAllowed) world.system.deny(ReminderPermission.NOTIFICATIONS)
        if (!state.exactAlarmsAllowed) world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    @Тогда("^после заставки открывается (экран настройки разрешений|экран «Сегодня»)$")
    fun theDestinationIs(expected: String) {
        val state = requireNotNull(conditions) { "the scenario must name the launch condition" }
        val reason = requireNotNull(expectedReason)
        assertEquals(reason, SplashRoute.reasonOf(state), "the reason from the table must be the real one")
        val destination = when (expected) {
            "экран настройки разрешений" -> StartDestination.Onboarding
            else -> StartDestination.Today
        }
        assertEquals(destination, SplashRoute.destinationOf(state), "the splash must lead to $destination")
    }

    // --- Знак как иллюстрация ------------------------------------------------------------------

    @Когда("^человек открывает (пустое состояние «Сегодня»|плашку «О приложении» в настройках)$")
    fun thePersonOpens(place: String) {
        when (place) {
            "пустое состояние «Сегодня»" -> {
                // Владелец есть у приложения всегда (ПРФ): сценарий профиля не называет.
                ensureOwner()
                val state = TodayFromWorld.state(world)
                illustration = factsOf(context.getString(UiR.string.cd_app_mark)) {
                    TodayScreen(state = state, onIntent = {})
                }
                illustrationImage = bitmapOf(SCREEN_WIDTH, SCREEN_HEIGHT) {
                    SklyankaTheme { Box(Modifier.fillMaxSize()) { TodayScreen(state = state, onIntent = {}) } }
                }
                illustrationColour = roles().switchOff.toArgb()
            }

            else -> {
                ensureOwner()
                val state = SettingsAppProbe.settingsState(world)
                illustration = factsOf(context.getString(SettingsR.string.cd_settings_about_mark)) {
                    SettingsScreen(state = state, onIntent = {})
                }
                illustrationImage = bitmapOf(SCREEN_WIDTH, SCREEN_HEIGHT) {
                    SklyankaTheme { Box(Modifier.fillMaxSize()) { SettingsScreen(state = state, onIntent = {}) } }
                }
                illustrationColour = roles().textTertiary.toArgb()
            }
        }
    }

    @Тогда("^там показан знак плоской редукцией одной краской$")
    fun theMarkIsAFlatOneInkDrawing() {
        val facts = requireNotNull(illustration)
        // Иллюстрация не берёт стекло даже на 92 px: это правило `FlatMark`
        // (`Glyphs.kt`), и оно повторяет требование §7 — знак одной краской.
        val variant = markVariantFor(androidx.compose.ui.unit.Dp(facts.sizeDp))
            .takeUnless { it == MarkVariant.Glass }
            ?: MarkVariant.FlatRingsAndDot
        assertTrue(variant.isFlat, "at the illustration size the mark must be flat, got $variant")
        val shape = FLAT_MARK_SHAPES.getValue(variant)
        val colours = ringColours(facts, shape.outerRadius)
        assertTrue(colours.isNotEmpty(), "the illustration mark leaves no paint")
        colours.forEach { colour ->
            assertTrue(
                colourGap(colour, illustrationColour) <= INK_TOLERANCE,
                "the flat mark must be painted with one role colour: ${hex(colour)} vs ${hex(illustrationColour)}",
            )
        }
    }

    @И("^размер знака — (\\d+) пикселей$")
    fun theMarkSizeIs(size: Int) {
        val facts = requireNotNull(illustration)
        assertTrue(
            abs(facts.sizeDp - size) <= SIZE_TOLERANCE,
            "the mark must occupy $size px, got ${facts.sizeDp}",
        )
    }

    // --- Доступность ---------------------------------------------------------------------------

    @Когда("^человек проходит экраны со знаком с включённой читалкой$")
    fun thePersonWalksTheScreensWithTheReader() {
        labels = Labels(
            splash = descriptionsOf { SplashScreen() },
            gallery = galleryLabels(),
            // Крупный знак — стеклянная отрисовка компонента; её описание — строка макета
            // `SklyankaMark.dc.html`, единственная для этой формы.
            glass = descriptionsOf {
                SklyankaMark(
                    contentDescription = context.getString(UiR.string.cd_app_mark_glass),
                    size = SklyankaSizes.SplashMark,
                    environment = MarkEnvironment.Dark,
                )
            },
        )
    }

    @Тогда("^знак на заставке озвучивается как «([^»]+)»$")
    fun theSplashMarkIsAnnouncedAs(label: String) {
        assertEquals(context.getString(UiR.string.cd_app_mark), label, "the label must be the resource of the mockup")
        assertTrue(label in requireNotNull(labels).splash, "the splash mark must carry «$label»")
    }

    @И("^крупный знак озвучивается как «([^»]+)»$")
    fun theLargeMarkIsAnnouncedAs(label: String) {
        assertEquals(
            context.getString(UiR.string.cd_app_mark_glass),
            label,
            "the glass drawing must be announced by the label of its mockup",
        )
        assertEquals(listOf(label), requireNotNull(labels).glass, "the large mark must carry exactly its label")
    }

    @И("^иконки озвучиваются как «([^»]+)» и «([^»]+)»$")
    fun theIconsAreAnnouncedAs(dark: String, light: String) {
        val gallery = requireNotNull(labels).gallery.keys
        assertEquals(context.getString(UiR.string.cd_icon_on_dark), dark)
        assertEquals(context.getString(UiR.string.cd_icon_on_light), light)
        assertTrue(dark in gallery && light in gallery, "both icon variants must be announced: $gallery")
    }

    @И("^каждая ступень редукции озвучивается с указанием своего размера$")
    fun everyFlatStepNamesItsSize() {
        val gallery = requireNotNull(labels).gallery
        FLAT_STEPS.forEach { (labelId, size) ->
            val label = context.getString(labelId)
            val facts = requireNotNull(gallery[label]) { "the step «$label» is not announced in the gallery" }
            assertEquals(
                size.toFloat(),
                facts.sizeDp,
                "the step «$label» must be shown at $size px, got ${facts.sizeDp}",
            )
            assertTrue(label.contains(size.toString()), "the label «$label» must name its own size")
        }
    }

    // --- Крупный системный шрифт ---------------------------------------------------------------

    @Допустим("^системный масштаб шрифта — (\\d+) %$")
    fun theSystemFontScaleIs(percent: Int) {
        fontScale = percent / PERCENT
    }

    @Тогда("^название «Склянка» видно целиком$")
    fun theNameIsNotCut() {
        assertWholeText(context.getString(UiR.string.app_name))
    }

    @И("^подпись «приём лекарств» видна целиком$")
    fun theTaglineIsNotCut() {
        assertWholeText(context.getString(UiR.string.brand_tagline).uppercase(InterfaceLocale.VALUE))
    }

    @И("^«Stohastika» не налезает на подпись$")
    fun theVendorDoesNotOverlapTheTagline() {
        val facts = requireNotNull(splash)
        assertTrue(
            facts.vendor.top >= facts.tagline.bottom,
            "the vendor overlaps the tagline at ${fontScale}x: ${facts.vendor} vs ${facts.tagline}",
        )
    }

    // --- Контраст ------------------------------------------------------------------------------

    @Когда("^измеряют контраст надписей заставки к фону$")
    fun theContrastIsMeasured() {
        val colours = darkColors()
        ratios = mapOf(
            "название" to contrastRatio(colours.text.toArgb(), colours.background.toArgb()),
            "подпись" to contrastRatio(colours.textMuted.toArgb(), colours.background.toArgb()),
        )
    }

    @Тогда("^контраст названия и подписи проходит требования AA$")
    fun theContrastPassesAa() {
        ratios.forEach { (role, ratio) ->
            assertTrue(ratio >= AA_RATIO, "the contrast of $role is ${"%.2f".format(ratio)}, below AA")
        }
    }

    @И("^акцентная дуга знака не несёт смысла в одиночку$")
    fun theWarmArcMeansNothingAlone() {
        // Дуга — та же акцентная роль в обеих средах и в обоих вариантах иконки:
        // она украшение, а не носитель состояния (НФТ-33: смысл не в цвете).
        val accent = roles().accent
        assertEquals(accent, glassMarkColors(MarkEnvironment.Dark, accent).warmArc, "the arc is the accent on dark")
        assertEquals(accent, glassMarkColors(MarkEnvironment.Light, accent).warmArc, "the arc is the accent on light")
        // И собственного описания у неё нет: знак озвучивается целиком, одним описанием.
        val descriptions = descriptionsOf {
            SklyankaMark(
                contentDescription = context.getString(UiR.string.cd_app_mark_glass),
                size = SklyankaSizes.SplashMark,
                environment = MarkEnvironment.Dark,
            )
        }
        assertEquals(1, descriptions.size, "the arc must not be announced apart from the mark")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun roles() = if (systemThemeLight) lightColors() else darkColors()

    /** Владелец телефона есть у приложения всегда (ПРФ); сценарий профиля не называет. */
    private fun ensureOwner() {
        if (world.profiles.self() == null) world.profiles.use(OWNER)
    }

    private fun assertWholeText(text: String) {
        val fact = requireNotNull(requireNotNull(splash).texts.firstOrNull { it.text == text }) {
            "the text «$text» is not on the splash at font scale $fontScale"
        }
        val root = requireNotNull(splash).root
        assertFalse(
            fact.cut,
            "the text «$text» is cut off at ${fontScale}x: ${fact.complaint()}, root=$root",
        )
        assertEquals(text, fact.lines.joinToString(separator = ""), "the text «$text» is not shown whole")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun splashFacts(): SplashFacts {
        var facts: SplashFacts? = null
        runComposeUiTest {
            setContent { Scaled { SplashScreen() } }
            val root = onRoot().fetchSemanticsNode()
            facts = SplashFacts(
                root = root.boundsInRoot,
                mark = onNodeWithContentDescription(
                    context.getString(UiR.string.cd_app_mark),
                ).fetchSemanticsNode().boundsInRoot,
                title = onNodeWithText(context.getString(UiR.string.app_name)).fetchSemanticsNode().boundsInRoot,
                tagline = onNodeWithText(taglineText()).fetchSemanticsNode().boundsInRoot,
                vendor = onNodeWithText(context.getString(UiR.string.brand_vendor)).fetchSemanticsNode().boundsInRoot,
                texts = brandTextFacts(),
                loaderNodes = onAllNodesWithTag(PROGRESS_BAR_TAG).fetchSemanticsNodes().size,
                hasNoLoader = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isEmpty(),
                titleText = context.getString(UiR.string.app_name),
                taglineText = taglineText(),
                vendorText = context.getString(UiR.string.brand_vendor),
            )
            waitForIdle()
        }
        return requireNotNull(facts)
    }

    private fun taglineText(): String = context.getString(UiR.string.brand_tagline).uppercase(InterfaceLocale.VALUE)

    private fun descriptionsOf(content: @Composable () -> Unit): List<String> {
        var found: List<String> = emptyList()
        runComposeUiTest {
            setContent { SklyankaTheme { content() } }
            found = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            waitForIdle()
        }
        return found
    }

    private fun galleryLabels(): Map<String, MarkFacts> {
        var found: Map<String, MarkFacts> = emptyMap()
        runComposeUiTest {
            setContent { SklyankaTheme { MarkGallery() } }
            found = onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().associate { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().first()
                label to MarkFacts(bounds = node.boundsInRoot, sizeDp = node.boundsInRoot.width.toFloat())
            }
            waitForIdle()
        }
        return found
    }

    private fun factsOf(label: String, content: @Composable () -> Unit): IllustrationFacts {
        var facts: IllustrationFacts? = null
        runComposeUiTest {
            setContent { SklyankaTheme { content() } }
            val bounds = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            facts = IllustrationFacts(bounds = bounds, sizeDp = bounds.width.toFloat())
            waitForIdle()
        }
        return requireNotNull(facts)
    }

    /** Цвета кольца знака-иллюстрации: восемь точек внешнего кольца. */
    private fun ringColours(facts: IllustrationFacts, outerRadius: Float): List<Int> {
        val image = requireNotNull(illustrationImage) { "the illustration has not been rendered yet" }
        val scale = facts.sizeDp / SklyankaMarkTokens.FIELD
        return (0 until INK_SAMPLES).map { index ->
            val angle = Math.toRadians((index * 360f / INK_SAMPLES).toDouble())
            val x = facts.bounds.center.x + outerRadius * scale * cos(angle)
            val y = facts.bounds.center.y + outerRadius * scale * sin(angle)
            image.colourAt(x.roundToInt(), y.roundToInt())
        }
    }

    private fun hex(colour: Int): String = String.format("#%06X", colour and 0xFFFFFF)

    /** Содержимое заставки: габариты, надписи и разметка при текущем шрифте. */
    private data class SplashFacts(
        val root: Rect,
        val mark: Rect,
        val title: Rect,
        val tagline: Rect,
        val vendor: Rect,
        val texts: List<BrandTextFact>,
        val loaderNodes: Int,
        val hasNoLoader: Boolean,
        val titleText: String,
        val taglineText: String,
        val vendorText: String,
    )

    /** Что нашлось в каталоге: описание и его габариты. */
    private data class MarkFacts(val bounds: Rect, val sizeDp: Float)

    private data class IllustrationFacts(val bounds: Rect, val sizeDp: Float)

    private data class Labels(val splash: List<String>, val gallery: Map<String, MarkFacts>, val glass: List<String>)

    @Composable
    private fun Scaled(content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.toFloat()),
            content = content,
        )
    }

    private companion object {
        const val OWNER = "Я"
        const val SCREEN_WIDTH = 390
        const val SCREEN_HEIGHT = 2400
        const val DENSITY = 1f
        const val PERCENT = 100.0
        const val AA_RATIO = 4.5
        const val CENTER_TOLERANCE = 2.0
        const val EDGE_TOLERANCE = 3.0
        const val SIZE_TOLERANCE = 1.5
        const val INK_TOLERANCE = 10
        const val ACCENT_TOLERANCE = 12
        const val INK_SAMPLES = 8
        const val PROGRESS_BAR_TAG = "progress_bar"

        /** Ступени редукции каталога: описание → размер (`MarkGallery`). */
        val FLAT_STEPS = listOf(
            UiR.string.cd_flat_mark_72 to 72,
            UiR.string.cd_flat_mark_44 to 44,
            UiR.string.cd_flat_mark_28 to 28,
            UiR.string.cd_flat_mark_18 to 18,
        )
    }
}
