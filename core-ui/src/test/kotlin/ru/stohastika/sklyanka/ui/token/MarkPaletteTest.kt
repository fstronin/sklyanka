package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Палитра знака — холодное стекло (Р-56, Р-57; норма — `docs/v2/12-design.md` §2.4).
 *
 * Проверка держит три вещи, которые ломаются тихо. Первое — **значения**: знак собран
 * из своей палитры, а не из ролей интерфейса, и подменить его тёплой бумагой снова
 * нечем, кроме правки этой таблицы. Второе — **числа**: контрасты, зазор от `taken`
 * и потолок янтаря названы нормой, и сборка обязана ловить расхождение (НФТ-32).
 * Третье — **границы**: акцент снаружи в краски знака не приходит (Р-56), слабые слои
 * остаются слабыми, а производные заставки выводятся долей смешения, а не подбираются.
 *
 * Числа сверяются с нормой с допуском таблицы ([TABLE_TOLERANCE]) — она посчитана
 * до сотых (§6.1).
 */
class MarkPaletteTest {

    @Test
    fun `the palette carries exactly the values of R-57`() {
        assertEquals("#C4F0F7", hex(SklyankaMarkColors.InkOnDark))
        assertEquals("#0B2831", hex(SklyankaMarkColors.InkOnLight))
        assertEquals("#111A1E", hex(SklyankaMarkColors.TileDark))
        assertEquals("#E7F0F4", hex(SklyankaMarkColors.TileLight))
        assertEquals("#FFFFFF", hex(SklyankaMarkColors.Highlight))
        assertEquals("#A86A22", hex(SklyankaMarkColors.Amber))
    }

    @Test
    fun `the paints are cold glass on both tiles, and the tiles are cold`() {
        // Свойство холода, а не вкус: у тёплой бумаги `#EFE7DB` старшая составляющая
        // красная, у холодного стекла и холодных плиток — синяя (критерий 1).
        assertTrue("the dark paint is not cold glass", isCold(SklyankaMarkColors.InkOnDark))
        assertTrue("the light paint is not cold glass", isCold(SklyankaMarkColors.InkOnLight))
        assertTrue("the dark tile is not cold", isCold(SklyankaMarkColors.TileDark))
        assertTrue("the light tile is not cold", isCold(SklyankaMarkColors.TileLight))
        assertTrue(
            "the paper tile was warm: " + hex(Color(0xFFEFE7DB)),
            !isCold(Color(0xFFEFE7DB)),
        )
        // Стекло читается светлотой: на тёмной плите краска светлая, на светлой — тёмная.
        assertTrue(
            "the dark paint must be light glass",
            relativeLuminance(SklyankaMarkColors.InkOnDark) > relativeLuminance(SklyankaMarkColors.TileDark),
        )
        assertTrue(
            "the light paint must be dark glass",
            relativeLuminance(SklyankaMarkColors.InkOnLight) < relativeLuminance(SklyankaMarkColors.TileLight),
        )
    }

    @Test
    fun `the paint reads on both tiles at AA`() {
        assertEquals(
            14.41,
            contrastRatio(SklyankaMarkColors.InkOnDark, SklyankaMarkColors.TileDark),
            TABLE_TOLERANCE,
        )
        assertEquals(
            13.34,
            contrastRatio(SklyankaMarkColors.InkOnLight, SklyankaMarkColors.TileLight),
            TABLE_TOLERANCE,
        )
    }

    @Test
    fun `the paint stands away from taken on both tiles`() {
        // Холодная краска знака соседствует с холодными ролями исхода приёма, и знак
        // не должен с ними сливаться: порог шкалы §2.2 — 75.
        val dark = profileDistance(SklyankaMarkColors.InkOnDark, Palette.Taken)
        val light = profileDistance(SklyankaMarkColors.InkOnLight, Palette.Taken)
        assertEquals(260, dark)
        assertEquals(82, light)
        assertTrue("the paint is $dark away from taken", dark >= MIN_PROFILE_PAIR_DISTANCE)
        assertTrue("the paint is $light away from taken", light >= MIN_PROFILE_PAIR_DISTANCE)
        assertTrue("the dark gap must be the wider one", dark > light)
    }

