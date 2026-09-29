package ru.stohastika.sklyanka.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import java.util.Locale

/**
 * Состав заставки (`acceptance.feature`, «Заставка показывает знак и название»).
 *
 * Главная проверка здесь — отрицательная: индикатора загрузки на заставке нет.
 * Положительные легко восстановить глазами, а вот индикатор добавляют «чтобы
 * человек видел, что что-то происходит», и замечают это уже в магазине.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), а прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theSplashCarriesTheMarkTheNameTheTaglineAndTheVendor() {
        composeTestRule.setContent { SplashScreen() }

        composeTestRule.onNodeWithTag(TAG_SPLASH).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cd_app_mark))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.brand_tagline).uppercase(Locale.getDefault()))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.brand_vendor)).assertIsDisplayed()
    }

    @Test
    fun thereIsNoProgressIndicatorOnTheSplash() {
        composeTestRule.setContent { SplashScreen() }
        // Полоса прогресса дизайн-системы — единственный индикатор, который мог бы
        // сюда попасть: собственных заставка не заводит.
        assertEquals(0, composeTestRule.onAllNodesWithTag(TAG_PROGRESS_BAR).fetchSemanticsNodes().size)
    }
}

/**
 * Метка полосы прогресса дизайн-системы.
 *
 * Повторена здесь, а не импортирована, намеренно: тест утверждает, что на заставке
 * НЕТ ни одного такого узла, и если компонент однажды переименуют, тест обязан
 * сломаться и потребовать перечитать заставку заново, а не тихо искать новую метку.
 */
private const val TAG_PROGRESS_BAR = "progress_bar"
