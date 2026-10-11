package ru.stohastika.sklyanka.acceptance.palette

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.token.AA_LARGE
import ru.stohastika.sklyanka.ui.token.AA_NORMAL
import ru.stohastika.sklyanka.ui.token.MIN_PROFILE_ACCENT_DISTANCE
import ru.stohastika.sklyanka.ui.token.MIN_PROFILE_PAIR_DISTANCE
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.Palette
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SCRIM_ALPHA
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaMarkColors
import ru.stohastika.sklyanka.ui.token.contrastRatio
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.flatMarkColor
import ru.stohastika.sklyanka.ui.token.glassMarkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.markTileBackground
import ru.stohastika.sklyanka.ui.token.profileDistance
import ru.stohastika.sklyanka.ui.token.tintedColors
import java.io.File

/**
 * Шаги экранной группы приёмки SKL-109 — палитры «олива на бумаге» (Р-20).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно. Пакет `ru.stohastika.sklyanka.acceptance` разрешён
 * правилом `RussianTextInCode` именно поэтому.
 *
 * Сценарии говорят о свойствах цвета, а не о значениях, и это не уловка: значения живут
 * в палитре и в нормативе (`docs/v2/12-design.md` §2.1, §6.1), и второй их экземпляр здесь
 * разошёлся бы с первым. Единственное исключение — норматив: Предыстория сверяет, что
 * значения, которыми пользуется код, названы документом.
 *
 * Состояние сценария держится в самом классе: Cucumber создаёт по экземпляру на сценарий.
 */
@Suppress("TooManyFunctions")
class Sk109PaletteSteps {

    private var dark = false
    private var foreign = false

    // --- Предыстория -----------------------------------------------------------

    /**
     * Значения палитры не «примерно такие»: они названы нормативом, и разойтись
     * с ним код не имеет права (Правило 1 §1).
     */
    @Допустим("значения палитры закреплены нормативом дизайн-системы")
    fun thePaletteValuesAreFixedByTheDesignSystemDocument() {
        val document = File(repositoryRoot(), NORMATIVE_DOCUMENT).readText()
        val missing = theNineChangedValues()
            .filterNot { colour -> document.contains(hex(colour), ignoreCase = true) }
        assertTrue(
            "the design system document does not name these values: " +
                missing.joinToString { hex(it) },
            missing.isEmpty(),
        )
    }

    // --- Среда -----------------------------------------------------------------

    @Допустим("система цвета собрана для светлой среды владельца")
    fun theSystemIsBuiltForTheLightOwnerEnvironment() {
        dark = false
        foreign = false
    }

    @Допустим("система цвета собрана для светлой среды чужого профиля")
    fun theSystemIsBuiltForTheLightForeignEnvironment() {
        dark = false
        foreign = true
    }

    @Допустим("система цвета собрана для тёмной среды напоминания")
    fun theSystemIsBuiltForTheDarkEnvironment() {
        dark = true
        foreign = false
    }

    // --- 1. Светлая среда ------------------------------------------------------

    @Тогда("фон светлых экранов — тёплая бумага: красная составляющая старшая")
    fun theLightBackgroundIsWarmPaper() {
        val background = lightColors().background
        assertTrue(
            "the light background ${hex(background)} is not warm paper: red is not the leading channel",
            red(background) > green(background) && red(background) > blue(background),
        )
    }

    @И("прежнего терракотового акцента в системе нет")
    fun theTerracottaAccentIsGone() {
        val adopted = theNineChangedValues()
        assertTrue(
            "the superseded terracotta accent survived in the system",
            adopted.none { rgb(it) == TERRACOTTA },
        )
        assertTrue(
            "the light accent must not be the superseded terracotta",
            rgb(lightColors().accent) != TERRACOTTA,
        )
    }

    @Тогда("основной акцент — олива: зелёная составляющая старшая")
    fun theAccentIsOlive() {
        val accent = lightColors().accent
        assertTrue(
            "the accent ${hex(accent)} is not olive: green is not the leading channel",
            green(accent) > red(accent) && green(accent) > blue(accent),
        )
    }

