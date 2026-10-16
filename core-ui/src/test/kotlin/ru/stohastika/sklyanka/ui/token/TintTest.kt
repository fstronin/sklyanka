package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Тонирование поверхностей чужого профиля (TDD §5.2, критерий 1).
 *
 * Эталон — значения при цвете профиля `#8F3E23`, посчитанные от текущих базовых
 * токенов и доли: палитра пересматривалась решением Р-20, а макеты `design/`
 * не редактируются, поэтому кадр макета эталоном быть перестал. Допуск — округление
 * канала; подкраска обязана оставаться той же самой подкраской, что и функция.
 */
class TintTest {

    private val light = lightColors()
    private val profile = Palette.ProfileForeign
    private val tinted = tintedColors(light, profile, dark = false)

    @Test
    fun `tinted surfaces follow the base tokens`() {
        assertChannelsClose("background", tinted.background, TINTED_BACKGROUND)
        assertChannelsClose("profileBackground", tinted.profileBackground, TINTED_BACKGROUND)
        assertChannelsClose("profileHeader", tinted.profileHeader, TINTED_HEADER)
        assertChannelsClose("line", tinted.line, TINTED_LINE)
        assertChannelsClose("lineOnSurface", tinted.lineOnSurface, TINTED_LINE_ON_SURFACE)
        assertChannelsClose("lineInteractive", tinted.lineInteractive, TINTED_LINE_INTERACTIVE)
        // Рамка чужого профиля выводится от поверхности **среды** долей 0,20 (§2.2, SKL-120):
        // к светлой поверхности, а не от рамки темы — иначе светлая рамка вставала бы
        // вокруг тёмной карточки.
        assertChannelsClose("accentBorder", tinted.accentBorder, tint(light.surface, profile, 0.20f).toArgb())
    }

    @Test
    fun `the soft plate and its ink are the profile's own and do not follow the environment`() {
        // Подложка семьи — «бумага»: цвет профиля, разбавленный к белой поверхности 0,14,
        // и чернила на ней — к основному тексту 0,50. Оба значения одни в обеих средах (§2.2).
        assertChannelsClose("accentSoft", tinted.accentSoft, tint(Palette.Surface, profile, 0.14f).toArgb())
        assertChannelsClose("onAccentSoft", tinted.onAccentSoft, onAccentSoftOf(profile).toArgb())
        val atNight = tintedColors(darkColors(), profile, dark = true)
        assertChannelsClose("accentSoft at night", atNight.accentSoft, tint(Palette.Surface, profile, 0.14f).toArgb())
        assertChannelsClose("onAccentSoft at night", atNight.onAccentSoft, onAccentSoftOf(profile).toArgb())
    }

    @Test
    fun `accent and stripe are raised at night and stay the profile colour by day`() {
        assertEquals(profile, tinted.accent)
        assertEquals(profile, tinted.profileStripe)
        // В тёмной среде они поднимаются к светлому значению — иначе ролью текста не были бы (§2.2).
        val atNight = tintedColors(darkColors(), profile, dark = true)
        assertChannelsClose("accent at night", atNight.accent, tint(Palette.Background, profile, 0.55f).toArgb())
        assertChannelsClose("stripe at night", atNight.profileStripe, tint(Palette.Background, profile, 0.55f).toArgb())
    }

    /**
     * Что намеренно не тонируется.
     *
     * «Принято» не зависит от того, чей профиль; текст держит контраст, а не оттенок;
     * архив в контексте профиля не подкрашивается. Если однажды кто-то решит подкрасить
     * и их, тест назовёт роль поимённо.
     */
    @Test
    fun `semantic and text roles are left alone`() {
        assertEquals(light.taken, tinted.taken)
        assertEquals(light.takenSoft, tinted.takenSoft)
        assertEquals(light.text, tinted.text)
        assertEquals(light.textMuted, tinted.textMuted)
        assertEquals(light.archiveBackground, tinted.archiveBackground)
        assertEquals(light.surface, tinted.surface)
        assertEquals(light.warning, tinted.warning)
        // Опасность не становится цветом профиля: иначе кнопка необратимого действия
        // перекрашивалась бы в чужом профиле (ОВ-67).
        assertEquals(light.danger, tinted.danger)
    }

    @Test
    fun `owner theme is not tinted at all`() {
        assertEquals(light.background, lightColors().background)
        assertEquals(0f, lightColors().profileStripe.alpha, 0f)
        assertEquals(lightColors().background, lightColors().profileHeader)
    }

    @Test
    fun `tinting is monotonic in the ratio`() {
        val weak = tint(light.background, profile, TINT_BACKGROUND)
        val strong = tint(light.background, profile, TINT_HEADER)
        assertTrue("stronger tint must be further from the base", strong.red < weak.red)
        assertTrue(strong.green < weak.green)
    }

    private fun assertChannelsClose(role: String, actual: Color, expected: Int) {
        val expectedColor = Color(expected)
        listOf(
            "red" to (actual.red to expectedColor.red),
            "green" to (actual.green to expectedColor.green),
            "blue" to (actual.blue to expectedColor.blue),
        ).forEach { (channel, values) ->
            val difference = abs(values.first.to255() - values.second.to255())
            assertTrue(
                "$role.$channel: expected ${values.second.to255()}, " +
                    "actual ${values.first.to255()}, difference $difference > $TOLERANCE",
                difference <= TOLERANCE,
            )
        }
    }

    private fun Float.to255(): Int = (this * MAX_CHANNEL).roundToInt()

    private companion object {

        /**
         * Допуск по каналу.
         *
         * Значения посчитаны от тех же токенов и той же доли, что и [tint], поэтому
         * разойтись может только округление канала.
         */
        const val TOLERANCE = 1
        const val MAX_CHANNEL = 255f

        const val TINTED_BACKGROUND = 0xFFF4F0E8.toInt()
        const val TINTED_HEADER = 0xFFEBE0D7.toInt()
        const val TINTED_LINE = 0xFFE0D3C6.toInt()
        const val TINTED_LINE_ON_SURFACE = 0xFFE8DBCE.toInt()
        const val TINTED_LINE_INTERACTIVE = 0xFFD4C3B2.toInt()
        const val TINTED_ACCENT_BORDER = 0xFFC2B6A1.toInt()
    }
}
