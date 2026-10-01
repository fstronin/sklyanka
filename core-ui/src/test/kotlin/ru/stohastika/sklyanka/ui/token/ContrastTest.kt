package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контраст пар из `docs/spec/08-design-system.md` §6.1 (НФТ-32, критерий 7).
 *
 * Тест сверяет расчёт по токенам с таблицей документа. Он падает и когда контраст
 * ухудшился, и когда исключение внезапно «исправили»: второе означает, что код и
 * документ разошлись, а разошедшийся документ перестаёт быть источником.
 */
class ContrastTest {

    private val light = lightColors()
    private val dark = darkColors()

    @Test
    fun `contrast pairs match the design system table`() {
        val failures = TABLE.filter { pair ->
            val actual = contrastRatio(pair.foreground, pair.background)
            kotlin.math.abs(actual - pair.expected) > TABLE_TOLERANCE
        }
        assertTrue(
            failures.joinToString("\n") { pair ->
                "${pair.name}: expected ${pair.expected}, " +
                    "actual ${"%.2f".format(contrastRatio(pair.foreground, pair.background))}"
            },
            failures.isEmpty(),
        )
    }

    @Test
    fun `text accent and profile colour pass AA for normal text`() {
        val pairs = mapOf(
            "text on background" to contrastRatio(light.text, light.background),
            "text on surface" to contrastRatio(light.text, light.surface),
            "muted on background" to contrastRatio(light.textMuted, light.background),
            "muted on surface" to contrastRatio(light.textMuted, light.surface),
            "accent on background" to contrastRatio(light.accent, light.background),
            "accent on surface" to contrastRatio(light.accent, light.surface),
            "surface on accent" to contrastRatio(light.surface, light.accent),
            "onAccent on accent" to contrastRatio(light.onAccent, light.accent),
            "surface on danger" to contrastRatio(light.surface, light.danger),
            "taken on surface" to contrastRatio(light.taken, light.surface),
            "taken on takenSoft" to contrastRatio(light.taken, light.takenSoft),
            "warning on background" to contrastRatio(light.warning, light.background),
            "warning on accentSoft" to contrastRatio(light.warning, light.accentSoft),
            "banner text on accentSoft" to contrastRatio(light.onAccentSoft, light.accentSoft),
            "muted on archive" to contrastRatio(light.textMuted, light.archiveBackground),
        )
        pairs.forEach { (name, ratio) ->
            assertTrue("$name: $ratio < $AA_NORMAL", ratio >= AA_NORMAL)
        }
    }

    @Test
    fun `every profile colour passes AA on the background it tints`() {
        ProfilePalette.all.forEach { profile ->
            val tinted = tintedColors(light, profile.color)
            val onBackground = contrastRatio(tinted.accent, tinted.background)
            assertTrue(
                "${profile.name}: accent on tinted background is $onBackground",
                onBackground >= AA_NORMAL,
            )
            val onSurface = contrastRatio(tinted.accent, tinted.surface)
            assertTrue("${profile.name}: accent on surface is $onSurface", onSurface >= AA_NORMAL)
        }
    }

    /**
     * Три исключения §6.1 закреплены именно как исключения.
     *
     * Если однажды они пройдут норму, тест упадёт — и это правильно: значит, токен
     * поменяли, а документ остался прежним.
     */
    @Test
    fun `the three declared exceptions stay exceptions`() {
        assertTrue(
            "TextTertiary on background must stay below AA for normal text",
            contrastRatio(light.textTertiary, light.background) < AA_NORMAL,
        )
        assertTrue(
            "TextTertiary on background must still pass AA for large text",
            contrastRatio(light.textTertiary, light.background) >= AA_LARGE,
        )
        assertTrue(
            "TextTertiary on surface must stay below AA for normal text",
            contrastRatio(light.textTertiary, light.surface) < AA_NORMAL,
        )
        assertTrue(
            "TextDisabled on surface must stay below AA even for large text",
            contrastRatio(light.textDisabled, light.surface) < AA_LARGE,
        )
        assertTrue(
            "IconFaint is decorative only and must not reach AA for normal text",
            contrastRatio(light.iconFaint, light.surface) < AA_NORMAL,
        )
    }

    /**
     * §6.1, вывод 4: основной акцент в тёмной среде на тексте не применяется.
     *
     * Проверяется не запретом, а подменой: в тёмном наборе роль `accent` — это уже
     * `AccentOnDark`, и применить `#B4512F` в тёмной среде неоткуда.
     */
    @Test
    fun `dark environment replaces the light accent`() {
        assertNotEquals(light.accent, dark.accent)
        assertEquals(Palette.AccentOnDark, dark.accent)
        assertTrue(
            "AccentOnDark on dark background must pass AA for normal text",
            contrastRatio(dark.accent, dark.background) >= AA_NORMAL,
        )
        assertTrue(
            "The light accent on the dark background is exactly why it is replaced",
            contrastRatio(light.accent, dark.background) < AA_NORMAL,
        )
    }

