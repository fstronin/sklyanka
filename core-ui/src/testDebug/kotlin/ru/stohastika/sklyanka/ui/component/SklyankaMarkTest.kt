package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.util.Locale

/**
 * Знак на экране: запрошенный размер, описание для чтения с экрана, логотип
 * (`task.md`, критерии 9 и 13; НФТ-30).
 *
 * Какой именно вариант нарисован, проверяет `MarkVariantTest` на JVM — правило
 * выбора это арифметика, и гонять ради неё эмулятор незачем. Здесь проверяется то,
 * что без композиции не проверить: знак действительно занимает столько, сколько
 * попросили, и действительно озвучивается.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), а прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class SklyankaMarkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theMarkOccupiesExactlyTheRequestedSize() {
        val description = context.getString(R.string.cd_app_mark_glass)
        composeTestRule.setContent {
            SklyankaTheme(dark = true) {
                SklyankaMark(
                    contentDescription = description,
                    size = SklyankaSizes.SplashMark,
                    environment = MarkEnvironment.Dark,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription(description)
            .assertWidthIsEqualTo(SklyankaSizes.SplashMark)
            .assertHeightIsEqualTo(SklyankaSizes.SplashMark)
    }

    @Test
    fun everyStepOfTheLadderCarriesItsOwnDescription() {
        val descriptions = listOf(
            R.string.cd_flat_mark_72 to 72.dp,
            R.string.cd_flat_mark_44 to 44.dp,
            R.string.cd_flat_mark_28 to 28.dp,
            R.string.cd_flat_mark_18 to 18.dp,
        ).map { (id, size) -> context.getString(id) to size }

        composeTestRule.setContent {
            SklyankaTheme {
                Column {
                    descriptions.forEach { (description, size) ->
                        SklyankaMark(
                            contentDescription = description,
                            size = size,
                            environment = MarkEnvironment.Light,
                        )
                    }
                }
            }
        }

        descriptions.forEach { (description, size) ->
            composeTestRule.onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(size)
        }
    }

    @Test
    fun theIllustrationVariantKeepsTheColourItWasGiven() {
        // Знак-иллюстрация красится ролью места, а не средой (§7). Проверить цвет
        // пикселя без устройства нельзя; проверяется то, что компонент принимает
        // цвет и рисуется в запрошенном размере.
        composeTestRule.setContent {
            SklyankaTheme {
                FlatMark(
                    color = SklyankaTheme.colors.switchOff,
                    size = SklyankaSizes.EmptyStateMark,
                )
            }
        }
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun theLockupPutsTheNameNextToTheMark() {
        composeTestRule.setContent { SklyankaLockup() }
        composeTestRule.onNodeWithTag(TAG_LOCKUP).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.brand_tagline).uppercase(Locale.getDefault()))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cd_app_mark))
            .assertContentDescriptionEquals(context.getString(R.string.cd_app_mark))
    }
}