    @И("мягкая подложка акцента светлее акцента и того же оливкового тона")
    fun theSoftAccentIsALighterTintOfTheAccent() {
        val light = lightColors()
        channels(light.accentSoft).forEachIndexed { index, channel ->
            val accent = channels(light.accent)[index]
            val surface = channels(light.surface)[index]
            assertTrue(
                "the soft accent channel $index ($channel) is not a tint of the accent $accent " +
                    "towards the surface $surface",
                channel > minOf(accent, surface) && channel < maxOf(accent, surface),
            )
        }
    }

    @И("поверхности остаются белыми")
    fun theSurfacesStayWhite() {
        assertEquals(
            "the light surface must stay white",
            WHITE,
            lightColors().surface.toArgb(),
        )
    }

    // --- 2. Контрасты ----------------------------------------------------------

    @Тогда("контраст белой подписи с акцентной заливкой не меньше 4,5")
    fun theWhiteCaptionOnTheAccentPasses() {
        val light = lightColors()
        assertEquals("the caption on the accent fill must be white", WHITE, light.onAccent.toArgb())
        assertContrast(light.onAccent, light.accent, "caption on accent")
    }

    @И("контраст акцента с фоном не меньше 4,5")
    fun theAccentOnTheBackgroundPasses() {
        assertContrast(lightColors().accent, lightColors().background, "accent on background")
    }

    @И("контраст акцента с белой поверхностью не меньше 4,5")
    fun theAccentOnTheSurfacePasses() {
        assertContrast(lightColors().accent, lightColors().surface, "accent on surface")
    }

    @И("контраст подписи на мягкой подложке не меньше 4,5")
    fun theCaptionOnTheSoftAccentPasses() {
        assertContrast(lightColors().onAccentSoft, lightColors().accentSoft, "caption on soft accent")
    }

    // --- 3. Нейтральные роли ---------------------------------------------------

    @Тогда("основной и вторичный текст читаются и на фоне, и на поверхности")
    fun thePrimaryAndSecondaryTextPass() {
        val light = lightColors()
        assertContrast(light.text, light.background, "text on background")
        assertContrast(light.text, light.surface, "text on surface")
        assertContrast(light.textMuted, light.background, "muted on background")
        assertContrast(light.textMuted, light.surface, "muted on surface")
    }

    @И("третий уровень текста норму обычного текста не проходит, но остаётся читаемым как крупный")
    fun theTertiaryTextStaysAnException() {
        val light = lightColors()
        val onBackground = contrastRatio(light.textTertiary, light.background)
        assertTrue(
            "tertiary on background is $onBackground, AA for normal text must stay out of reach",
            onBackground < AA_NORMAL,
        )
        assertTrue(
            "tertiary on background is $onBackground, AA for large text must hold",
            onBackground >= AA_LARGE,
        )
        assertTrue(
            "tertiary on surface must stay below AA for normal text",
            contrastRatio(light.textTertiary, light.surface) < AA_NORMAL,
        )
    }

    @И("недоступный текст не проходит и норму крупного текста")
    fun theDisabledTextStaysAnException() {
        assertTrue(
            "disabled on surface must stay below AA even for large text",
            contrastRatio(lightColors().textDisabled, lightColors().surface) < AA_LARGE,
        )
    }

    @И("неинформативная графика норму обычного текста не проходит")
    fun theFaintIconStaysAnException() {
        assertTrue(
            "the faint icon is decorative and must stay below AA for normal text",
            contrastRatio(lightColors().iconFaint, lightColors().surface) < AA_NORMAL,
        )
    }

    // --- 4. Палитра профилей и предупреждение ----------------------------------

    @Тогда("ни один цвет закрытого набора профилей не совпадает с основным акцентом")
    fun noProfileColourRepeatsTheAccent() {
        assertTrue(
            "the accent must not be selectable as a profile colour",
            ProfilePalette.all.none { it.color == Palette.Accent },
        )
    }

