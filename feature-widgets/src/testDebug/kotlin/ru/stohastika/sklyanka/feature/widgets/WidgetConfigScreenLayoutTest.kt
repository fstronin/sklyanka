package ru.stohastika.sklyanka.feature.widgets

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.feature.widgets.config.TAG_WIDGET_MEDICATION_ROW
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigScreen
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigState
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/** Порог цели нажатия §6.2: 44 dp. */
private val SklyankaMinTouchTarget = SklyankaSizes.MinTouchTarget

/** Потолок строки лекарства по макету: цель 44 dp плюс поля не должны её раздувать. */
private val MedicationRowLimit = SklyankaSizes.MinTouchTarget + 4.dp

/**
 * Цели нажатия экрана настройки виджета (SKL-118, НФТ-29, §6.2).
 *
 * Строка лекарства однострочная и может оказаться ниже 44 dp: визуальный размер остаётся
 * прежним, а измеряемая область расширяется `minTouchTarget`. Сторож держит именно
 * измеряемую высоту — ту, по которой попадает палец.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class WidgetConfigScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the medication row keeps the touch target of the norm`() {
        rule.setContent {
            ProfileChrome(state = ProfileChromeState.Owner) {
                SklyankaTheme {
                    WidgetConfigScreen(
                        state = WidgetConfigState(type = WidgetType.MEDICATION),
                        onSelectType = {},
                        onSelectMedication = {},
                        onComplete = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(TAG_WIDGET_MEDICATION_ROW).assertHeightIsAtLeast(SklyankaMinTouchTarget)
        // И не выше макета: цель расширяется до поля, а не вокруг него — иначе строка
        // раздувалась бы до 12 + 44 + 12 (замечание верификатора V-65).
        val measured = rule.onNodeWithTag(TAG_WIDGET_MEDICATION_ROW).fetchSemanticsNode().size.height
        val lower = with(rule.density) { SklyankaMinTouchTarget.roundToPx() }
        val upper = with(rule.density) { MedicationRowLimit.roundToPx() }
        assertTrue("строка лекарства меряет ${measured}px, ожидалось $lower…$upper px", measured in lower..upper)
    }
}
