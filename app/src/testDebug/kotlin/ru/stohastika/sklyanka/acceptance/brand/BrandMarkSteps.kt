package ru.stohastika.sklyanka.acceptance.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.ui.component.SklyankaLockup
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.component.TAG_LOCKUP
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.FLAT_MARK_SHAPES
import ru.stohastika.sklyanka.ui.token.FlatMarkShape
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.MarkVariant
import ru.stohastika.sklyanka.ui.token.SklyankaBrandType
import ru.stohastika.sklyanka.ui.token.SklyankaMarkTokens
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.markVariantFor
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шаги приёмки знака, иконки и логотипа (SKL-21, группа `@screen @composition`).
 *
 * Сценарии живут в `app`, потому что он один видит сразу всё: компоненты и токены
 * `core-ui`, ресурсы иконки и заставки, решение `SplashRoute` и экраны, где знак стоит
 * иллюстрацией. Вынести знак в `core-ui` отдельным прогоном нельзя: Предыстория файла
 * («сейчас…», «релизная сборка…», «системная тема…») объявлена общим словарём, которого
 * `core-ui` не видит (запрет `ModuleGraphRules`), а второе объявление тех же строк
 * погасило бы прогон целиком.
 *
 * Что читают проверки: настоящая композиция `SklyankaMark`/`SklyankaLockup`/заставки
 * и каталога знака, настоящие токены геометрии ([SklyankaMarkTokens], [markVariantFor],
 * [FLAT_MARK_SHAPES]) и настоящие файлы ресурсов иконки. Рисунок проверяется пикселями
 * снятой композиции, структура — вызовами знака в исходниках продукта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class BrandMarkSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var image: Bitmap? = null
    private var markSizePx: Float = 0f
    private var requested: Int = 0
    private var callSites: List<SourceLine> = emptyList()
    private var iconVariants: List<IconVariant> = emptyList()
    private var lockup: LockupFacts? = null
    private var lockupImage: Bitmap? = null
    private var ladder: List<FlatMarkShape>? = null

    // --- Крупный знак --------------------------------------------------------------------------

    @Когда("^знак показан в крупном размере на тёмном фоне$")
    fun theMarkIsShownLargeOnDark() {
        val side = SklyankaSizes.SplashMark
        assertEquals(MarkVariant.Glass, markVariantFor(side), "the large mark is the glass drawing")
        image = bitmapOf(widthDp = side.value.toInt(), heightDp = side.value.toInt()) {
            SklyankaTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(darkColors().background)) {
                    SklyankaMark(
                        contentDescription = context.getString(UiR.string.cd_app_mark_glass),
                        size = side,
                        environment = MarkEnvironment.Dark,
                    )
                }
            }
        }
        // Поле знака 200 отображено в ширину снимка: координаты слоёв — доли поля.
        markSizePx = requireNotNull(image).width.toFloat()
    }

    @Тогда("^видны концентрические кольца разной плотности$")
    fun concentricRingsOfDifferentDensityAreVisible() {
        val radii = SklyankaMarkTokens.RING_RADII
        assertEquals(3, radii.size, "the glass mark carries three rings")
        assertEquals(radii.size, SklyankaMarkTokens.RING_STROKES.size)
        assertEquals(radii.size, SklyankaMarkTokens.RING_ALPHAS_ON_DARK.size)
        val alphas = SklyankaMarkTokens.RING_ALPHAS_ON_DARK
        assertEquals(alphas.sorted(), alphas, "the density of the rings grows towards the centre")
        radii.forEach { radius ->
            assertTrue(inkOnCircle(radius) >= RING_SAMPLES * 2 / 3, "the ring of radius $radius leaves no paint")
        }
    }

    @И("^виден световой серп по верхнему краю$")
    fun theCrescentIsVisible() {
        assertTrue(SklyankaMarkTokens.CRESCENT_OUTER_RADIUS > SklyankaMarkTokens.CRESCENT_INNER_RADIUS)
        val middle = (SklyankaMarkTokens.CRESCENT_OUTER_RADIUS + SklyankaMarkTokens.CRESCENT_INNER_RADIUS) / 2
        assertTrue(inkOnCircle(middle, CRESCENT_ANGLES) >= 3, "the crescent leaves no paint on the upper edge")
    }

    @И("^видна тёплая дуга преломлённого луча по нижнему краю$")
    fun theWarmArcIsVisible() {
        // «Тёплая дуга» — имя элемента знака, а не утверждение об оттенке: решением Р-20
        // акцентом стала олива, и дуга идёт цветом акцента. Проверяется поэтому совпадение
        // порядка каналов с акцентом, а не «тёплое»: иначе проверка падала бы на каждой
        // смене палитры, ничего не говоря о самом знаке.
        val points = WARM_ARC_ANGLES.count { angle ->
            inkAtCircle(SklyankaMarkTokens.WARM_ARC_RADIUS, angle) &&
                isAccentHued(colourAtCircle(SklyankaMarkTokens.WARM_ARC_RADIUS, angle))
        }
        assertTrue(points >= 2, "the accent arc is not on the lower edge: $points points")
    }

    @И("^видна каустика — световое пятно под знаком$")
    fun theCausticIsVisible() {
        val caustic = colourAtField(SklyankaMarkTokens.CENTER, SklyankaMarkTokens.CAUSTIC_CENTER_Y)
        assertTrue(colourGap(caustic, darkColors().background.toArgb()) > GAP, "the caustic leaves no paint")
        assertTrue(isAccentHued(caustic), "the caustic must be the accent spot under the sign: ${hex(caustic)}")
    }

    @И("^видна центральная точка$")
    fun theDotIsVisible() {
        val dot = colourAtField(SklyankaMarkTokens.CENTER, SklyankaMarkTokens.CENTER)
        assertTrue(
            Color.red(dot) >= LIGHT && Color.green(dot) >= LIGHT && Color.blue(dot) >= LIGHT,
            "the central dot must be a light point: ${hex(dot)}",
        )
    }

    // --- Выбор варианта по размеру -------------------------------------------------------------

    @Когда("^знак запрашивают размером (\\d+) пикселей$")
    fun theMarkIsRequestedAt(size: Int) {
        requested = size
    }

    @Тогда("^показывается (стеклянная отрисовка|плоская редукция)$")
    fun theVariantIsShown(expected: String) {
        val variant = markVariantFor(requested.dp)
        if (expected == GLASS) {
            assertEquals(MarkVariant.Glass, variant, "the size $requested must ask for the glass drawing")
        } else {
            assertTrue(variant.isFlat, "the size $requested must ask for a flat step, got $variant")
        }
    }

    @И("^состав знака — (.+)$")
    fun theCompositionIs(composition: String) {
        val variant = markVariantFor(requested.dp)
        when (composition) {
            GLASS_COMPOSITION -> {
                assertEquals(MarkVariant.Glass, variant)
                assertEquals(3, SklyankaMarkTokens.RING_RADII.size, "кольца")
                assertTrue(SklyankaMarkTokens.CRESCENT_OUTER_RADIUS > 0, "серп")
                assertTrue(SklyankaMarkTokens.WARM_ARC_RADIUS > 0, "тёплая дуга")
                assertTrue(
                    SklyankaMarkTokens.CAUSTIC_RADIUS_X > 0 && SklyankaMarkTokens.CAUSTIC_RADIUS_Y > 0,
                    "каустика",
                )
                assertTrue(SklyankaMarkTokens.DOT_RADIUS > 0, "точка")
            }

            else -> {
                val shape = FLAT_MARK_SHAPES.getValue(variant)
                when (composition) {
                    "два кольца и точка" ->
                        assertTrue(shape.innerRadius != null && shape.dotRadius != null, "two rings and a dot")

                    "два кольца без точки" ->
                        assertTrue(shape.innerRadius != null && shape.dotRadius == null, "two rings, no dot")

                    else ->
                        assertTrue(shape.innerRadius == null && shape.dotRadius != null, "one ring and a large dot")
                }
            }
        }
    }

    // --- Лестница редукции ---------------------------------------------------------------------

    @Когда("^сравнивают плоскую редукцию размеров (\\d+), (\\d+), (\\d+) и (\\d+)$")
    fun theFlatLadderIsCompared(first: Int, second: Int, third: Int, fourth: Int) {
        ladder = listOf(first, second, third, fourth).map { size -> FLAT_MARK_SHAPES.getValue(markVariantFor(size.dp)) }
        assertTrue(requireNotNull(ladder).isNotEmpty())
    }

    @Тогда("^толщина линий растёт от размера к размеру$")
    fun theStrokesGrow() {
        val outer = requireNotNull(ladder).map { it.outerStroke }
        assertEquals(outer.sorted(), outer, "the outer stroke must grow as the mark shrinks: $outer")
        val inner = requireNotNull(ladder).mapNotNull { it.innerStroke }
        assertEquals(inner.sorted(), inner, "the inner stroke must grow as the mark shrinks: $inner")
    }

    @И("^число элементов знака при этом убывает$")
    fun theElementCountFalls() {
        val counts = requireNotNull(ladder).map { it.elementCount }
        assertEquals(counts.sortedDescending(), counts, "the number of elements must not grow: $counts")
        assertTrue(counts.first() > counts.last(), "the ladder must lose elements towards the small sizes: $counts")
    }

    @И("^на каждой ступени знак остаётся кругом с кольцами, а не превращается в пятно$")
    fun everyStepStaysARing() {
        requireNotNull(ladder).forEach { shape ->
            assertTrue(
                shape.outerStroke < shape.outerRadius,
                "the outer ring of ${shape.outerStroke}/${shape.outerRadius} closes into a blob",
            )
            val innerRadius = shape.innerRadius
            val innerStroke = shape.innerStroke
            if (innerRadius != null && innerStroke != null) {
                assertTrue(innerStroke < innerRadius, "the inner ring of $innerStroke/$innerRadius closes into a blob")
            }
            val dot = shape.dotRadius
            if (dot != null) {
                assertTrue(dot < shape.outerRadius - shape.outerStroke / 2, "the dot of $dot merges with the ring")
            }
        }
    }

    // --- Вариант выбирает компонент ------------------------------------------------------------

    @Когда("^экран показывает знак$")
    fun aScreenShowsTheMark() {
        callSites = markCallSites()
        assertTrue(callSites.isNotEmpty(), "the product shows the mark nowhere")
    }

    @Тогда("^он запрашивает знак нужного размера, а вариант выбирается сам$")
    fun theSizeIsRequestedAndTheVariantIsChosen() {
        val manual = callSites.filter { it.text.contains("MarkVariant") || it.text.contains("markVariantFor") }
        assertEquals(emptyList<String>(), manual.map { it.toString() }, "a screen chooses the mark variant by hand")
        val sizeless = callSites.filter { it.text.contains("SklyankaMark(") && !it.text.contains("size") }
        assertEquals(emptyList<String>(), sizeless.map { it.toString() }, "the mark must be requested at a size")
        val selectors = filesContaining("markVariantFor(")
        assertEquals(SELECTOR_FILES, selectors, "the variant must be chosen in one place")
    }

    @И("^цвета знака взяты из токенов дизайн-системы, а не заданы на месте$")
    fun theColoursComeFromTokens() {
        val literals = callSites.filter { COLOUR_LITERAL.containsMatchIn(it.text) }
        assertEquals(emptyList<String>(), literals.map { it.toString() }, "a screen paints the mark with a literal")
        assertTrue(
            File(brandRepoRoot(), DETEKT_CONFIG).readText().contains(HARDCODED_RULE),
            "$HARDCODED_RULE must still forbid colour literals outside the design system",
        )
    }

    // --- Иконка приложения ---------------------------------------------------------------------

    @Когда("^сравнивают тёмный и светлый варианты иконки$")
    fun theIconVariantsAreCompared() {
        iconVariants = listOf(iconVariant("drawable"), iconVariant("drawable-night"))
        assertEquals(2, iconVariants.size)
    }

    @Тогда("^в тёмном варианте знак светлый на тёмной подложке$")
    fun theDarkVariantIsLightOnDark() {
        val dark = iconVariants.first { relativeLuminance(it.tile) < relativeLuminance(it.ink) }
        assertTrue(relativeLuminance(dark.tile) < DARK_TILE_CEILING, "the tile of ${dark.folder} is not dark")
        assertTrue(relativeLuminance(dark.ink) > LIGHT_INK_FLOOR, "the mark of ${dark.folder} is not light")
    }

    @Тогда("^в светлом варианте кольца тёмные на светлой подложке$")
    fun theLightVariantIsDarkOnLight() {
        val light = iconVariants.first { relativeLuminance(it.tile) > relativeLuminance(it.ink) }
        assertTrue(relativeLuminance(light.tile) > LIGHT_TILE_FLOOR, "the tile of ${light.folder} is not light")
        assertTrue(relativeLuminance(light.ink) < DARK_INK_CEILING, "the rings of ${light.folder} are not dark")
    }

    @И("^тёплая акцентная дуга в обоих вариантах одна и та же$")
    fun theWarmArcIsTheSame() {
        assertEquals(iconVariants[0].warmArc, iconVariants[1].warmArc, "the warm arc differs between the icon variants")
    }

    // --- Логотип с названием -------------------------------------------------------------------

    @Когда("^показан логотип с названием$")
    fun theLockupIsShown() {
        val title = context.getString(UiR.string.app_name)
        val tagline = context.getString(UiR.string.brand_tagline).uppercase(InterfaceLocale.VALUE)
        val markLabel = context.getString(UiR.string.cd_app_mark)
        runComposeUiTest {
            setContent { SklyankaLockup() }
            lockup = LockupFacts(
                mark = onNodeWithContentDescription(markLabel).fetchSemanticsNode().boundsInRoot,
                title = onNodeWithText(title).fetchSemanticsNode().boundsInRoot,
                tagline = onNodeWithText(tagline).fetchSemanticsNode().boundsInRoot,
                lockup = onNodeWithTag(TAG_LOCKUP).fetchSemanticsNode().boundsInRoot,
                labels = listOf(markLabel, title, tagline),
            )
            waitForIdle()
        }
        lockupImage = bitmapOf(widthDp = LOCKUP_WIDTH, heightDp = LOCKUP_HEIGHT) { SklyankaLockup() }
    }

    @Тогда("^знак стоит слева, а название «Склянка» — справа$")
    fun theMarkStandsToTheLeft() {
        val facts = requireNotNull(lockup)
        assertTrue(
            facts.mark.right <= facts.title.left,
            "the mark must stand to the left of the name: ${facts.mark} vs ${facts.title}",
        )
    }

    @И("^под названием идёт подпись «приём лекарств» прописными с разрядкой$")
    fun theTaglineIsUnderTheName() {
        val facts = requireNotNull(lockup)
        val tagline = context.getString(UiR.string.brand_tagline)
        assertEquals(TAGLINE_TEXT, tagline, "the tagline must be the words of the mockup")
        assertTrue(tagline.uppercase(InterfaceLocale.VALUE) in facts.labels, "the tagline must be on the lockup")
        assertTrue(
            facts.tagline.top >= facts.title.bottom,
            "the tagline must stand under the name",
        )
        val spacing = SklyankaBrandType.LockupTagline.letterSpacing
        assertTrue(spacing.isEm && abs(spacing.value - TRACKING) < TRACKING_EPS, "the tagline needs the 0.2em tracking")
    }

    @И("^логотип стоит на тёмном фоне$")
    fun theLockupStandsOnDark() {
        val facts = requireNotNull(lockup)
        val corner = requireNotNull(lockupImage).colourAt(facts.lockup.left.toInt() + 2, facts.lockup.top.toInt() + 2)
        assertEquals(
            darkColors().background.toArgb(),
            corner,
            "the lockup background must be ${hex(darkColors().background.toArgb())}, got ${hex(corner)}",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun bitmap(): Bitmap = requireNotNull(image) { "the mark has not been rendered yet" }

    /** Краска ли в точке поля знака. */
    private fun colourAtField(fieldX: Float, fieldY: Float): Int {
        val scale = markSizePx / SklyankaMarkTokens.FIELD
        return bitmap().colourAt((fieldX * scale).roundToInt(), (fieldY * scale).roundToInt())
    }

    private fun inkAtField(fieldX: Float, fieldY: Float): Boolean =
        colourGap(colourAtField(fieldX, fieldY), darkColors().background.toArgb()) > GAP

    /** Цвет точки на окружности радиуса [radius] под углом [angle] (0° — три часа). */
    private fun colourAtCircle(radius: Float, angle: Float): Int = colourAtField(
        SklyankaMarkTokens.CENTER + radius * cos(Math.toRadians(angle.toDouble())).toFloat(),
        SklyankaMarkTokens.CENTER + radius * sin(Math.toRadians(angle.toDouble())).toFloat(),
    )

    private fun inkAtCircle(radius: Float, angle: Float): Boolean =
        colourGap(colourAtCircle(radius, angle), darkColors().background.toArgb()) > GAP

    /** Сколько точек окружности радиуса [radius] оставляют краску. */
    private fun inkOnCircle(
        radius: Float,
        angles: List<Float> = (0 until RING_SAMPLES).map { it * 360f / RING_SAMPLES },
    ): Int = angles.count { angle -> inkAtCircle(radius, angle) }

    /**
     * Краска идёт цветом акцента: порядок каналов совпадает с акцентом, а точка отличается
     * от фона среды. Проверка не завязана на конкретный оттенок — палитра меняется решением
     * (Р-20), а знак обязан остаться тем же знаком.
     */
    private fun isAccentHued(colour: Int): Boolean =
        channelOrder(colour) == channelOrder(darkColors().accent.toArgb()) &&
            colourGap(colour, darkColors().background.toArgb()) > GAP

    private fun channelOrder(colour: Int): List<Int> =
        CHANNELS.sortedByDescending { channel -> channelValue(colour, channel) }

    private fun channelValue(colour: Int, channel: Int): Int = when (channel) {
        RED_CHANNEL -> Color.red(colour)
        GREEN_CHANNEL -> Color.green(colour)
        else -> Color.blue(colour)
    }

    private fun hex(colour: Int): String = String.format("#%06X", colour and 0xFFFFFF)

    private data class LockupFacts(
        val mark: Rect,
        val title: Rect,
        val tagline: Rect,
        val lockup: Rect,
        val labels: List<String>,
    )

    private companion object {
        const val GAP = 8
        const val RED_CHANNEL = 0
        const val GREEN_CHANNEL = 1
        const val BLUE_CHANNEL = 2
        val CHANNELS = listOf(RED_CHANNEL, GREEN_CHANNEL, BLUE_CHANNEL)
        const val LIGHT = 200
        const val RING_SAMPLES = 12
        const val LOCKUP_WIDTH = 390
        const val LOCKUP_HEIGHT = 220
        const val TAGLINE_TEXT = "приём лекарств"
        const val TRACKING = 0.2f
        const val TRACKING_EPS = 0.001f
        const val DARK_TILE_CEILING = 0.05
        const val LIGHT_INK_FLOOR = 0.6
        const val LIGHT_TILE_FLOOR = 0.6
        const val DARK_INK_CEILING = 0.05
        const val GLASS = "стеклянная отрисовка"
        const val GLASS_COMPOSITION = "кольца, серп, тёплая дуга, каустика, точка"
        const val DETEKT_CONFIG = "config/detekt/detekt.yml"
        const val HARDCODED_RULE = "HardcodedDesignToken"
        val COLOUR_LITERAL = Regex("""Color\(|0x[0-9A-Fa-f]{6,}|Color\.""")
        val CRESCENT_ANGLES = listOf(200f, 220f, 240f, 255f)
        val WARM_ARC_ANGLES = listOf(30f, 50f, 70f)
        val SELECTOR_FILES = listOf("Glyphs.kt", "SklyankaMark.kt", "SklyankaMarkTokens.kt")
    }
}
