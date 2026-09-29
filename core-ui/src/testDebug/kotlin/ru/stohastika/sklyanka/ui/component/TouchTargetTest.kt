package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.catalog.Showcase
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Цели нажатия (НФТ-29, критерий 6).
 *
 * Обход всего каталога, а не отдельных компонентов: список компонентов в тесте
 * пришлось бы дописывать руками, и новый компонент с мелкой кнопкой проехал бы
 * незамеченным. Каталог же обязан содержать всё (критерий 10), и проверка идёт по
 * нему.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), а прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyClickableAreaInTheCatalogIsAtLeastTheMinimum() {
        composeTestRule.setContent { Showcase() }
        assertTouchTargets(composeTestRule)
    }

    @Test
    fun theSameHoldsInAForeignProfile() {
        composeTestRule.setContent {
            Showcase(profile = ProfileAccent.Foreign(ProfileColor.Terracotta))
        }
        assertTouchTargets(composeTestRule)
    }

    private fun assertTouchTargets(provider: SemanticsNodeInteractionsProvider) {
        val nodes = provider.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("the catalog must contain clickable elements", nodes.isNotEmpty())
        nodes.forEach { node ->
            val size = node.size
            val density = node.layoutInfo.density
            val width = with(density) { size.width.toDp() }
            val height = with(density) { size.height.toDp() }
            val minimum = SklyankaSizes.MinTouchTarget - TOLERANCE
            assertTrue(
                "clickable node ${node.id} is $width×$height, below ${SklyankaSizes.MinTouchTarget}",
                width >= minimum && height >= minimum,
            )
        }
    }

    private companion object {
        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
