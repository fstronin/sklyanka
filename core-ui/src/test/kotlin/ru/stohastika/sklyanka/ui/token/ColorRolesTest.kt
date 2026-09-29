package ru.stohastika.sklyanka.ui.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Полнота наборов ролей (TDD §11.1).
 *
 * Роли перечисляются не списком в тесте, а разбором `toString()` набора: список в
 * тесте пришлось бы дописывать руками, и новая роль появилась бы в коде, оставшись
 * непроверенной. Разбор строки выглядит грубо, зато он не отстаёт от кода.
 */
class ColorRolesTest {

    private val light = rolesOf(lightColors())
    private val dark = rolesOf(darkColors())

    @Test
    fun `light set defines every role`() {
        assertTrue("the role set must not be empty", light.isNotEmpty())
        val undefined = light.filterValues { it.isBlank() }.keys
        assertTrue("roles without a value: $undefined", undefined.isEmpty())
    }

    @Test
    fun `dark set defines the same roles as the light one`() {
        assertEquals(light.keys, dark.keys)
    }

    /**
     * §5: в тёмной среде переопределяются ровно восемь ролей — плюс две нейтральные
     * роли профиля, которые своего цвета не вводят и повторяют фон среды.
     *
     * Тест падает и когда тёмную роль забыли, и когда завели лишнюю: неиспользуемый
     * тёмный токен — приглашение нарисовать экран, которого в объёме нет.
     */
    @Test
    fun `dark set overrides exactly the eight declared roles`() {
        val changed = light.keys.filter { role -> light[role] != dark[role] }.toSet()
        assertEquals((DARK_OVERRIDDEN_ROLES + NEUTRAL_PROFILE_ROLES).toSet(), changed)
        assertEquals(EIGHT_DARK_TOKENS, DARK_OVERRIDDEN_ROLES.size)
    }

    /**
     * Нейтральная роль профиля — это «подкраски нет», а не отдельный цвет (ПРФ-16).
     *
     * Поэтому в любой среде она равна фону этой среды. Пока роль оставалась светлой
     * в тёмном наборе, шапка экрана в тёмной среде рисовалась светлой, а заголовок
     * поверх неё — цветом фона, то есть не рисовался вовсе (SKL-25, дефект 2).
     */
    @Test
    fun `neutral profile roles follow the background of their environment`() {
        NEUTRAL_PROFILE_ROLES.forEach { role ->
            assertEquals("$role must equal the light background", light["background"], light[role])
            assertEquals("$role must equal the dark background", dark["background"], dark[role])
        }
    }

    @Test
    fun `owner profile has no stripe and no tinted surfaces`() {
        val colors = lightColors()
        assertEquals(0f, colors.profileStripe.alpha, 0f)
        assertEquals(colors.background, colors.profileHeader)
        assertEquals(colors.background, colors.profileBackground)
        assertEquals(ProfilePalette.ownerAccent, colors.accent)
    }

    @Test
    fun `foreign profile turns all three indicators on at once`() {
        ProfilePalette.all.forEach { profile ->
            val colors = tintedColors(lightColors(), profile.color)
            assertEquals(profile.color, colors.profileStripe)
            assertEquals(profile.color, colors.accent)
            assertTrue(
                "${profile.name}: header must differ from the plain background",
                colors.profileHeader != lightColors().background,
            )
        }
    }

    private companion object {

        /** §5 называет восемь тёмных токенов; число проверяется, а не подразумевается. */
        const val EIGHT_DARK_TOKENS = 8

        /**
         * Имена и значения ролей из `toString()` набора.
         *
         * Внутри значения `Color(...)` собственных пар «имя=значение» нет, поэтому
         * маркером служит `имяРоли=` — и разбор не путается в запятых цвета.
         */
        val ROLE = Regex("""\b([a-z][A-Za-z]*)=""")

        fun rolesOf(colors: SklyankaColors): Map<String, String> {
            val text = colors.toString()
            val matches = ROLE.findAll(text).toList()
            return matches.mapIndexed { index, match ->
                val from = match.range.last + 1
                val to = matches.getOrNull(index + 1)?.range?.first ?: text.length
                match.groupValues[1] to text.substring(from, to).trim().trimEnd(',', ')')
            }.toMap()
        }
    }
}
