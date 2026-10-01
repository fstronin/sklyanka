package ru.stohastika.sklyanka.acceptance.brand

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Сопоставление плиток иконки теме системы (ОВ-95, SKL-73).
 *
 * Светлая тема — светлая плитка с тёмным знаком; тёмная — тёмная плитка со стеклянным
 * знаком. Проверяются настоящие ресурсы сборки: отрисовку и кэш иконки задаёт лаунчер,
 * и стенд её не воспроизводит.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconThemeTest {

    @Test
    fun theLightThemeCarriesTheLightTileAndDarkInk() {
        val light = iconVariant("drawable")
        assertEquals(appColour("mark_tile_light"), light.tile)
        assertEquals(appColour("mark_ink_on_light"), light.ink)
        assertTrue(
            "the light tile must be lighter than its ink",
            relativeLuminance(light.tile) > relativeLuminance(light.ink),
        )
    }

    @Test
    fun theDarkThemeCarriesTheDarkTileAndLightInk() {
        val dark = iconVariant("drawable-night")
        assertEquals(appColour("mark_tile_dark"), dark.tile)
        assertEquals(appColour("mark_ink_on_dark"), dark.ink)
        assertTrue(
            "the dark tile must be darker than its ink",
            relativeLuminance(dark.tile) < relativeLuminance(dark.ink),
        )
    }

    @Test
    fun theWarmArcIsTheSameInBothVariants() {
        assertEquals(iconVariant("drawable").warmArc, iconVariant("drawable-night").warmArc)
    }
}