    @Test
    fun `the amber arc is one paint for both versions and its ceiling is named`() {
        // Одна краска в обеих версиях: две разные дуги запрещены SKL-21.
        assertEquals(SklyankaMarkColors.Amber, dark.warmArc)
        assertEquals(dark.warmArc, light.warmArc)
        assertEquals(dark.warmArc, dark.caustic.copy(alpha = 1f))
        assertEquals(dark.warmArc, light.caustic.copy(alpha = 1f))
        // Потолок назван честно: порог крупной графики 3,0 пройден, четвёртый порог
        // одной краской взять негде, и выдавать 3,0 за 4,5 запрещено.
        assertEquals(3.99, contrastRatio(dark.warmArc, SklyankaMarkColors.TileDark), TABLE_TOLERANCE)
        assertEquals(3.83, contrastRatio(light.warmArc, SklyankaMarkColors.TileLight), TABLE_TOLERANCE)
        assertTrue(contrastRatio(dark.warmArc, SklyankaMarkColors.TileDark) >= AA_LARGE)
        assertTrue(
            "the fourth threshold is unreachable with one paint",
            contrastRatio(light.warmArc, SklyankaMarkColors.TileLight) < AA_NORMAL,
        )
    }

    @Test
    fun `the decorative layers stay decorative and keep their alphas`() {
        // Альфы не пересматриваются под холодную краску: поднять их — переписать знак,
        // а не перекрасить (Р-57, §2.4). Слабые слои остаются ниже крупной графики 3,0;
        // сильные названы поимённо, чтобы «ниже порога» не читалось как «все».
        assertEquals(listOf(0.24f, 0.07f, 0.015f), SklyankaMarkTokens.BODY_ALPHAS_ON_DARK)
        assertEquals(listOf(0.95f, 0.35f, 0.06f), SklyankaMarkTokens.BODY_ALPHAS_ON_LIGHT)
        assertEquals(listOf(0.85f, 0.16f, 0.42f), SklyankaMarkTokens.RIM_ALPHAS_ON_DARK)
        assertEquals(listOf(0.16f, 0.5f, 0.22f), SklyankaMarkTokens.RIM_ALPHAS_ON_LIGHT)
        assertEquals(listOf(0.13f, 0.26f, 0.5f), SklyankaMarkTokens.RING_ALPHAS_ON_DARK)
        assertEquals(listOf(0.09f, 0.2f, 0.42f), SklyankaMarkTokens.RING_ALPHAS_ON_LIGHT)

        val darkTile = SklyankaMarkColors.TileDark
        val lightTile = SklyankaMarkColors.TileLight
        val weak = mapOf(
            "body dark" to over(dark.body, darkTile),
            "body light" to over(light.body, lightTile),
            "rim light 16" to ratio(light.rim[0], lightTile),
            "rim light 22" to ratio(light.rim[2], lightTile),
            "rings dark 13" to ratio(dark.rings[0], darkTile),
            "rings dark 26" to ratio(dark.rings[1], darkTile),
            "rings light" to over(light.rings, lightTile),
            "reflection" to ratio(requireNotNull(dark.reflection), darkTile),
            "crescent light" to ratio(light.crescent, lightTile),
            "caustic dark" to ratio(dark.caustic, darkTile),
            "caustic light" to ratio(light.caustic, lightTile),
        )
        weak.forEach { (name, ratio) ->
            assertTrue("the weak layer «$name» is $ratio, not decorative any more", ratio < AA_LARGE)
        }
        // Впритык к порогу — средняя остановка ободка на светлой плите (3,04): она одна
        // из слабых стоит выше 3,0, и это названо в норме, а не спрятано.
        assertEquals(3.04, ratio(light.rim[1], lightTile), TABLE_TOLERANCE)
        // Сильные слои держат знак на тёмной плите: точка и серп.
        assertEquals(12.34, ratio(dark.dot, darkTile), TABLE_TOLERANCE)
        assertEquals(9.95, ratio(dark.crescent, darkTile), TABLE_TOLERANCE)
    }

