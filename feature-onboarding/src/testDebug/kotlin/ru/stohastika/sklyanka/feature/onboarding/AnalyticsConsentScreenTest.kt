package ru.stohastika.sklyanka.feature.onboarding

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.onboarding.RecordingBackOwner
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_ALLOW
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_BODY
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_DECLINE
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_SCREEN
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_TITLE
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Состав экрана согласия на аналитику (SKL-165, АН-1, ПМ-25).
 *
 * Экранный прогон на JVM под Robolectric: настоящая композиция, настоящие ресурсы
 * дизайн-системы, настоящий `BackHandler`. Тексты сверяются с ресурсами, а не набираются
 * в тесте руками — иначе тест проверял бы сам себя.
 *
 * Что стенд не доказывает: что вопрос действительно показывается после диалога политики
 * при первом запуске — это маршрут `app`, и его проверяет стенд композиции запуска.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class AnalyticsConsentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backOwner = RecordingBackOwner()

    private var allowed = false
    private var declined = false
    private var policyOpened = false

    private fun show(withPolicy: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                SklyankaTheme {
                    AnalyticsConsentScreen(
                        onAllow = { allowed = true },
                        onDecline = { declined = true },
                        onOpenPolicy = if (withPolicy) {
                            { policyOpened = true }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun text(id: Int): String = context.getString(id)

    @Test
    fun `the screen asks the question and shows both actions`() {
        show()

        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_TITLE).assertIsDisplayed()
        compose.onNodeWithText(text(UiR.string.analytics_consent_title)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_BODY).assertIsDisplayed()
        compose.onNodeWithText(text(UiR.string.analytics_consent_body)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_ALLOW).assertIsDisplayed()
        compose.onNodeWithText(text(UiR.string.analytics_consent_allow)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_DECLINE).assertIsDisplayed()
        compose.onNodeWithText(text(UiR.string.analytics_consent_decline)).assertIsDisplayed()
    }

    @Test
    fun `allow reports the decision and nothing else`() {
        show()

        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_ALLOW).performClick()

        assertTrue(allowed)
        assertFalse(declined)
    }

    @Test
    fun `not now reports a refusal and nothing else`() {
        show()

        compose.onNodeWithTag(TAG_ANALYTICS_CONSENT_DECLINE).performClick()

        assertTrue(declined)
        assertFalse(allowed)
    }

    @Test
    fun `the system back is a refusal, not a silent exit`() {
        show()

        backOwner.onBackPressedDispatcher.onBackPressed()

        assertTrue(declined)
        assertFalse(allowed)
    }

    @Test
    fun `the policy link is absent without a caller action`() {
        show(withPolicy = false)

        assertEquals(
            0,
            compose.onAllNodesWithText(text(UiR.string.analytics_consent_policy)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the policy link opens the policy when the caller gives it`() {
        show(withPolicy = true)

        compose
            .onNodeWithTag(ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_POLICY)
            .performClick()

        assertTrue(policyOpened)
    }
}
