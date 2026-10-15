package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Роль чипа объявлена, а не подразумевается (НФТ-30, `docs/norm/06-design.md` §5.10).
 *
 * Чип по умолчанию — выбор одного значения из набора (`RadioButton`); ряд дней недели
 * выбирает несколько значений разом и объявляет `Role.Checkbox`. Вид чипа и токены при
 * этом не меняются — проверка читает семантику, а не пиксели. Полное имя дня заменяет
 * сокращённую подпись в чтении с экрана целиком, а не читается вместе с ней.
 */
@RunWith(AndroidJUnit4::class)
class ChipRoleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aDayChipDeclaresTheCheckboxRoleAndItsFullName() {
        composeTestRule.setContent {
            SklyankaTheme {
                SklyankaChip(
                    text = "Mo",
                    state = ChipState.Selected,
                    role = Role.Checkbox,
                    contentDescription = "Monday",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TAG_CHIP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithContentDescription("Monday").assertExists()
    }

    @Test
    fun aChipKeepsTheRadioButtonRoleByDefault() {
        composeTestRule.setContent {
            SklyankaTheme {
                SklyankaChip(text = "3", onClick = {})
            }
        }

        composeTestRule.onNodeWithTag(TAG_CHIP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }
}