    @Test
    fun `the splash derivatives are mixed from the paint of the mark`() {
        // Оттенок выведен долей смешения с плиткой, а не подобран по кадру (Правило 1 §1).
        val tile = SklyankaMarkColors.TileDark
        assertEquals("#809FA5", hex(markCaptionColor()))
        assertEquals("#728E93", hex(markVendorColor()))
        assertEquals(6.23, contrastRatio(markCaptionColor(), tile), TABLE_TOLERANCE)
        assertEquals(5.05, contrastRatio(markVendorColor(), tile), TABLE_TOLERANCE)
        assertTrue(
            "the worse derivative must still pass AA",
            contrastRatio(markVendorColor(), tile) >= AA_NORMAL,
        )
        // Историческая доля 0,42 не проходила AA — она и есть причина подъёма долей.
        assertTrue(contrastRatio(markDerivedColor(0.42f), tile) < AA_NORMAL)
    }

    @Test
    fun `the mark colours do not take an accent from outside`() {
        // Р-56: акцент перестал быть входом функции красок знака — иначе холодное стекло
        // вернулось бы в тёплый интерфейс при смене акцентной темы «Про».
        val method = Class.forName("ru.stohastika.sklyanka.ui.token.SklyankaMarkTokensKt")
            .declaredMethods
            .single { it.name == "glassMarkColors" }
        assertEquals(1, method.parameterCount)
        assertEquals(MarkEnvironment::class.java, method.parameterTypes.single())
        // И плоская краска с плиткой не берут ролей интерфейса: янтарь — не акцент
        // ни в светлой среде, ни в тёмной.
        assertTrue(glassMarkColors(MarkEnvironment.Dark).warmArc != Palette.AccentOnDark)
        assertTrue(glassMarkColors(MarkEnvironment.Light).warmArc != Palette.Accent)
    }

    private val dark: GlassMarkColors get() = glassMarkColors(MarkEnvironment.Dark)
    private val light: GlassMarkColors get() = glassMarkColors(MarkEnvironment.Light)

    /** Составная краска слоя на плите: знак читается композитом, а не одной альфой. */
    private fun composite(layer: Color, tile: Color): Color = Color(
        red = layer.red * layer.alpha + tile.red * (1f - layer.alpha),
        green = layer.green * layer.alpha + tile.green * (1f - layer.alpha),
        blue = layer.blue * layer.alpha + tile.blue * (1f - layer.alpha),
    )

    /** Худший контраст набора слабых слоёв: у каждого слоя он считается отдельно. */
    private fun over(layers: List<Color>, tile: Color): Double = layers
        .map { ratio(it, tile) }
        .max()

    /** Контраст слоя с плитой: слой полупрозрачен, поэтому читается композитом. */
    private fun ratio(layer: Color, tile: Color): Double = contrastRatio(composite(layer, tile), tile)

    /** Старшая составляющая — синяя: свойство холода, отличающее стекло от тёплой бумаги. */
    private fun isCold(colour: Color): Boolean {
        val red = (colour.toArgb() shr SHIFT_RED) and CHANNEL_MASK
        val green = (colour.toArgb() shr SHIFT_GREEN) and CHANNEL_MASK
        val blue = colour.toArgb() and CHANNEL_MASK
        return blue > red && blue >= green
    }

    private fun hex(colour: Color): String = "#%06X".format(colour.toArgb() and RGB_MASK)

    private companion object {
        const val CHANNEL_MASK = 0xFF
        const val RGB_MASK = 0xFFFFFF
        const val SHIFT_RED = 16
        const val SHIFT_GREEN = 8
    }
}
