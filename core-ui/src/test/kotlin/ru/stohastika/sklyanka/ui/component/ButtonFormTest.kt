package ru.stohastika.sklyanka.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors

/**
 * Формы кнопок: габарит, радиус и боковое поле подписи (ОВ-145, ОВ-146).
 *
 * Форма объявляет всё, что отличает кнопку одного вида от кнопки другого:
 * высоту основной и второстепенной, радиус и поле подписи. Тест удерживает
 * тёмную форму от сползания к светлой: 28 dp поля подписи ломали ряд тёмного
 * напоминания, и вернуться это поле может только вместе с падением проверки.
 */
class ButtonFormTest {

    @Test
    fun `the dark form keeps the heights and radius of the reminder mockup`() {
        // Числа 58/54 закреплены у токенов (ScalesTest); здесь — что форма берёт именно
        // тёмные габариты, а не светлые.
        assertEquals(SklyankaSizes.DarkPrimaryButtonHeight, ButtonForm.Dark.primaryHeight)
        assertEquals(SklyankaSizes.DarkSecondaryButtonHeight, ButtonForm.Dark.secondaryHeight)
        assertEquals(SklyankaShapes.Card, ButtonForm.Dark.shape)
    }

    /**
     * Поле подписи тёмной формы — ноль: ряд равных половин держится шириной подписи,
     * а не кнопки целиком (§5.1). Возврат 28 dp — дефект, а не настройка.
     */
    @Test
    fun `the dark form has no label padding`() {
        assertEquals(SklyankaSpacing.None, ButtonForm.Dark.labelPadding)
    }

    /**
     * Минимальный боковой зазор тёмной формы — `SklyankaSpacing.Micro` 4 dp (§5.1, ОВ-146):
     * стоп-кран держит ряд, только если подпись не прилипает к рамке. У светлых форм
     * зазора нет — воздух там даёт поле подписи 28 dp, и этот зазор к ним не относится.
     */
    @Test
    fun `the dark form keeps the four dp side gap of the row criterion`() {
        assertEquals(SklyankaSpacing.Micro, ButtonForm.Dark.minLabelSideGap)
        listOf(ButtonForm.Screen, ButtonForm.Card).forEach { form ->
            assertEquals("$form must not carry the dark side gap", SklyankaSpacing.None, form.minLabelSideGap)
        }
    }

    @Test
    fun `the light forms keep their declared heights, radii and label padding`() {
        assertEquals(SklyankaSizes.PrimaryButtonHeight, ButtonForm.Screen.primaryHeight)
        assertEquals(SklyankaSizes.PrimaryButtonHeight, ButtonForm.Screen.secondaryHeight)
        assertEquals(SklyankaShapes.Large, ButtonForm.Screen.shape)
        assertEquals(SklyankaSizes.ButtonPadding, ButtonForm.Screen.labelPadding)

        assertEquals(SklyankaSizes.SecondaryButtonHeight, ButtonForm.Card.primaryHeight)
        assertEquals(SklyankaSizes.SecondaryButtonHeight, ButtonForm.Card.secondaryHeight)
        assertEquals(SklyankaShapes.Button, ButtonForm.Card.shape)
        assertEquals(SklyankaSizes.ButtonPadding, ButtonForm.Card.labelPadding)
    }

    @Test
    fun `the dark secondary button takes the roles of the dark surface`() {
        val roles = ButtonForm.Dark.secondaryRoles(darkColors())
        assertEquals(darkColors().textOnDark, roles.content)
        assertEquals(darkColors().line, roles.border)
        assertEquals(darkColors().surface, roles.pressedBackground)
    }

    @Test
    fun `the light secondary button keeps the roles of the light surface`() {
        listOf(ButtonForm.Screen, ButtonForm.Card).forEach { form ->
            val roles = form.secondaryRoles(lightColors())
            assertEquals(lightColors().text, roles.content)
            assertEquals(lightColors().lineInteractive, roles.border)
            assertEquals(lightColors().neutralSoft, roles.pressedBackground)
        }
    }
}