    @И("каждый цвет набора отстоит от акцента не меньше порога различимости")
    fun everyProfileColourStaysApartFromTheAccent() {
        val tooClose = ProfilePalette.all.filter { profile ->
            profileDistance(profile.color, Palette.Accent) < MIN_PROFILE_ACCENT_DISTANCE
        }
        assertTrue(
            tooClose.joinToString("\n") { profile ->
                "${profile.name} is ${profileDistance(profile.color, Palette.Accent)} away from the accent, " +
                    "below $MIN_PROFILE_ACCENT_DISTANCE: the profile would read as the accent"
            },
            tooClose.isEmpty(),
        )
    }

    @И("цвета набора отстоят друг от друга не меньше порога различимости")
    fun profileColoursStayApartFromEachOther() {
        val tooClose = mutableListOf<String>()
        ProfilePalette.all.forEachIndexed { index, first ->
            ProfilePalette.all.drop(index + 1).forEach { second ->
                val distance = profileDistance(first.color, second.color)
                if (distance < MIN_PROFILE_PAIR_DISTANCE) {
                    tooClose += "${first.name} and ${second.name} are $distance apart, below $MIN_PROFILE_PAIR_DISTANCE"
                }
            }
        }
        assertTrue(tooClose.joinToString("\n"), tooClose.isEmpty())
    }

    @Тогда("предупреждение и опасность — одно и то же значение")
    fun theWarningAndTheDangerShareOneValue() {
        assertEquals(
            "the warning and the danger are two roles on one value (ПРФ-10)",
            lightColors().warning,
            lightColors().danger,
        )
    }

    @И("оно равно цвету профиля из закрытого набора")
    fun theValueRepeatsAProfileColour() {
        val warning = lightColors().warning
        assertTrue(
            "the warning ${hex(warning)} must repeat one of the closed profile colours",
            ProfilePalette.all.any { it.color == warning },
        )
    }

    @И("в чужом профиле предупреждение не перекрашивается")
    fun theWarningIsNotTinted() {
        assertEquals(
            "the warning must read the same in any profile (ПРФ-10)",
            lightColors().warning,
            roles().warning,
        )
    }

    @И("опасность не перекрашивается под цвет профиля")
    fun theDangerIsNotTinted() {
        assertEquals(
            "the danger must not take the profile colour (ПРФ-10)",
            lightColors().danger,
            roles().danger,
        )
    }

    @И("предупреждение читается на фоне и на мягкой подложке")
    fun theWarningIsReadable() {
        val light = lightColors()
        assertContrast(light.warning, light.background, "warning on background")
        assertContrast(light.warning, light.accentSoft, "warning on soft accent")
    }

    @И("предупреждение отличимо от основного акцента")
    fun theWarningStaysApartFromTheAccent() {
        val distance = profileDistance(lightColors().warning, Palette.Accent)
        assertTrue(
            "the warning is $distance away from the accent, below $MIN_PROFILE_ACCENT_DISTANCE: " +
                "an error would read as an action",
            distance >= MIN_PROFILE_ACCENT_DISTANCE,
        )
    }

    // --- 5. Тёмная среда и затемнение ------------------------------------------

    @Тогда("фон тёмной среды — тот же тёмный токен палитры, что и затемнение модального слоя")
    fun theDarkBackgroundIsTheScrimToken() {
        val dark = darkColors()
        assertEquals(Palette.DarkBackground, dark.background)
        assertEquals(Palette.DarkBackground.copy(alpha = SCRIM_ALPHA), lightColors().scrim)
    }

    @И("акцент тёмной среды — светлая олива: зелёная составляющая старшая")
    fun theDarkAccentIsLightOlive() {
        val accent = darkColors().accent
        assertTrue(
            "the dark accent ${hex(accent)} is not light olive: green is not the leading channel",
            green(accent) > red(accent) && green(accent) > blue(accent),
        )
    }

