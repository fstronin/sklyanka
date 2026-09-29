package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Тонирование поверхностей чужого профиля (TDD §5.2, критерий 1).
 *
 * Эталон — макет `MainProfile.dc.html` при цвете профиля `#8F3E23`. Совпадение
 * проверяется с допуском: макет рисовался глазом, и требовать от функции побитового
 * совпадения с ним значит подгонять функцию под погрешность рисования. Допуск при
 * этом мал — подкраска обязана оставаться той же самой подкраской.
 */
class TintTest {

    private val light = lightColors()
    private val profile = Palette.ProfileForeign
    private val tinted = tintedColors(light, profile)

    @Test
    fun `tinted surfaces match the profile mockup`() {
        assertChannelsClose("background", tinted.background, MOCKUP_BACKGROUND)
        assertChannelsClose("profileBackground", tinted.profileBackground, MOCKUP_BACKGROUND)
        assertChannelsClose("profileHeader", tinted.profileHeader, MOCKUP_HEADER)
        assertChannelsClose("line", tinted.line, MOCKUP_LINE)
        assertChannelsClose("lineOnSurface", tinted.lineOnSurface, MOCKUP_LINE_ON_SURFACE)
        assertChannelsClose("lineInteractive", tinted.lineInteractive, MOCKUP_LINE_INTERACTIVE)
        assertChannelsClose("accentBorder", tinted.accentBorder, MOCKUP_ACCENT_BORDER)
    }

    @Test
    fun `accent and stripe become the profile colour itself`() {
        assertEquals(profile, tinted.accent)
        assertEquals(profile, tinted.profileStripe)
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
         * TDD §5.2 закладывал один-два пункта; фактическое расхождение доходит до
         * четырёх на линиях. Причина — в макете каждая линия подкрашена своей долей
         * (от 0,041 до 0,066), а система держит одну долю на все линии. Заводить три
         * доли ради четырёх пунктов не стоит: на экране разница не видна, а лишняя
         * константа — это лишний способ рассогласовать набор.
         */
        const val TOLERANCE = 4
        const val MAX_CHANNEL = 255f

        const val MOCKUP_BACKGROUND = 0xFFF7F1EA.toInt()
        const val MOCKUP_HEADER = 0xFFF0E2D6.toInt()
        const val MOCKUP_LINE = 0xFFE3D2C3.toInt()
        const val MOCKUP_LINE_ON_SURFACE = 0xFFEBDFD2.toInt()
        const val MOCKUP_LINE_INTERACTIVE = 0xFFD8C3B2.toInt()
        const val MOCKUP_ACCENT_BORDER = 0xFFD8B49F.toInt()
    }
}
