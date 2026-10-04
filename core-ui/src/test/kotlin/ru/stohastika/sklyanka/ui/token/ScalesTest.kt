package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Шкалы: типографика в `sp`, геометрия и отступы в `dp` (§6.3, TDD §11.1).
 *
 * Проверка не формальность. Размер шрифта, заданный в `dp`, перестаёт расти вместе
 * с системным шрифтом, и обнаруживается это на устройстве человека, которому
 * крупный шрифт нужен, — то есть позже всего.
 */
class ScalesTest {

    @Test
    fun `every text style is measured in sp`() {
        SklyankaType.all.forEach { style ->
            assertTrue("fontSize must be in sp: $style", style.fontSize.isSp)
            assertTrue("lineHeight must be in sp: $style", style.lineHeight.isSp)
        }
    }

    @Test
    fun `type scale keeps exactly the sizes of the specification`() {
        val sizes = SklyankaType.all.map { it.fontSize.value }.distinct().sortedDescending()
        assertEquals(SPEC_FONT_SIZES, sizes)
    }

    @Test
    fun `headings use the heading family and the interface uses the interface family`() {
        SklyankaType.headings.forEach { style ->
            assertEquals("heading must be set in Literata", Literata, style.fontFamily)
        }
        SklyankaType.all.filterNot { it in SklyankaType.headings }.forEach { style ->
            assertEquals("interface text must be set in Golos Text", GolosText, style.fontFamily)
        }
    }

    @Test
    fun `overline styles carry tracking and the rest do not`() {
        assertTrue(SklyankaType.Overline.letterSpacing.value > 0f)
        assertTrue(SklyankaType.OverlineDate.letterSpacing.value > 0f)
        assertTrue(SklyankaType.MicroOverline.letterSpacing.value > 0f)
        assertTrue("display tracking is negative by the specification", SklyankaType.Display.letterSpacing.value < 0f)
        assertEquals(0f, SklyankaType.Body.letterSpacing.value, 0f)
    }

    @Test
    fun `line height is never smaller than the font size`() {
        SklyankaType.all.forEach { style ->
            assertTrue(
                "line height below font size squeezes the text: $style",
                style.lineHeight.value >= style.fontSize.value,
            )
        }
    }

    @Test
    fun `radius scale keeps exactly the steps of the specification`() {
        assertEquals(SPEC_RADII.map { it.dp }, SklyankaShapes.allRadii)
    }

    @Test
    fun `spacing scale keeps exactly the steps of the specification`() {
        assertEquals(SPEC_SPACING.map { it.dp }, SklyankaSpacing.all)
    }

    @Test
    fun `minimum touch target is the one the system declares`() {
        assertEquals(MIN_TOUCH_TARGET.dp, SklyankaSizes.MinTouchTarget)
        // Material предлагает 48 dp; система задаёт 44, и расхождение видно на
        // плотной вёрстке макета (§3.3).
        assertTrue(SklyankaSizes.MinTouchTarget < MATERIAL_TOUCH_TARGET.dp)
    }

    @Test
    fun `components never go below the minimum touch target`() {
        listOf(
            "markButton" to SklyankaSizes.MarkButton,
            "chip" to SklyankaSizes.ChipHeight,
            "secondaryButton" to SklyankaSizes.SecondaryButtonHeight,
            "primaryButton" to SklyankaSizes.PrimaryButtonHeight,
        ).forEach { (name, size) ->
            assertTrue("$name is $size, below the minimum", size >= SklyankaSizes.MinTouchTarget)
        }
    }

    @Test
    fun `input text never drops below the size EMUI starts rescaling at`() {
        assertTrue(SklyankaType.Body.fontSize >= MIN_INPUT_SIZE.sp)
    }

    @Test
    fun `the diagnostics tokens keep the sizes of the mockup`() {
        // Габариты именные и живут токенами, а не по месту (НФТ-47): плитка-маркер 40,
        // колонка действия 100, многострочное поле не ниже 112 (§6.6, AC-10/AC-11).
        assertEquals(40.dp, SklyankaSizes.MarkerTile)
        assertEquals(100.dp, SklyankaSizes.PermissionActionColumn)
        assertEquals(112.dp, SklyankaSizes.MultilineFieldMinHeight)
        // Ритм карточки «Разрешения»: `.pgroup{padding:13px 0 3px}` (AC-8).
        assertEquals(13.dp, SklyankaSizes.PermissionGroupLabelPaddingTop)
        assertEquals(3.dp, SklyankaSizes.PermissionGroupPaddingBottom)
    }

    @Test
    fun `the dark reminder button tokens keep the sizes of the mockup`() {
        // Тёмная форма полноэкранного напоминания: 58/54 dp (§5.1, ОВ-146). Числа макета
        // закреплены у токенов — здесь литералы разрешены (НФТ-47), — а формы кнопок
        // ссылаются на эти токены (ButtonFormTest).
        assertEquals(58.dp, SklyankaSizes.DarkPrimaryButtonHeight)
        assertEquals(54.dp, SklyankaSizes.DarkSecondaryButtonHeight)
    }

    private companion object {
        val SPEC_FONT_SIZES = listOf(64f, 32f, 30f, 28f, 26f, 23f, 17f, 16f, 15f, 14f, 13f, 12f, 11f, 10f)
        val SPEC_RADII = listOf(9, 12, 13, 14, 16, 18, 999)
        val SPEC_SPACING = listOf(2, 4, 8, 10, 12, 14, 16, 20, 24)
        const val MIN_TOUCH_TARGET = 44
        const val MATERIAL_TOUCH_TARGET = 48
        const val MIN_INPUT_SIZE = 16
    }
}
