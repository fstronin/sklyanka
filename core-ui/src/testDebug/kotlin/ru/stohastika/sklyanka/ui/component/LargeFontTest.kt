package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.snapshot.EnvironmentSheet
import ru.stohastika.sklyanka.ui.snapshot.SnapshotSurface
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Увеличенный системный шрифт: текст не обрезан и не разорван (НФТ-31, SKL-25).
 *
 * Зачем проверка рядом с эталонным снимком. Снимок фиксирует ИЗМЕНЕНИЕ вёрстки, но не
 * утверждает её правильность: эталон с обрезанной подписью — такой же зелёный эталон,
 * как любой другой, и именно так дефект дожил до SKL-25
 * (`docs/tasks/SKL-24/TDD.md §2.2`). Здесь утверждается свойство, а не совпадение с
 * картинкой, и утверждается оно по разметке текста, которую Compose уже посчитал:
 * разбор — `TextLayoutFacts.kt`.
 *
 * Композиция — тот же лист среды, который снимает эталон
 * `core-ui/screenshots/environment/`. Общий намеренно: снимок и проверка обязаны
 * говорить об одном и том же, иначе снимок показывает одно, а проверка молчит о другом.
 *
 * Чего проверка НЕ утверждает: как это выглядит на настоящем экране EMUI. Перекрытие
 * соседних блоков, читаемость и проходимость экрана до конечного действия по-прежнему
 * смотрят глазами на эталонном устройстве — группа `@manual @device` файла
 * `docs/tasks/SKL-25/acceptance.feature`.
 */
@RunWith(AndroidJUnit4::class)
class LargeFontTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun textIsNeitherCutNorBrokenAtTheNormalScale() = assertLayoutIsIntact(SCALE_100)

    @Test
    fun textIsNeitherCutNorBrokenAtOneHundredThirtyPercent() = assertLayoutIsIntact(SCALE_130)

    @Test
    fun textIsNeitherCutNorBrokenAtTwoHundredPercent() = assertLayoutIsIntact(SCALE_200)

    @Test
    fun theSameHoldsInTheDarkEnvironment() = assertLayoutIsIntact(SCALE_200, dark = true)

    /**
     * Перестроение ряда не имеет права уменьшить цель нажатия (НФТ-29).
     *
     * Кнопки, вставшие друг под друга, остаются кнопками: лечить одно требование
     * доступности за счёт другого нельзя.
     */
    @Test
    fun everyActionKeepsItsTouchTargetAtTwoHundredPercent() {
        show(SCALE_200)
        val minimum = with(composeTestRule.density) { (SklyankaSizes.MinTouchTarget - TOLERANCE).roundToPx() }
        val targets = composeTestRule.touchTargets()
        assertTrue("the sheet must contain clickable elements", targets.isNotEmpty())
        val small = targets.filter { size -> size.width < minimum || size.height < minimum }
        assertTrue(
            small.joinToString("\n") { size -> "clickable area ${size.width}×${size.height} px is below $minimum" },
            small.isEmpty(),
        )
    }

    private fun assertLayoutIsIntact(scale: Float, dark: Boolean = false) {
        show(scale, dark)
        val facts = composeTestRule.textFacts()
        assertTrue("the sheet must contain text at font scale $scale", facts.isNotEmpty())
        val complaints = facts.mapNotNull { it.complaint() }.map { "at font scale $scale $it" }
        assertTrue(complaints.joinToString("\n"), complaints.isEmpty())
    }

    private fun show(scale: Float, dark: Boolean = false) {
        composeTestRule.setContent {
            Scaled(scale) {
                SnapshotSurface(dark = dark) { EnvironmentSheet() }
            }
        }
    }

    /**
     * Системный масштаб шрифта подменой плотности.
     *
     * Так же, как его задаёт снимку `RoborazziComposeOptions.fontScale`: масштаб живёт
     * в `Density`, а не в теме, и экран о нём не знает.
     */
    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private companion object {
        const val SCALE_100 = 1.0f
        const val SCALE_130 = 1.3f
        const val SCALE_200 = 2.0f

        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
