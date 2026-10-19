package ru.stohastika.sklyanka.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Диалог политики при первом запуске (Р-89, AppGallery 7.5).
 *
 * Проверяется состав и роли: документ назван, ссылка открывает его, не снимая диалог,
 * и единственное действие — «Принимаю».
 */
@RunWith(AndroidJUnit4::class)
class PrivacyConsentDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theDialogNamesTheDocumentOpensItAndAcceptsIt() {
        var opened = 0
        var agreed = 0
        composeTestRule.setContent {
            SklyankaTheme {
                PrivacyConsentDialog(onOpenPolicy = { opened++ }, onAgree = { agreed++ })
            }
        }

        composeTestRule.onNodeWithTag(TAG_PRIVACY_CONSENT).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.privacy_consent_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.privacy_consent_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.privacy_consent_agree)).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TAG_PRIVACY_CONSENT_POLICY).performClick()
        assertEquals("the link must open the policy", 1, opened)
        assertEquals("the link must not accept the policy", 0, agreed)

        composeTestRule.onNodeWithTag(TAG_PRIVACY_CONSENT_AGREE).performClick()
        assertEquals("the only action accepts the policy", 1, agreed)
    }
}
