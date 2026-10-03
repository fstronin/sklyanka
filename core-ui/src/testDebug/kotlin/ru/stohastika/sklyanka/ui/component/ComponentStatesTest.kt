package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Статус не передаётся одним лишь цветом (НФТ-33, критерий 9).
 *
 * У принятого приёма есть галочка и время отметки, у пропущенного — подпись, у
 * приёма, отмеченного из чужого профиля, — пометка «отметили вы». Все подписи
 * берутся из ресурсов, а не из кода (критерий 12), и тест читает их оттуда же:
 * иначе он проверял бы собственную копию текста.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), а прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ComponentStatesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun takenIntakeShowsTheMarkTimeNextToTheCheck() {
        showCard(IntakeCardState.Taken)
        composeTestRule.onNodeWithTag(TAG_TAKEN_STATUS).assertIsDisplayed()
        composeTestRule.onNodeWithText(MARK_TIME).assertIsDisplayed()
    }

    @Test
    fun missedIntakeCarriesACaptionAndNotOnlyAColour() {
        showCard(IntakeCardState.Missed)
        composeTestRule.onNodeWithTag(TAG_MISSED_STATUS).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.status_missed))
            .assertIsDisplayed()
    }

    @Test
    fun dueIntakeOpensBothActions() {
        showCard(IntakeCardState.DueNow)
        composeTestRule.onNodeWithText(context.getString(R.string.action_taken)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_skip)).assertIsDisplayed()
    }

    @Test
    fun anIntakeMarkedFromAnotherProfileSaysSo() {
        composeTestRule.setContent {
            SklyankaTheme {
                IntakeCard(state = IntakeCardState.Taken, labels = labels(), markedByYou = true)
            }
        }
        composeTestRule.onNodeWithTag(TAG_MARKED_BY_YOU).assertIsDisplayed()
    }

    @Test
    fun progressAlwaysCarriesTheSameNumberInText() {
        val label = context.getString(R.string.preview_field_value)
        composeTestRule.setContent {
            SklyankaTheme {
                SklyankaProgressBar(progress = HALF, label = label)
            }
        }
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test
    fun theDangerQuietButtonBelongsToTheDesignSystem() {
        // ОВ-124, §4.3: удаление — вид тихой кнопки, а не новый компонент.
        org.junit.Assert.assertEquals(
            setOf(QuietButtonKind.Accent, QuietButtonKind.Muted, QuietButtonKind.Danger),
            QuietButtonKind.entries.toSet(),
        )
        composeTestRule.setContent {
            SklyankaTheme {
                QuietButton(
                    text = context.getString(R.string.preview_quiet_action),
                    onClick = {},
                    kind = QuietButtonKind.Danger,
                )
            }
        }
        composeTestRule.onNodeWithTag(TAG_QUIET_BUTTON).assertIsDisplayed()
    }

    private fun showCard(state: IntakeCardState) {
        composeTestRule.setContent {
            SklyankaTheme {
                IntakeCard(
                    state = state,
                    labels = labels(),
                    actions = IntakeCardActions(onMark = {}, onSkip = {}),
                )
            }
        }
    }

    private fun labels() = IntakeCardLabels(
        medication = context.getString(R.string.preview_medication),
        dose = context.getString(R.string.preview_dose),
        time = MARK_TIME,
    )

    private companion object {
        const val MARK_TIME = "08:05"
        const val HALF = 0.5f
    }
}
