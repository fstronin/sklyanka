package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.ui.token.Palette
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.pressedTint

class TabTintTest {

    @Test
    fun `a pressed tab takes the pressed tint and enters on the enter curve`() {
        val animation = tabTint(base = BASE, pressed = true)

        assertEquals(pressedTint(BASE), animation.target)
        val spec = animation.spec as TweenSpec<Color>
        assertEquals(SklyankaMotion.DurationFast, spec.durationMillis)
        assertEquals(SklyankaMotion.EasingEnter, spec.easing)
    }

    @Test
    fun `a released tab returns to the base tint on the exit curve`() {
        val animation = tabTint(base = BASE, pressed = false)

        assertEquals(BASE, animation.target)
        val spec = animation.spec as TweenSpec<Color>
        assertEquals(SklyankaMotion.DurationFast, spec.durationMillis)
        assertEquals(SklyankaMotion.EasingExit, spec.easing)
    }

    private companion object {
        val BASE = Palette.TextMuted
    }
}
