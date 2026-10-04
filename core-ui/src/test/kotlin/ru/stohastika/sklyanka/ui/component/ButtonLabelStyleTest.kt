package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Канон подписей общих кнопок (ОВ-145, ОВ-146).
 *
 * Снимки ловят расхождение кадром, но не называют, какая ступень шкалы где ожидается.
 * Здесь ступень названа явно: кегль — свойство формы кнопки и роли действия, а не
 * случайность вёрстки, и проверка это фиксирует.
 */
class ButtonLabelStyleTest {

    @Test
    fun `screen form carries the same body strong label for both buttons`() {
        assertEquals(SklyankaType.BodyStrong, ButtonForm.Screen.primaryLabelStyle())
        assertEquals(SklyankaType.BodyStrong, ButtonForm.Screen.secondaryLabelStyle())
    }

    @Test
    fun `card form separates the pair by weight`() {
        assertEquals(SklyankaType.LabelStrong, ButtonForm.Card.primaryLabelStyle())
        assertEquals(SklyankaType.Label, ButtonForm.Card.secondaryLabelStyle())
    }

    @Test
    fun `dark form carries body large and the medium body default of the mockup`() {
        assertEquals(SklyankaType.BodyLarge, ButtonForm.Dark.primaryLabelStyle())
        assertEquals(SklyankaType.BodyDefaultMedium, ButtonForm.Dark.secondaryLabelStyle())
    }

    @Test
    fun `the medium body default keeps the fifteen points and the medium weight`() {
        assertEquals(15f, SklyankaType.BodyDefaultMedium.fontSize.value, 0f)
        assertEquals(FontWeight.Medium, SklyankaType.BodyDefaultMedium.fontWeight)
    }

    @Test
    fun `quiet button in a row keeps the label strong style`() {
        QuietButtonKind.entries.forEach { kind ->
            assertEquals(
                "row quiet button is LabelStrong for $kind",
                SklyankaType.LabelStrong,
                quietLabelStyle(QuietButtonForm.Row, kind),
            )
        }
    }

    @Test
    fun `quiet button in a header is body default, cancel lighter than confirm`() {
        assertEquals(SklyankaType.BodyDefault, quietLabelStyle(QuietButtonForm.Header, QuietButtonKind.Muted))
        assertEquals(SklyankaType.BodyDefaultStrong, quietLabelStyle(QuietButtonForm.Header, QuietButtonKind.Accent))
    }
}