    /**
     * Подпись акцентной кнопки читается в обеих средах (ОВ-90).
     *
     * Светлая пара — [Palette.Surface] на [Palette.Accent] (5,05). В тёмной среде
     * подпись набирается фоном тёмной среды: поверхность на `AccentOnDark` даёт 3,44
     * и не проходит AA — ровно поэтому роль `onAccent` и заведена.
     */
    @Test
    fun `the accent button caption passes AA in both environments`() {
        assertTrue(
            "onAccent on accent (light): ${contrastRatio(light.onAccent, light.accent)}",
            contrastRatio(light.onAccent, light.accent) >= AA_NORMAL,
        )
        assertTrue(
            "onAccent on accent (dark): ${contrastRatio(dark.onAccent, dark.accent)}",
            contrastRatio(dark.onAccent, dark.accent) >= AA_NORMAL,
        )
        assertTrue(
            "Surface on AccentOnDark must stay below AA — that is why onAccent is dark",
            contrastRatio(Palette.Surface, Palette.AccentOnDark) < AA_NORMAL,
        )
    }

    @Test
    fun `dark text stays readable`() {
        assertTrue(contrastRatio(dark.text, dark.background) >= AA_NORMAL)
        assertTrue(contrastRatio(dark.textMuted, dark.background) >= AA_NORMAL)
        assertTrue(contrastRatio(dark.text, dark.surface) >= AA_NORMAL)
    }

    /**
     * Шапка экрана в тёмной среде (SKL-25, дефект 2).
     *
     * Фон шапки — роль `profileHeader`, а не `background`: у владельца подкраски нет,
     * и роль совпадает с фоном. Пока она оставалась светлой в тёмном наборе, шапка
     * была светлой, а заголовок поверх неё — цветом фона: контраст ровно 1,00, то
     * есть заголовка не видно (НФТ-32).
     */
    @Test
    fun `the screen header stays readable in the dark environment`() {
        assertTrue(
            "the title on the dark header is ${contrastRatio(dark.text, dark.profileHeader)}",
            contrastRatio(dark.text, dark.profileHeader) >= AA_NORMAL,
        )
        assertTrue(
            "the overline on the dark header is ${contrastRatio(dark.textMuted, dark.profileHeader)}",
            contrastRatio(dark.textMuted, dark.profileHeader) >= AA_NORMAL,
        )
    }

    private data class Pair(val name: String, val foreground: Color, val background: Color, val expected: Double)

    private companion object {

        /**
         * Таблица §6.1 целиком, за вычетом двух архивных строк: цвета `#4A433C` и
         * `#6E655C` помечены в §1.2 производными и токенами не являются — сверять
         * по ним нечего, архивная пара проверяется отдельно требованием AA.
         *
         * Строки «напоминания» считаются на фоне `#241F1B` — том самом значении,
         * которое макет `Reminder.dc.html` использует вместо `DarkBackground`
         * (ОВ-9). Совпадение с токеном `Text` здесь не случайность, а суть вопроса.
         */
        val TABLE = listOf(
            Pair("text on background", Palette.Text, Palette.Background, 15.29),
            Pair("text on surface", Palette.Text, Palette.Surface, 16.32),
            Pair("muted on background", Palette.TextMuted, Palette.Background, 6.73),
            Pair("muted on surface", Palette.TextMuted, Palette.Surface, 7.18),
            Pair("accent on background", Palette.Accent, Palette.Background, 4.73),
            Pair("accent on surface", Palette.Accent, Palette.Surface, 5.05),
            Pair("surface on accent", Palette.Surface, Palette.Accent, 5.05),
            Pair("onAccent on accentOnDark", Palette.DarkBackground, Palette.AccentOnDark, 4.61),
            Pair("surface on danger", Palette.Surface, Palette.ProfileForeign, 7.29),
            Pair("taken on surface", Palette.Taken, Palette.Surface, 7.22),
            Pair("taken on takenSoft", Palette.Taken, Palette.TakenSoft, 6.10),
            Pair("profile on background", Palette.ProfileForeign, Palette.Background, 6.83),
            Pair("profile on accentSoft", Palette.ProfileForeign, Palette.AccentSoft, 6.04),
            Pair("onAccentSoft on accentSoft", Palette.OnAccentSoft, Palette.AccentSoft, 8.01),
            Pair("tertiary on background", Palette.TextTertiary, Palette.Background, 3.56),
            Pair("tertiary on surface", Palette.TextTertiary, Palette.Surface, 3.80),
            Pair("disabled on surface", Palette.TextDisabled, Palette.Surface, 2.69),
            Pair("dark text on dark background", Palette.Background, Palette.DarkBackground, 17.00),
            Pair("accentOnDark on dark background", Palette.AccentOnDark, Palette.DarkBackground, 4.61),
            Pair("light accent on dark background", Palette.Accent, Palette.DarkBackground, 3.59),
            Pair("reminder: textOnDark", Palette.TextOnDark, Palette.Text, 12.04),
            Pair("reminder: missed", Palette.Missed, Palette.Text, 8.99),
            Pair("reminder: disabled", Palette.TextDisabled, Palette.Text, 6.06),
            Pair("line on background", Palette.Line, Palette.Background, 1.25),
            Pair("lineOnSurface on surface", Palette.LineOnSurface, Palette.Surface, 1.23),
            Pair("lineInteractive on surface", Palette.LineInteractive, Palette.Surface, 1.57),
        )
    }
}
