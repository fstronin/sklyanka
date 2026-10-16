package ru.stohastika.sklyanka.acceptance.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.ui.token.AA_NORMAL
import ru.stohastika.sklyanka.ui.token.DARK_INHERITED_ROLES
import ru.stohastika.sklyanka.ui.token.DARK_OVERRIDDEN_ROLES
import ru.stohastika.sklyanka.ui.token.Palette
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.TABLE_TOLERANCE
import ru.stohastika.sklyanka.ui.token.contrastRatio
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.relativeLuminance
import ru.stohastika.sklyanka.ui.token.tintedColors

/**
 * Экранные шаги приёмки SKL-120 — тёмные роли, контрасты и палитра профилей (§9, §2.5, §2.2).
 *
 * Шаги живут в `core-ui`, потому что говорят о **токенах дизайн-системы**: и краски тем,
 * и тёмный набор, и палитра профилей объявлены здесь, и проверять их надо там же, где они
 * живут (тот же приём, что у палитры Р-20 в SKL-109). Контрасты считаются настоящим
 * [contrastRatio], а не пересказом формулы: числа нормы §2.5 сверяются со значениями
 * продукта, и расхождение обязано находить приложение, а не глаз на ревью.
 *
 * **Предыстория файла объявлена здесь заглушкой.** Модуль не видит общего артефакта приёмки
 * (SKL-93: `:acceptance` — чистый JVM и в `core-ui` не приезжает), а Предыстория сценария
 * исполняется для каждого его сценария — иначе Cucumber счёл бы шаг нереализованным.
 * Так же поступает стенд дизайн-системы SKL-24 (`ScreenStandSteps` дублирует строки
 * Предыстории): заглушка называет причину вслух, а не молчит.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class Sk120TokenSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Допустим("^активен профиль «Я»$")
    fun theActiveProfileIs() {
        // Заглушка Предыстории: токенам не нужен профиль сценария, а модуль не видит мира
        // приёмки. Сценарии этой группы говорят о красках, а не о человеке с лекарствами.
        assertTrue("the token stand must run on the imitation of Android", context.packageName.isNotEmpty())
    }

    @Когда("^на стенде токенов собраны все четыре темы в обеих средах$")
    fun everyThemeIsBuiltInBothEnvironments() {
        assertTrue("four accent themes are expected (§2.5)", AccentTheme.entries.size == FOUR_THEMES)
    }

    @Тогда("^на стенде тёмная среда переопределяет каждую роль, что встречается на экранах$")
    fun theDarkEnvironmentOverridesEveryRoleOfTheScreens() {
        AccentTheme.entries.forEach { theme ->
            val light = lightColors(theme)
            val dark = darkColors(theme)
            DARK_OVERRIDDEN_ROLES.forEach { role ->
                assertNotEquals(
                    "the dark environment must override $role (§9)",
                    roleOf(light, role),
                    roleOf(dark, role),
                )
            }
            DARK_INHERITED_ROLES.forEach { role ->
                assertEquals("the dark environment must inherit $role (§9)", roleOf(light, role), roleOf(dark, role))
            }
        }
    }

    @Тогда("^на стенде фон тёмной среды — тёмная олива нормы$")
    fun theDarkBackgroundIsTheNormativeOlive() {
        assertEquals(
            "the dark background must be the normative token (§9)",
            Palette.DarkBackground,
            darkColors().background,
        )
        assertEquals("the dark background is #181712 (§9)", NIGHT_BACKGROUND, darkColors().background.toArgb())
    }

    @Тогда("^на стенде акцент каждой темы в тёмной среде светлее её светлого$")
    fun theDarkAccentOfEveryThemeIsLighter() {
        AccentTheme.entries.forEach { theme ->
            assertTrue(
                "the dark accent of «${theme.key}» must be lighter than its light one (§2.5)",
                relativeLuminance(darkColors(theme).accent) > relativeLuminance(lightColors(theme).accent),
            )
        }
    }

    @Тогда("^на стенде акцент каждой темы читается и на фоне, и на поверхности$")
    fun theAccentOfEveryThemeIsReadable() {
        AccentTheme.entries.forEach { theme ->
            val light = lightColors(theme)
            val dark = darkColors(theme)
            assertAtLeast(light.accent, light.background, "accent on the light background of «${theme.key}»")
            assertAtLeast(light.accent, light.surface, "accent on the light surface of «${theme.key}»")
            assertAtLeast(light.onAccent, light.accent, "the caption on the accent of «${theme.key}»")
            assertAtLeast(light.onAccentSoft, light.accentSoft, "the caption on the soft accent of «${theme.key}»")
            assertAtLeast(dark.accent, dark.background, "the dark accent of «${theme.key}» on its background")
            assertAtLeast(dark.onAccentSoft, dark.accentSoft, "the caption on the dark soft accent of «${theme.key}»")
        }
    }

    @Тогда("^на стенде самая тесная пара набора проходит порог с запасом$")
    fun theTightestPairOfTheSetPassesWithRoom() {
        // Самая тесная пара набора — базовая «олива» (5,11 по §2.5), и запас — не меньше 0,5:
        // «олива» закреплена решением Р-20, и порог она проходит не впритык.
        val tightest = AccentTheme.entries.map { theme ->
            contrastRatio(lightColors(theme).accent, lightColors(theme).background)
        }.min()
        assertEquals(
            "the base olive keeps the published number (§2.5)",
            BASE_ACCENT_ON_BACKGROUND,
            tightest,
            TABLE_TOLERANCE,
        )
        assertTrue("the tightest pair of the set must pass AA with room, was $tightest", tightest >= AA_NORMAL + ROOM)
    }

    @Тогда("^на стенде семья акцента чужого профиля — цвет профиля при любой теме$")
    fun theForeignAccentFamilyIsTheProfileColour() {
        val foreign = ProfilePalette.all.first()
        AccentTheme.entries.forEach { theme ->
            val tinted = tintedColors(lightColors(theme), foreign.color, dark = false)
            assertEquals(
                "the foreign accent is the profile colour, not the theme one (§2.2)",
                foreign.color,
                tinted.accent,
            )
            assertEquals("the stripe of a foreign profile is its colour (§2.2)", foreign.color, tinted.profileStripe)
            assertNotEquals(
                "the theme accent must not survive in a foreign profile",
                lightColors(theme).accent,
                tinted.accent,
            )
        }
    }

    @Тогда("^на стенде палитра профилей ни одной темой не тронута$")
    fun theProfilePaletteIsUntouched() {
        AccentTheme.entries.forEach { theme ->
            ProfilePalette.all.forEach { colour ->
                assertNotEquals(
                    "no theme may repeat a profile colour: «${colour.name}» under «${theme.key}» (§2.2)",
                    lightColors(theme).accent,
                    colour.color,
                )
                assertNotEquals(darkColors(theme).accent, colour.color)
            }
        }
    }

    /** Порог §6.1 для всех пар нормы — AA обычного текста. */
    private fun assertAtLeast(foreground: Color, background: Color, pair: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$pair must pass AA ($AA_NORMAL), was $ratio", ratio >= AA_NORMAL)
    }

    /**
     * Значение роли по её имени из списка §9.
     *
     * По имени, а не по списку пар «роль — поле»: список ролей тёмной среды объявлен
     * перечислением строк (§9), и второй перечень тех же ролей разошёлся бы с первым.
     * Поле, забытое здесь, валит проверку — это и есть её смысл.
     */
    private fun roleOf(colors: SklyankaColors, name: String): Color = when (name) {
        "background" -> colors.background
        "surface" -> colors.surface
        "text" -> colors.text
        "textMuted" -> colors.textMuted
        "textTertiary" -> colors.textTertiary
        "textDisabled" -> colors.textDisabled
        "line" -> colors.line
        "lineOnSurface" -> colors.lineOnSurface
        "lineInteractive" -> colors.lineInteractive
        "iconFaint" -> colors.iconFaint
        "accent" -> colors.accent
        "accentSoft" -> colors.accentSoft
        "accentBorder" -> colors.accentBorder
        "onAccent" -> colors.onAccent
        "onAccentSoft" -> colors.onAccentSoft
        "textOnDark" -> colors.textOnDark
        "taken" -> colors.taken
        "takenSoft" -> colors.takenSoft
        "missed" -> colors.missed
        "takenPartial" -> colors.takenPartial
        "warning" -> colors.warning
        "warningOnSoft" -> colors.warningOnSoft
        "danger" -> colors.danger
        "neutralSoft" -> colors.neutralSoft
        "progressTrack" -> colors.progressTrack
        "switchOff" -> colors.switchOff
        "archiveBackground" -> colors.archiveBackground
        "archiveText" -> colors.archiveText
        "profileStripe" -> colors.profileStripe
        "profileBackground" -> colors.profileBackground
        "profileHeader" -> colors.profileHeader
        "scrim" -> colors.scrim
        else -> throw IllegalArgumentException("${colors::class.simpleName} has no role «$name»")
    }

    private companion object {
        const val FOUR_THEMES = 4

        /** #181712 — тёмная олива нормы (§9): значение, а не свойство. */
        const val NIGHT_BACKGROUND = 0xFF181712.toInt()

        /** Самая тесная пара набора — акцент «Оливы» на фоне (§2.5). */
        const val BASE_ACCENT_ON_BACKGROUND = 5.11

        /** Запас к порогу AA, который норма называет для тесной пары. */
        const val ROOM = 0.5
    }
}