    @И("подпись на акцентной заливке читается не хуже 4,5")
    fun theDarkCaptionOnTheAccentPasses() {
        assertContrast(darkColors().onAccent, darkColors().accent, "caption on the dark accent")
    }

    @И("основной акцент на тёмном фоне норму не проходит — потому и заменён")
    fun theLightAccentFailsOnTheDarkBackground() {
        val ratio = contrastRatio(Palette.Accent, Palette.DarkBackground)
        assertTrue(
            "the light accent on the dark background is $ratio: the role replacement would be pointless",
            ratio < AA_NORMAL,
        )
    }

    @И("основной, вторичный и третий уровень текста на тёмном фоне читаются")
    fun theDarkTextIsReadable() {
        val dark = darkColors()
        assertContrast(dark.text, dark.background, "dark text on background")
        assertContrast(dark.textMuted, dark.background, "dark muted on background")
        assertContrast(dark.textTertiary, dark.background, "dark tertiary on background")
    }

    @Когда("приложение рисует модальный слой")
    fun theApplicationDrawsAModalLayer() {
        assertEquals(
            "the scrim opacity must be the declared one",
            SCRIM_ALPHA,
            lightColors().scrim.alpha,
            CHANNEL_ROUNDING,
        )
    }

    @Тогда("подложка под слоем — тёмный токен палитры с прежней долей 0,32")
    fun theScrimFollowsTheDarkToken() {
        assertEquals(Palette.DarkBackground.copy(alpha = SCRIM_ALPHA), lightColors().scrim)
    }

    @И("своего значения затемнения ни один слой не заводит")
    fun noLayerBringsItsOwnScrim() {
        // Затемнение — роль темы, а не параметр слоя: у обеих сред и у чужого профиля
        // она одна и та же (SKL-105 уже проверяет, что слои берут её из темы).
        assertEquals(lightColors().scrim, darkColors().scrim)
        assertEquals(
            lightColors().scrim,
            tintedColors(lightColors(), FOREIGN_PROFILE.color).scrim,
        )
    }

    // --- 6. Марка и оболочки иконок --------------------------------------------

    @Тогда("знак на тёмной плите рисуется краской знака")
    fun theMarkOnTheDarkTileTakesTheLightBackground() {
        val mark = glassMarkColors(MarkEnvironment.Dark)
        val layers = mark.body + mark.rim + mark.rings +
            listOfNotNull(mark.crescent, mark.reflection, mark.dot)
        val expected = rgb(SklyankaMarkColors.InkOnDark)
        val other = layers.filterNot { rgb(it) == expected }
        assertTrue(
            "${other.size} mark layers are not painted with the mark ink " +
                "${hex(SklyankaMarkColors.InkOnDark)}: " + other.joinToString { hex(it) },
            other.isEmpty(),
        )
    }

    @И("плитка знака тёмной среды равна тёмной плите знака")
    fun theDarkMarkTileRepeatsTheDarkBackground() {
        assertEquals(
            SklyankaMarkColors.TileDark.toArgb(),
            markTileBackground(MarkEnvironment.Dark).toArgb(),
        )
    }

    @И("плоская редукция светлой среды рисуется краской знака на светлой плите")
    fun theFlatMarkOnLightTakesTheTextToken() {
        assertEquals(
            SklyankaMarkColors.InkOnLight.toArgb(),
            flatMarkColor(MarkEnvironment.Light).toArgb(),
        )
    }

