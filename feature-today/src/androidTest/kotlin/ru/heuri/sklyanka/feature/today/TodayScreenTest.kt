package ru.heuri.sklyanka.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

/** Экранный уровень (НФТ-45): пустой экран открывается и отрисовывается. */
class TodayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyTodayScreenIsRendered() {
        composeRule.setContent { TodayScreen() }

        composeRule.onNodeWithTag(TODAY_SCREEN_TEST_TAG).assertExists()
    }
}
