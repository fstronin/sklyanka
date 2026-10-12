package ru.stohastika.sklyanka.acceptance.mark

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.brand.appColour
import ru.stohastika.sklyanka.acceptance.brand.argb
import ru.stohastika.sklyanka.acceptance.brand.bitmapOf
import ru.stohastika.sklyanka.acceptance.brand.brandRepoRoot
import ru.stohastika.sklyanka.acceptance.brand.colourAt
import ru.stohastika.sklyanka.acceptance.brand.contrastRatio
import ru.stohastika.sklyanka.acceptance.brand.relativeLuminance
import ru.stohastika.sklyanka.startup.SplashScreen
import ru.stohastika.sklyanka.ui.component.SklyankaLockup
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.GlassMarkColors
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SklyankaBrandType
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaMarkColors
import ru.stohastika.sklyanka.ui.token.SklyankaMarkTokens
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.glassMarkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.markCaptionColor
import ru.stohastika.sklyanka.ui.token.markDerivedColor
import ru.stohastika.sklyanka.ui.token.markTileBackground
import ru.stohastika.sklyanka.ui.token.markVendorColor
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-139 — холодная палитра знака (Р-56, Р-57).
 *
 * Проверки читают настоящий код: краски знака приходят функциями токенов
 * ([glassMarkColors], [markTileBackground], [markDerivedColor]), заставка собирается
 * композицией [SplashScreen], а при 200 % читается разметка настоящей надписи — той,
 * которую набрал [ru.stohastika.sklyanka.ui.component.BrandTitle].
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса; пакет
 * `ru.stohastika.sklyanka.acceptance` разрешён правилом `RussianTextInCode` поэтому.
 *
 * Числа сценариев не дублируются в коде: сценарий говорит о свойствах, значения живут
 * в палитре и в норме `docs/norm/06-design.md` §2.4. Число названо только там, где без него
 * свойство неразличимо, — зазор от `taken` (порог 75), потолок янтаря (3,0) и контрасты
 * производных (порог 4,5).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk139MarkSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var dark: GlassMarkColors = glassMarkColors(MarkEnvironment.Dark)
    private var light: GlassMarkColors = glassMarkColors(MarkEnvironment.Light)
    private var themes: List<ProfileAccent> = emptyList()
    private var renderings: List<Bitmap> = emptyList()
    private var title: TitleReading? = null
    private var lockupTitle: TitleReading? = null

    // --- 1. Свойства красок знака на холодных плитках ------------------------------------------

    @Допустим("знак собран в светлой среде и в тёмной")
    fun theMarkIsBuiltInBothEnvironments() {
        dark = glassMarkColors(MarkEnvironment.Dark)
        light = glassMarkColors(MarkEnvironment.Light)
    }

    @Тогда("краска знака на тёмной плите — светлое стекло: синяя составляющая старшая")
    fun theDarkPaintIsLightColdGlass() {
        assertTrue(isCold(markPaint(MarkEnvironment.Dark)), "the dark paint is not blue-leading")
        assertTrue(
            relativeLuminance(argb(markPaint(MarkEnvironment.Dark))) >
                relativeLuminance(argb(markTileBackground(MarkEnvironment.Dark))),
            "the dark paint must be light glass",
        )
    }

    @И("краска знака на светлой плите — тёмное стекло: синяя составляющая старшая")
    fun theLightPaintIsDarkColdGlass() {
        assertTrue(isCold(markPaint(MarkEnvironment.Light)), "the light paint is not blue-leading")
        assertTrue(
            relativeLuminance(argb(markPaint(MarkEnvironment.Light))) <
                relativeLuminance(argb(markTileBackground(MarkEnvironment.Light))),
            "the light paint must be dark glass",
        )
    }

    @И("плитка светлой среды холодная, а не тёплая бумага")
    fun theLightTileIsCold() {
        assertTrue(isCold(markTileBackground(MarkEnvironment.Light)), "the light tile is warm paper")
    }

    @И("плитка тёмной среды — холодный уголь, а не тёплый")
    fun theDarkTileIsCold() {
        assertTrue(isCold(markTileBackground(MarkEnvironment.Dark)), "the dark tile is warm coal")
    }

    // --- 2. Контрасты краски знака и зазор от taken --------------------------------------------

    @Тогда("контраст краски знака с тёмной плиткой не меньше 4,5")
    fun theDarkContrastPasses() {
        assertContrast(markPaint(MarkEnvironment.Dark), markTileBackground(MarkEnvironment.Dark))
    }

    @И("контраст краски знака с светлой плиткой не меньше 4,5")
    fun theLightContrastPasses() {
        assertContrast(markPaint(MarkEnvironment.Light), markTileBackground(MarkEnvironment.Light))
    }

    @Тогда("расстояние краски знака от цвета taken `#1F5F6B` не меньше 75 по шкале §2.2")
    fun thePaintStandsAwayFromTaken() {
        val darkGap = scaleDistance(markPaint(MarkEnvironment.Dark), TAKEN_COLOUR)
        val lightGap = scaleDistance(markPaint(MarkEnvironment.Light), TAKEN_COLOUR)
        assertTrue(
            darkGap >= DISTANCE_THRESHOLD,
            "the dark paint is $darkGap away from taken, below $DISTANCE_THRESHOLD",
        )
        assertTrue(
            lightGap >= DISTANCE_THRESHOLD,
            "the light paint is $lightGap away from taken, below $DISTANCE_THRESHOLD",
        )
    }

    @И("расстояние краски знака от taken на тёмной плите больше, чем на светлой")
    fun theDarkGapIsTheWiderOne() {
        assertTrue(
            scaleDistance(markPaint(MarkEnvironment.Dark), TAKEN_COLOUR) >
                scaleDistance(markPaint(MarkEnvironment.Light), TAKEN_COLOUR),
            "the dark gap must be the wider one",
        )
    }

    // --- 3. Знак не берёт акцент темы -----------------------------------------------------------

    @Допустим("знак собран в акцентной теме «Про»")
    fun theMarkIsBuiltInTheProAccentTheme() {
        // Акцентная тема «Про» — набор ролей, которым тема красит действия: у владельца
        // акцент один, в чужом профиле — цвет профиля (ПРФ-11). Обе темы и есть смена.
        themes = listOf(ProfileAccent.Owner, ProfileAccent.Foreign(ProfileColor.Plum))
        val accents = themes.map { lightColors().copy(accent = it.color).accent }
        assertEquals(2, accents.distinct().size, "the scenario needs two different accent themes")
    }

    @Когда("акцентную тему сменяют на другую")
    fun theAccentThemeChanges() {
        // Знак рисуется настоящей композицией при каждой теме: проверять нечем, кроме
        // картинки, — акцент в краски знака не входит, и смена темы не должна её тронуть.
        renderings = themes.map { theme -> renderMark(theme) }
        assertEquals(themes.size, renderings.size, "every accent theme must render the mark")
    }

    @Тогда("краски знака не меняются")
    fun theMarkColoursDoNotChange() {
        val first = renderings.first()
        val repainted = renderings.drop(1).filterNot { it.sameAs(first) }
        assertTrue(
            repainted.isEmpty(),
            "the mark is repainted by a change of the accent theme: ${renderings.size} renderings differ",
        )
    }

    @И("функция красок знака акцент снаружи не получает")
    fun theMarkColoursTakeNoAccent() {
        val method = Class.forName("ru.stohastika.sklyanka.ui.token.SklyankaMarkTokensKt")
            .declaredMethods
            .single { it.name == "glassMarkColors" }
        assertEquals(1, method.parameterCount, "the accent must not be a parameter of the mark colours")
        assertEquals(MarkEnvironment::class.java, method.parameterTypes.single())
    }

    // --- 4. Янтарная дуга — одна краска в двух версиях ------------------------------------------

    @Тогда("дуга и каустика — одна и та же краска в обеих версиях")
    fun theArcIsOnePaint() {
        assertEquals(SklyankaMarkColors.Amber, dark.warmArc)
        assertEquals(dark.warmArc, light.warmArc)
        assertEquals(dark.warmArc, dark.caustic.copy(alpha = 1f))
        assertEquals(dark.warmArc, light.caustic.copy(alpha = 1f))
    }

    @И("её контраст не ниже потолка крупной графики 3,0")
    fun theArcPassesTheLargeGraphicsCeiling() {
        assertTrue(
            arcContrast(MarkEnvironment.Dark) >= AA_LARGE_GRAPHICS,
            "the amber on the dark tile is ${arcContrast(MarkEnvironment.Dark)}",
        )
        assertTrue(
            arcContrast(MarkEnvironment.Light) >= AA_LARGE_GRAPHICS,
            "the amber on the light tile is ${arcContrast(MarkEnvironment.Light)}",
        )
    }

    @Но("4,5 на обеих плитках одной краской недостижимо")
    fun theFourthThresholdIsUnreachable() {
        // Потолок назван честно, а не выдан за 4,5: области светлоты для четвёртого порога
        // одной краской не пересекаются, а две разные дуги запрещены SKL-21.
        assertTrue(
            arcContrast(MarkEnvironment.Dark) < AA_NORMAL || arcContrast(MarkEnvironment.Light) < AA_NORMAL,
            "one paint must not pass 4,5 on both tiles",
        )
    }

    // --- 5. Декоративные слои ----------------------------------------------------------------

    @Тогда("слабые слои знака остаются ниже крупной графики 3,0 или впритык к ней")
    fun theWeakLayersStayWeak() {
        // Числа слоёв названы нормой §2.4 и сверяются с нормой юнит-тестом палитры
        // (`MarkPaletteTest`); здесь проверяется свойство сценария: слабые слои не
        // перешагнули потолок крупной графики, а единственное исключение осталось впритык.
        val overCeiling = weakLayers().filterValues { it >= AA_LARGE_GRAPHICS }
        assertTrue(
            overCeiling.isEmpty(),
            "the weak layers crossed the large graphics ceiling: $overCeiling",
        )
        val rimMiddle = ratio(light.rim[1], markTileBackground(MarkEnvironment.Light))
        assertTrue(
            rimMiddle >= AA_LARGE_GRAPHICS && rimMiddle < AA_LARGE_GRAPHICS + RIM_MIDDLE_HEADROOM,
            "the light rim middle stop is $rimMiddle: it is not «впритык» to 3,0 any more",
        )
    }

    @И("прозрачность слоёв остаётся прежней — под холодную краску альфы не поднимаются")
    fun theAlphasAreUnchanged() {
        assertEquals(listOf(0.24f, 0.07f, 0.015f), SklyankaMarkTokens.BODY_ALPHAS_ON_DARK)
        assertEquals(listOf(0.95f, 0.35f, 0.06f), SklyankaMarkTokens.BODY_ALPHAS_ON_LIGHT)
        assertEquals(listOf(0.85f, 0.16f, 0.42f), SklyankaMarkTokens.RIM_ALPHAS_ON_DARK)
        assertEquals(listOf(0.16f, 0.5f, 0.22f), SklyankaMarkTokens.RIM_ALPHAS_ON_LIGHT)
        assertEquals(listOf(0.13f, 0.26f, 0.5f), SklyankaMarkTokens.RING_ALPHAS_ON_DARK)
        assertEquals(listOf(0.09f, 0.2f, 0.42f), SklyankaMarkTokens.RING_ALPHAS_ON_LIGHT)
        assertEquals(0.82f, SklyankaMarkTokens.CRESCENT_ALPHA_ON_DARK)
        assertEquals(0.95f, SklyankaMarkTokens.CRESCENT_ALPHA_ON_LIGHT)
        assertEquals(0.92f, SklyankaMarkTokens.DOT_ALPHA_ON_DARK)
        assertEquals(0.72f, SklyankaMarkTokens.DOT_ALPHA_ON_LIGHT)
    }

    // --- 6. Заставка --------------------------------------------------------------------------

    @Допустим("приложение показывает заставку")
    fun theSplashIsShown() = Unit

    @Тогда("заставка — тёмная поверхность, но холодная")
    fun theSplashIsADarkColdSurface() {
        val canvas = bitmapOf(SCREEN_WIDTH, SCREEN_HEIGHT) { SplashScreen() }.colourAt(CORNER, CORNER)
        assertEquals(
            markTileBackground(MarkEnvironment.Dark).toArgb(),
            canvas,
            "the splash canvas must be the cold mark tile: ${hex(canvas)}",
        )
        assertTrue(relativeLuminance(canvas) < DARK_LUMINANCE_CEILING, "the splash canvas is not dark")
    }

    @И("фон заставки и цвета статус-бара остаются тёмными")
    fun theSplashBackgroundAndStatusBarStayDark() {
        val theme = File(brandRepoRoot(), SPLASH_THEME).readText()
        listOf("windowSplashScreenBackground", "android:statusBarColor", "android:navigationBarColor").forEach { item ->
            assertTrue(
                theme.contains("<item name=\"$item\">@color/mark_tile_dark</item>"),
                "the theme must take the cold tile for $item",
            )
        }
        assertTrue(
            relativeLuminance(appColour("mark_tile_dark")) < DARK_LUMINANCE_CEILING,
            "the splash background must be dark",
        )
    }

    @И("производные краски подписи и «Stohastika» смешаны из краски знака и тёмной плитки")
    fun theDerivativesAreMixed() {
        // Производная не подбирается по кадру: она — доля смешения краски знака с плиткой
        // (Правило 1 §1 нормы). Точные значения производных — норма и юнит-тест палитры;
        // здесь проверяется само правило: обе производные выведены смешением.
        val fractions = listOf(SklyankaMarkColors.CAPTION_FRACTION, SklyankaMarkColors.VENDOR_FRACTION)
        fractions.forEach { fraction ->
            assertEquals(
                hex(markDerivedColor(fraction)),
                hex(mixed(fraction)),
                "the derivative at $fraction is not the mix of the paint and the tile",
            )
        }
        assertEquals(hex(markCaptionColor()), hex(mixed(SklyankaMarkColors.CAPTION_FRACTION)))
        assertEquals(hex(markVendorColor()), hex(mixed(SklyankaMarkColors.VENDOR_FRACTION)))
        // И производная — не сама краска и не сама плитка: это отдельный оттенок.
        listOf(markCaptionColor(), markVendorColor()).forEach { derivative ->
            assertTrue(
                derivative != SklyankaMarkColors.InkOnDark && derivative != SklyankaMarkColors.TileDark,
                "the derivative must differ from the paint and the tile: ${hex(derivative)}",
            )
        }
    }

    @И("контраст подписи и «Stohastika» с тёмной плиткой не меньше 4,5")
    fun theVendorAndCaptionPassAa() {
        assertContrast(markVendorColor(), markTileBackground(MarkEnvironment.Dark))
        assertContrast(markCaptionColor(), markTileBackground(MarkEnvironment.Dark))
    }

    @И("контраст подписи «ПРИЁМ ЛЕКАРСТВ» с тёмной плиткой не меньше 4,5")
    fun theCaptionPassesAa() {
        assertContrast(markCaptionColor(), markTileBackground(MarkEnvironment.Dark))
    }

    // --- 7. Границы палитры: интерфейс не тронут -----------------------------------------------

    @Допустим("система цвета интерфейса собрана")
    fun theInterfaceColoursAreBuilt() = Unit

    @Тогда("интерфейс остаётся «оливой на бумаге»: фон, акцент и его семья прежние")
    fun theInterfaceStaysOliveOnPaper() {
        val light = lightColors()
        assertEquals(0xFFF8F6EF.toInt(), argb(light.background))
        assertEquals(0xFF5F6F2E.toInt(), argb(light.accent))
        assertEquals(0xFFEBEEDA.toInt(), argb(light.accentSoft))
        assertEquals(0xFF1F5F6B.toInt(), argb(light.taken))
        assertEquals(0xFF181712.toInt(), argb(darkColors().background))
    }

    @И("ни одна холодная краска знака не стала ролью интерфейса")
    fun noMarkColourBecameARole() {
        val roles = interfaceRoleValues()
        val leaked = markColours().filter { colour -> roles.any { it.copy(alpha = 1f) == colour } }
        assertTrue(
            leaked.isEmpty(),
            "the mark palette leaked into the interface roles: ${leaked.joinToString { hex(argb(it)) }}",
        )
    }

    @И("новых ролей интерфейса не появилось")
    fun noNewInterfaceRoles() {
        assertEquals(INTERFACE_ROLES, interfaceRoleNames(), "the set of interface roles changed")
    }

    // --- 8. Название одной строкой без переноса по слогам (V-16) --------------------------------

    @Допустим("шрифт увеличен до 200 %")
    fun theFontIsDoubled() = Unit

    @Когда("показаны заставка и фирменный блок")
    fun theSplashAndTheLockupAreShown() {
        title = readingOf { SplashScreen() }
        lockupTitle = readingOf { SklyankaLockup() }
    }

    @Тогда("название «Склянка» стоит одной строкой")
    fun theNameStaysOnOneLine() {
        val splash = requireNotNull(title)
        assertEquals(1, splash.lineCount, "the name must stay on one line: [${splash.lines.joinToString(" | ")}]")
        val lockup = requireNotNull(lockupTitle)
        assertEquals(
            1,
            lockup.lineCount,
            "the lockup name must stay on one line: [${lockup.lines.joinToString(" | ")}]",
        )
    }

    @И("кегль уменьшается сам вместо переноса")
    fun theSizeShrinks() {
        // Свойство «кегль подстроился» проверяемо только там, где слово действительно
        // не помещается: заставка широкая, и её название при 200 % проходит целиком,
        // а вот фирменному блоку знак и зазор оставляют меньше половины ширины — там
        // кегль обязан уменьшиться сам, а не разложить слово на строки.
        val splash = requireNotNull(title)
        assertTrue(
            splash.fontSize.value <= SklyankaBrandType.SplashTitle.fontSize.value,
            "the splash name must not grow past its style, got ${splash.fontSize}",
        )
        val lockup = requireNotNull(lockupTitle)
        assertTrue(
            lockup.fontSize.value < SklyankaBrandType.LockupTitle.fontSize.value,
            "the lockup name must shrink at 200 %, got ${lockup.fontSize}",
        )
        // И уменьшение не «на глаз»: без него слово шире отведённой ширины, поэтому
        // проверяется ещё и то, что нарисованное название в ширину помещается.
        listOf("splash" to splash, "lockup" to lockup).forEach { (surface, reading) ->
            assertTrue(
                reading.fitsWidth,
                "the name does not fit the $surface width at 200 %: it is clipped, not shrunk",
            )
        }
    }

    @Но("слово не разрезано переносом")
    fun theWordIsNotBroken() {
        assertTrue(!requireNotNull(title).wordBroken, "the name must not be broken inside a word")
        assertTrue(!requireNotNull(lockupTitle).wordBroken, "the lockup name must not be broken either")
    }

    // --- Обвязка ---------------------------------------------------------------------------

    /** Разметка названия: строки, кегль, признак разрезанного слова и попадание в ширину. */
    private data class TitleReading(
        val lines: List<String>,
        val fontSize: TextUnit,
        val wordBroken: Boolean,
        val fitsWidth: Boolean,
    ) {
        val lineCount: Int get() = lines.size
    }

    /**
     * Название, набранное при двойном масштабе шрифта.
     *
     * Масштаб подменяется плотностью, а не стилем: сценарий говорит о системной настройке
     * (НФТ-31), и подмена стиля проверила бы не то. Ширина — телефонная, чтобы слово
     * действительно не помещалось и кегль обязан был уменьшиться.
     */
    private fun readingOf(content: @Composable () -> Unit): TitleReading {
        val name = context.getString(UiR.string.app_name)
        var read: TitleReading? = null
        runComposeUiTest {
            setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = DOUBLE_SCALE),
                ) {
                    Box(Modifier.fillMaxSize().background(SklyankaMarkColors.TileDark)) { content() }
                }
            }
            val node = onNodeWithText(name).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = requireNotNull(results.firstOrNull()) { "the name has no layout on this surface" }
            read = TitleReading(
                lines = layout.visibleLines(),
                fontSize = layout.layoutInput.style.fontSize,
                wordBroken = layout.brokenInsideWord(),
                fitsWidth = layout.getLineRight(FIRST_LINE) <= layout.layoutInput.constraints.maxWidth + FIT_TOLERANCE,
            )
        }
        return requireNotNull(read)
    }

    /** Видимые строки разметки: без признака конца строки, иначе стык читался бы как разрыв. */
    private fun TextLayoutResult.visibleLines(): List<String> = (0 until lineCount).map { index ->
        layoutInput.text.text.substring(getLineStart(index), getLineEnd(index, visibleEnd = true))
    }

    /** Перенос разрезал слово: буква или цифра с обеих сторон границы строк. */
    private fun TextLayoutResult.brokenInsideWord(): Boolean {
        val lines = visibleLines()
        return (1 until lines.size).any { index ->
            val tail = lines[index - 1].lastOrNull()
            val head = lines[index].firstOrNull()
            tail != null && head != null && tail.isLetterOrDigit() && head.isLetterOrDigit()
        }
    }

    /** Краска знака на плите: точка несёт её же, только с объявленной альфой. */
    private fun markPaint(environment: MarkEnvironment): Color = when (environment) {
        MarkEnvironment.Dark -> dark.dot.copy(alpha = 1f)
        MarkEnvironment.Light -> light.dot.copy(alpha = 1f)
    }

    /**
     * Знак, нарисованный в композиции при заданной акцентной теме.
     *
     * Смена акцента — это тема, а не значение функции, поэтому проверяется картинкой:
     * иначе свойство «знак не берёт акцент» держалось бы одной сигнатурой функции.
     */
    private fun renderMark(theme: ProfileAccent): Bitmap = bitmapOf(MARK_SIDE, MARK_SIDE) {
        SklyankaTheme(profile = theme) {
            Box(Modifier.fillMaxSize().background(darkColors().background)) {
                SklyankaMark(
                    contentDescription = context.getString(UiR.string.cd_app_mark_glass),
                    size = MARK_SIZE,
                    environment = MarkEnvironment.Dark,
                )
            }
        }
    }

    /**
     * Шкала различимости §2.2 — одна на весь продукт: евклидово расстояние в sRGB
     * (канал 255) с округлением до целого. Помощник — копия `core-ui` (`ProfileDistance.kt`):
     * тестовые наборы модулей друг друга не видят, а порог обязан быть тем же.
     */
    private fun scaleDistance(colour: Color, other: Int): Int {
        fun channel(value: Float, argb: Int, shift: Int): Float =
            (value - ((argb shr shift) and CHANNEL_MASK) / CHANNELS) * CHANNELS
        val red = channel(colour.red, other, SHIFT_RED)
        val green = channel(colour.green, other, SHIFT_GREEN)
        val blue = channel(colour.blue, other, SHIFT_BLUE)
        return sqrt((red * red + green * green + blue * blue).toDouble()).roundToInt()
    }

    /** Контраст янтарной дуги с плитой среды: дуга — крупная графика, не обычный текст. */
    private fun arcContrast(environment: MarkEnvironment): Double = contrastRatio(
        argb(glassMarkColors(environment).warmArc),
        argb(markTileBackground(environment)),
    )

    /** Слабые слои палитры: тело, ободок, кольца, отражение, каустика и белый серп светлой. */
    private fun weakLayers(): Map<String, Double> {
        val darkTile = markTileBackground(MarkEnvironment.Dark)
        val lightTile = markTileBackground(MarkEnvironment.Light)
        return mapOf(
            "body dark" to worst(dark.body, darkTile),
            "body light" to worst(light.body, lightTile),
            "rim light 16" to ratio(light.rim[0], lightTile),
            "rim light 22" to ratio(light.rim[2], lightTile),
            "rings dark 13" to ratio(dark.rings[0], darkTile),
            "rings dark 26" to ratio(dark.rings[1], darkTile),
            "rings light" to worst(light.rings, lightTile),
            "reflection" to ratio(requireNotNull(dark.reflection), darkTile),
            "crescent light" to ratio(light.crescent, lightTile),
            "caustic dark" to ratio(dark.caustic, darkTile),
            "caustic light" to ratio(light.caustic, lightTile),
        )
    }

    private fun worst(layers: List<Color>, tile: Color): Double = layers.map { ratio(it, tile) }.max()

    private fun ratio(layer: Color, tile: Color): Double = contrastRatio(argb(composite(layer, tile)), argb(tile))

    /** Композит слоя на плите: знак читается составной краской, а не одной альфой. */
    private fun composite(layer: Color, tile: Color): Color = Color(
        red = layer.red * layer.alpha + tile.red * (1f - layer.alpha),
        green = layer.green * layer.alpha + tile.green * (1f - layer.alpha),
        blue = layer.blue * layer.alpha + tile.blue * (1f - layer.alpha),
    )

    /** Смешение краски знака с тёмной плиткой долей `fraction` — что должна дать производная. */
    private fun mixed(fraction: Float): Color {
        fun channel(tile: Float, ink: Float): Float =
            ((tile + fraction * (ink - tile)) * CHANNELS).roundToInt() / CHANNELS
        return Color(
            red = channel(SklyankaMarkColors.TileDark.red, SklyankaMarkColors.InkOnDark.red),
            green = channel(SklyankaMarkColors.TileDark.green, SklyankaMarkColors.InkOnDark.green),
            blue = channel(SklyankaMarkColors.TileDark.blue, SklyankaMarkColors.InkOnDark.blue),
        )
    }

    private fun markColours(): List<Color> = listOf(
        SklyankaMarkColors.InkOnDark,
        SklyankaMarkColors.InkOnLight,
        SklyankaMarkColors.TileDark,
        SklyankaMarkColors.TileLight,
        SklyankaMarkColors.Amber,
        markCaptionColor(),
        markVendorColor(),
    )

    /** Значения ролей интерфейса: оба набора плюс цвета профилей, которыми тонируется акцент. */
    private fun interfaceRoleValues(): List<Color> =
        listOf(lightColors(), darkColors()).flatMap { set -> roleGetters(set).map { valueOf(it, set) } } +
            ProfilePalette.all.map { it.color }

    /** Имена ролей интерфейса: геттеры набора, прочитанные отражением. */
    private fun interfaceRoleNames(): List<String> = roleGetters(lightColors())
        // Хвост вида `-0d7_KjU` — искажение имени, которым компилятор помечает геттер,
        // возвращающий value-класс `Color`: без него имя роли не прочитать.
        .map { it.name.removePrefix("get").substringBefore(MANGLING).replaceFirstChar { first -> first.lowercase() } }
        .sorted()

    /**
     * Геттеры ролей набора.
     *
     * Отбор по имени, а не по типу возврата: `Color` — value-класс, и в байткоде его
     * возвращает `long`, поэтому проверка на `Color::class.java` не нашла бы ни одной роли
     * и проверка стала бы пустой.
     */
    private fun roleGetters(set: SklyankaColors) = set.javaClass.declaredMethods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 }

    /** Значение роли: value-класс приходит распакованным числом. */
    private fun valueOf(getter: java.lang.reflect.Method, set: SklyankaColors): Color =
        Color((getter.invoke(set) as Number).toLong())

    private fun assertContrast(foreground: Color, background: Color) {
        val ratio = contrastRatio(argb(foreground), argb(background))
        assertTrue(ratio >= AA_NORMAL, "the contrast is ${"%.2f".format(ratio)}, below $AA_NORMAL")
    }

    private fun isCold(colour: Color): Boolean {
        val value = argb(colour)
        val red = (value shr SHIFT_RED) and CHANNEL_MASK
        val green = (value shr SHIFT_GREEN) and CHANNEL_MASK
        val blue = value and CHANNEL_MASK
        return blue > red && blue >= green
    }

    private fun hex(colour: Color): String = hex(argb(colour))

    private fun hex(value: Int): String = "#%06X".format(value and RGB_MASK)

    private companion object {
        /** Заставка: её тему читает сценарий о тёмном фоне и статус-баре. */
        const val SPLASH_THEME = "app/src/main/res/values/themes.xml"

        /** `taken` — цвет исхода приёма, с которым знак не должен сливаться (§2.4 нормы). */
        const val TAKEN_COLOUR = 0xFF1F5F6B.toInt()

        /** Порог шкалы различимости §2.2 между цветами набора. */
        const val DISTANCE_THRESHOLD = 75

        /** AA для обычного текста (§6.1). */
        const val AA_NORMAL = 4.5

        /** Потолок крупной графики (§6.1): дуга — крупная графика, не обычный текст. */
        const val AA_LARGE_GRAPHICS = 3.0

        /** Допуск сверки с таблицей §6.1: она посчитана до сотых. */
        const val TABLE_TOLERANCE = 0.02

        /** «Впритык к 3,0»: насколько средняя остановка ободка светлой плиты вправе её превысить. */
        const val RIM_MIDDLE_HEADROOM = 0.1

        /** Первая строка разметки названия: имя собственное обязано уместиться в одну. */
        const val FIRST_LINE = 0

        /**
         * Допуск попадания в ширину: `getLineRight` — край чернил, а не метрика шрифта,
         * поэтому последний пиксель округления в счёт не идёт.
         */
        const val FIT_TOLERANCE = 1f

        /** Плитка считается тёмной ниже этой светлоты: `#111A1E` даёт 0,0095. */
        const val DARK_LUMINANCE_CEILING = 0.2

        const val DOUBLE_SCALE = 2f
        const val SCREEN_WIDTH = 390
        const val SCREEN_HEIGHT = 844
        const val CORNER = 2

        /** Знак, которым проверяется независимость от акцента: стеклянная отрисовка. */
        val MARK_SIZE = SklyankaSizes.SplashMark
        val MARK_SIDE = MARK_SIZE.value.toInt()
        const val CHANNELS = 255f
        const val CHANNEL_MASK = 0xFF
        const val RGB_MASK = 0xFFFFFF
        const val SHIFT_RED = 16
        const val SHIFT_GREEN = 8
        const val SHIFT_BLUE = 0

        /** Разделитель искажения имени у геттера, возвращающего value-класс. */
        const val MANGLING = '-'

        /** Роли интерфейса: тридцать ролей §1.1 и §1.2. Новых задача не заводит (критерий 9). */
        val INTERFACE_ROLES = listOf(
            "accent",
            "accentBorder",
            "accentSoft",
            "archiveBackground",
            "archiveText",
            "background",
            "danger",
            "iconFaint",
            "line",
            "lineInteractive",
            "lineOnSurface",
            "missed",
            "neutralSoft",
            "onAccent",
            "onAccentSoft",
            "profileBackground",
            "profileHeader",
            "profileStripe",
            "progressTrack",
            "scrim",
            "surface",
            "switchOff",
            "taken",
            "takenPartial",
            "takenSoft",
            "text",
            "textDisabled",
            "textMuted",
            "textOnDark",
            "textTertiary",
            "warning",
        ).sorted()
    }
}