    @И("краски иконок запуска, заставки и уведомления совпадают с токенами палитры знака")
    fun theIconColoursRepeatThePalette() {
        val resources = File(repositoryRoot(), ICON_COLOURS).readText()
        val inkOnDark = hex6(SklyankaMarkColors.InkOnDark)
        val inkOnLight = hex6(SklyankaMarkColors.InkOnLight)
        val amber = hex6(SklyankaMarkColors.Amber)
        val plain = mapOf(
            "mark_tile_dark" to SklyankaMarkColors.TileDark,
            "mark_tile_light" to SklyankaMarkColors.TileLight,
            "mark_ink_on_dark" to SklyankaMarkColors.InkOnDark,
            "mark_ink_on_light" to SklyankaMarkColors.InkOnLight,
            "mark_accent" to SklyankaMarkColors.Amber,
        )
        val withAlpha = mapOf(
            "mark_body_dark_0" to "3D$inkOnDark",
            "mark_body_dark_1" to "12$inkOnDark",
            "mark_body_dark_2" to "04$inkOnDark",
            "mark_rim_dark_0" to "D9$inkOnDark",
            "mark_rim_dark_1" to "29$inkOnDark",
            "mark_rim_dark_2" to "6B$inkOnDark",
            "mark_body_light_2" to "0F$inkOnLight",
            "mark_rim_light_0" to "29$inkOnLight",
            "mark_rim_light_1" to "80$inkOnLight",
            "mark_rim_light_2" to "38$inkOnLight",
            "mark_caustic_dark" to "9E$amber",
            "mark_caustic_light" to "8C$amber",
            "mark_caustic_clear" to "00$amber",
        )
        val wrong = plain.filterNot { (name, colour) ->
            resources.contains("name=\"$name\">${hex(colour)}<")
        }.keys + withAlpha.filterNot { (name, value) ->
            resources.contains("name=\"$name\">#$value<")
        }.keys
        assertTrue(
            "the icon resources do not repeat the mark palette: $wrong",
            wrong.isEmpty(),
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun roles(): SklyankaColors = when {
        dark -> darkColors()
        foreign -> tintedColors(lightColors(), FOREIGN_PROFILE.color)
        else -> lightColors()
    }

    private fun assertContrast(foreground: Color, background: Color, pair: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$pair is $ratio, below $AA_NORMAL", ratio >= AA_NORMAL)
    }

    /** Девять ролей, которые Р-20 и вывод из токена переписали (§2.1 норматива). */
    private fun theNineChangedValues(): List<Color> = listOf(
        Palette.Background,
        Palette.Accent,
        Palette.AccentSoft,
        Palette.AccentBorder,
        Palette.OnAccentSoft,
        Palette.DarkBackground,
        Palette.AccentOnDark,
        Palette.AccentSoftOnDark,
        Palette.ProfileOlive,
    )

    private fun repositoryRoot(): File = File(
        requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: the screen test convention must be applied to this module"
        },
    )

    private fun channels(colour: Color): List<Int> = listOf(red(colour), green(colour), blue(colour))

    private fun red(colour: Color): Int = colour.toArgb() shr SHIFT_RED and CHANNEL_MASK

    private fun green(colour: Color): Int = colour.toArgb() shr SHIFT_GREEN and CHANNEL_MASK

    private fun blue(colour: Color): Int = colour.toArgb() and CHANNEL_MASK

    private fun rgb(colour: Color): Int = colour.toArgb() and RGB_MASK

    private fun hex(colour: Color): String = "#${hex6(colour)}"

    /** Шесть знаков без решётки: так записаны старшие байты ресурсов с прозрачностью. */
    private fun hex6(colour: Color): String = "%06X".format(rgb(colour))

    private companion object {
        const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
        const val NORMATIVE_DOCUMENT = "docs/v2/12-design.md"
        const val ICON_COLOURS = "app/src/main/res/values/colors.xml"

        /** Профиль, которым живёт «чужой» контекст сценариев: любая пара набора, кроме акцента. */
        val FOREIGN_PROFILE = ProfileColor.Plum

        /** Прежний основной акцент: решение Р-20 сняло его вместе с терракотовой палитрой (V-11). */
        const val TERRACOTTA = 0xB4512F

        const val WHITE = 0xFFFFFFFF.toInt()
        const val RGB_MASK = 0xFFFFFF
        const val CHANNEL_MASK = 0xFF
        const val SHIFT_RED = 16
        const val SHIFT_GREEN = 8

        /** Канал цвета — восемь бит: объявленная доля возвращается с точностью 1/255. */
        const val CHANNEL_ROUNDING = 1f / 255f
    }
}
