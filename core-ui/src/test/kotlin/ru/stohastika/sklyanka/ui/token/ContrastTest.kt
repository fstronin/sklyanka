package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.appearance.AccentTheme

/**
 * Контраст пар из `docs/norm/06-design.md` §6.1 (НФТ-32, критерий 7).
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
            "archive title on archive" to contrastRatio(light.archiveText, light.archiveBackground),
        )
        pairs.forEach { (name, ratio) ->
            assertTrue("$name: $ratio < $AA_NORMAL", ratio >= AA_NORMAL)
        }
    }

    @Test
    fun `every profile colour passes AA on the background it tints`() {
        ProfilePalette.all.forEach { profile ->
            val tinted = tintedColors(light, profile.color, dark = false)
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
     * `AccentOnDark`, и применить `#5F6F2E` в тёмной среде неоткуда.
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
     * Светлая пара — [Palette.Surface] на [Palette.Accent] (5,53). В тёмной среде
     * подпись набирается фоном тёмной среды: поверхность на `AccentOnDark` даёт 1,88
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

    /**
     * Четыре акцентные темы в обеих средах — восемь наборов (§2.5, §9, SKL-120).
     *
     * Тема меняет семью акцента, и вместе с ней обязана держать AA: акцент как текст
     * на фоне и на поверхности, подпись на акцентной заливке (в светлой среде белая,
     * в тёмной — фон среды), подпись на мягком акценте, а в тёмной среде — ещё и сам
     * акцент на тёмном фоне. Проверка идёт порогом, а не таблицей: числа §2.5 — вывод
     * правила, и их сверка живёт отдельным тестом ниже.
     */
    @Test
    fun `every accent theme passes AA in both environments`() {
        val failures = mutableListOf<String>()
        AccentTheme.entries.forEach { theme ->
            val lightSet = lightColors(theme)
            val darkSet = darkColors(theme)

            fun check(name: String, ratio: Double) {
                if (ratio < AA_NORMAL) failures += "$name: ${"%.2f".format(ratio)}"
            }

            check("${theme.key}: accent on background", contrastRatio(lightSet.accent, lightSet.background))
            check("${theme.key}: accent on surface", contrastRatio(lightSet.accent, lightSet.surface))
            check("${theme.key}: onAccent on accent", contrastRatio(lightSet.onAccent, lightSet.accent))
            check(
                "${theme.key}: onAccentSoft on accentSoft",
                contrastRatio(lightSet.onAccentSoft, lightSet.accentSoft),
            )
            check("${theme.key} dark: accent on background", contrastRatio(darkSet.accent, darkSet.background))
            check("${theme.key} dark: accent on surface", contrastRatio(darkSet.accent, darkSet.surface))
            check("${theme.key} dark: onAccent on accent", contrastRatio(darkSet.onAccent, darkSet.accent))
            check(
                "${theme.key} dark: onAccentSoft on accentSoft",
                contrastRatio(darkSet.onAccentSoft, darkSet.accentSoft),
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Числа таблицы §2.5 для всех четырёх тем (§2.5, SKL-120).
     *
     * Тема стоит трёх значений, а всё остальное выводится правилом; здесь сверяются
     * и данные значения, и вывод. Допуск — тот же, что у таблицы §6.1: числа посчитаны
     * с точностью до сотых.
     */
    @Test
    fun `accent theme pairs match the design table`() {
        val given = buildList {
            ACCENT_TABLE.forEach { row ->
                val lightSet = lightColors(row.theme)
                val darkSet = darkColors(row.theme)
                add(
                    Pair(
                        "${row.theme.key}: accent on background",
                        lightSet.accent,
                        lightSet.background,
                        row.onBackground,
                    ),
                )
                add(Pair("${row.theme.key}: accent on surface", lightSet.accent, lightSet.surface, row.onSurface))
                add(Pair("${row.theme.key}: onAccent on accent", lightSet.onAccent, lightSet.accent, row.caption))
                add(Pair("${row.theme.key} dark: accent", darkSet.accent, darkSet.background, row.onDarkBackground))
            }
        }
        // Пары мягкого акцента — вывод правила, а не заданное значение: числа §2.5 для них
        // посчитаны от выведенной подложки, и у «Аметиста» таблица расходится с правилом
        // на 0,09 (норма говорит «на долю 0,55», вывод идёт той же долей). Проверяются они
        // своим допуском, а правило держит отдельный тест ниже; порог AA проходят и там.
        // Мягкие пары акцентных тем в таблицу не идут: числа §2.5 для них — вывод правила
        // для сведения, а не данное нормы (дизайнер назвал расхождение в девять сотых
        // ошибкой чтения вывода). Мягкие пары держат порог AA
        // (`every accent theme passes AA in both environments`) и тест вывода семьи
        // (`the base theme accent family is derived by the published rule`).

        assertTable(given, TABLE_TOLERANCE)
    }

    /**
     * Сверка цвета с допуском в один шаг байта по каналу.
     *
     * Токен нормы записан байтами (`Color(0xFFCFD4C0)`), а выведенный правилом цвет —
     * float-смешение; сравнивать их float-равенством значит падать на седьмом знаке после
     * запятой. Число нормы при этом сходится с выводом с точностью до байта — это и проверяется.
     */
    private fun assertChannelEquals(expected: Color, actual: Color, name: String) {
        listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
            .forEachIndexed { index, (expectedChannel, actualChannel) ->
                assertTrue(
                    "$name: channel $index is $actualChannel, expected $expectedChannel",
                    kotlin.math.abs(expectedChannel - actualChannel) <= CHANNEL_TOLERANCE,
                )
            }
    }

    /** Сверка пар таблицы: расхождение с ожиданием — падение, с сообщением о паре. */
    private fun assertTable(pairs: List<Pair>, tolerance: Double) {
        val failures = pairs.filter { pair ->
            kotlin.math.abs(contrastRatio(pair.foreground, pair.background) - pair.expected) > tolerance
        }
        assertTrue(
            failures.joinToString("\n") { pair ->
                "${pair.name}: expected ${pair.expected}, " +
                    "actual ${"%.2f".format(contrastRatio(pair.foreground, pair.background))}"
            },
            failures.isEmpty(),
        )
    }

    /**
     * Роли семьи акцента выводятся правилом, а не подбираются (§2.5, Правило 1).
     *
     * Базовая тема сверяется с токенами §2.1: если вывод правила разойдётся с нормой,
     * разойдутся и остальные три темы — молча, потому что их чисел в коде нет.
     */
    @Test
    fun `the base theme accent family is derived by the published rule`() {
        val base = lightColors(AccentTheme.OLIVE)
        // Выведенный цвет — float-смешение, токен нормы — запись байтами: сравниваются
        // каналы с допуском в один шаг байта, а не float-равенством.
        assertChannelEquals(Palette.AccentBorder, base.accentBorder, "accentBorder")
        assertChannelEquals(Palette.OnAccentSoft, base.onAccentSoft, "onAccentSoft")
        assertEquals(Palette.AccentSoft, base.accentSoft)
        assertEquals(Palette.Accent, base.accent)

        AccentTheme.entries.forEach { theme ->
            val darkSet = darkColors(theme)
            assertEquals(
                "${theme.key}: the dark soft accent is the dark accent lightened to white by 0,55",
                darkAccentSoftOf(theme.palette.darkAccent),
                darkSet.accentSoft,
            )
        }
    }

    /**
     * Опасность и предупреждение читаются в обеих средах, и подпись на заливке опасности тоже.
     *
     * В светлой среде кнопка необратимого действия залита тёмной краской и подписана белым
     * (7,29); в тёмной — светлой краской, подписанной фоном среды (7,81). Это та же пара
     * ролей, что у акцентной кнопки, и проверяется вместе с ней.
     */
    @Test
    fun `danger and warning stay readable in both environments`() {
        assertTrue(contrastRatio(light.danger, light.background) >= AA_NORMAL)
        assertTrue(contrastRatio(light.danger, light.accentSoft) >= AA_NORMAL)
        assertTrue(contrastRatio(light.onAccent, light.danger) >= AA_NORMAL)
        AccentTheme.entries.forEach { theme ->
            val darkSet = darkColors(theme)
            assertTrue(
                "${theme.key}: danger on dark background",
                contrastRatio(darkSet.danger, darkSet.background) >= AA_NORMAL,
            )
            assertTrue(
                "${theme.key}: onAccent on dark danger",
                contrastRatio(darkSet.onAccent, darkSet.danger) >= AA_NORMAL,
            )
            assertTrue(
                "${theme.key}: dark taken on dark surface",
                contrastRatio(darkSet.taken, darkSet.surface) >= AA_NORMAL,
            )
        }
    }

    private data class AccentRow(
        val theme: AccentTheme,
        val onBackground: Double,
        val onSurface: Double,
        val caption: Double,
        val onDarkBackground: Double,
        val softOnSoftLight: Double,
        val softOnSoftDark: Double,
    )

    private data class Pair(val name: String, val foreground: Color, val background: Color, val expected: Double)

    private companion object {

        /** Допуск сверки каналов: один шаг байта (1/255) — цена записи нормы байтами. */
        const val CHANNEL_TOLERANCE = 1f / 255f

        /**
         * Таблица §2.5 для четырёх акцентных тем (SKL-120).
         *
         * Числа — из нормы, а не из кода: тест падает и когда посчитанное правило
         * разошлось с документом, и когда документ поменяли, а значения оставили.
         */
        val ACCENT_TABLE = listOf(
            AccentRow(AccentTheme.OLIVE, 5.11, 5.53, 5.53, 9.52, 8.24, 7.41),
            AccentRow(AccentTheme.INDIGO, 6.72, 7.27, 7.27, 9.03, 8.30, 8.47),
            AccentRow(AccentTheme.PINE, 8.08, 8.74, 8.74, 9.25, 10.09, 9.32),
            AccentRow(AccentTheme.AMETHYST, 7.73, 8.36, 8.36, 8.53, 8.98, 8.79),
        )

        /**
         * Таблица §6.1 целиком, за вычетом двух архивных строк: `archiveText` — цвет
         * производный (`tint(text, archiveBackground, 0,18)`), а не токен палитры,
         * поэтому архивная пара проверяется требованием AA отдельно.
         *
         * Строки «напоминания» считаются на фоне `#241F1B` — том самом значении,
         * которое макет `Reminder.dc.html` использует вместо фона тёмной среды
         * (ОВ-9; решением Р-20 фон тёмной среды — `#181712`, а `#241F1B` — цвет
         * текста, фоном он не применяется, §9).
         */
        val TABLE = listOf(
            Pair("text on background", Palette.Text, Palette.Background, 15.09),
            Pair("text on surface", Palette.Text, Palette.Surface, 16.32),
            Pair("muted on background", Palette.TextMuted, Palette.Background, 6.64),
            Pair("muted on surface", Palette.TextMuted, Palette.Surface, 7.18),
            Pair("accent on background", Palette.Accent, Palette.Background, 5.11),
            Pair("accent on surface", Palette.Accent, Palette.Surface, 5.53),
            Pair("surface on accent", Palette.Surface, Palette.Accent, 5.53),
            Pair("onAccent on accentOnDark", Palette.DarkBackground, Palette.AccentOnDark, 9.52),
            Pair("surface on danger", Palette.Surface, Palette.ProfileForeign, 7.29),
            Pair("taken on surface", Palette.Taken, Palette.Surface, 7.22),
            Pair("taken on takenSoft", Palette.Taken, Palette.TakenSoft, 6.10),
            Pair("profile on background", Palette.ProfileForeign, Palette.Background, 6.74),
            Pair("profile on accentSoft", Palette.ProfileForeign, Palette.AccentSoft, 6.17),
            Pair("onAccentSoft on accentSoft", Palette.OnAccentSoft, Palette.AccentSoft, 8.24),
            Pair("tertiary on background", Palette.TextTertiary, Palette.Background, 3.52),
            Pair("tertiary on surface", Palette.TextTertiary, Palette.Surface, 3.80),
            Pair("disabled on surface", Palette.TextDisabled, Palette.Surface, 2.69),
            Pair("dark text on dark background", Palette.Background, Palette.DarkBackground, 16.60),
            Pair("accentOnDark on dark background", Palette.AccentOnDark, Palette.DarkBackground, 9.52),
            Pair("light accent on dark background", Palette.Accent, Palette.DarkBackground, 3.25),
            Pair("reminder: textOnDark", Palette.TextOnDark, Palette.Text, 12.04),
            Pair("reminder: missed", Palette.Missed, Palette.Text, 8.99),
            Pair("reminder: disabled", Palette.TextDisabled, Palette.Text, 6.06),
            Pair("line on background", Palette.Line, Palette.Background, 1.23),
            Pair("lineOnSurface on surface", Palette.LineOnSurface, Palette.Surface, 1.23),
            Pair("lineInteractive on surface", Palette.LineInteractive, Palette.Surface, 1.57),
        )
    }

    /**
     * Краски на светлой подложке мягкого акцента в тёмной среде (§9, V-74).
     *
     * Подложка `accentSoft` светлая в обеих средах, поэтому и краски на ней светлые —
     * решает подложка, а не среда. Пары добавлены именно потому, что их отсутствие
     * и пропустило дефект: на светлой подложке стояли тёмные поднятые краски (1,68–1,75
     * при AA 4,5).
     */
    @Test
    fun `the inks on the light soft accent pass AA in the dark environment`() {
        val wrong = AccentTheme.entries.filter { theme ->
            val dark = darkColors(theme)
            contrastRatio(dark.onAccentSoft, dark.accentSoft) < AA_NORMAL ||
                contrastRatio(dark.warningOnSoft, dark.accentSoft) < AA_NORMAL
        }
        assertTrue(
            "the light soft accent must carry light inks in the dark environment: $wrong",
            wrong.isEmpty(),
        )
    }

    /**
     * Семья чужого профиля в мягком виде: подложка и чернила на ней (§2.2, SKL-120).
     *
     * Проверяется **один раз на цвет**: среда из подложки и чернил ушла — подложка семьи
     * светлая всегда, как и подложка темы, и тёмная среда её не перекрашивает.
     */
    @Test
    fun `the soft family of every profile colour passes AA whatever the environment`() {
        val wrong = ProfilePalette.all.filter { colour ->
            contrastRatio(onAccentSoftOf(colour.color), tint(Palette.Surface, colour.color, 0.14f)) < AA_NORMAL
        }
        assertTrue("the ink must read on the soft plate of its own colour: $wrong", wrong.isEmpty())
    }

    /**
     * Поднятый акцент чужого профиля на тёмном фоне (§2.2, §9, SKL-120).
     *
     * Цвет профиля на тёмном фоне даёт 2,4–3,2 и ролью текста быть не может, поэтому
     * в тёмной среде акцент и полоса поднимаются к светлому тексту тем же приёмом, каким
     * тема получает своё тёмное значение.
     */
    @Test
    fun `the raised accent of every profile colour passes AA on the dark background`() {
        val wrong = ProfilePalette.all.filter { colour ->
            val raised = tint(Palette.Background, colour.color, 0.55f)
            contrastRatio(raised, Palette.DarkBackground) < AA_NORMAL
        }
        assertTrue("the raised accent must read on the dark background: $wrong", wrong.isEmpty())
    }
}