/**
 * Экранный прогон приёмки SKL-120 — токены тем, тёмной среды и палитры профилей.
 *
 * Прогон свой, а не общий `ScreenAcceptanceRun`: тот живёт в модуле приёмки, которого
 * `core-ui` не видит (тем же путём идёт стенд палитры SKL-109). Группа
 * `@screen and @tokens` отсекает сценарии других стендов — настроек, иконки, плиты
 * и документа: у них свои двери.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk120TokenAcceptanceTest {

    @Test
    fun theTokenScenariosOfSk120RunOnTheStand() {
        assertNull(Sk120TokenRun.run())
    }
}

/** Прогон группы токенов SKL-120 внутри модуля `core-ui`. */
internal object Sk120TokenRun {

    private const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"

    private const val FEATURE_PATH = "acceptance/src/main/resources/features/SKL-120.feature"

    private const val GLUE = "ru.stohastika.sklyanka.acceptance"

    private const val TAGS = "@screen and @tokens"

    /** @return `null`, если прогон прошёл; иначе — сообщение об отказе */
    fun run(): String? {
        val root = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: the screen test convention must be applied to this module"
        }
        val feature = java.io.File(root, FEATURE_PATH)
        require(feature.isFile) { "the feature file is missing: ${feature.path}" }
        val exitCode = io.cucumber.core.cli.Main.run(
            "--glue",
            GLUE,
            "--tags",
            TAGS,
            "--plugin",
            "progress",
            feature.absolutePath,
        )
        return if (exitCode.toInt() == 0) null else "the $TAGS scenarios of SKL-120 did not pass on the token stand"
    }
}
