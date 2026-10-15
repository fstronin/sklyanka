package ru.stohastika.sklyanka.acceptance.pro

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.ProMark
import ru.stohastika.sklyanka.ui.component.TAG_PRO_MARK
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Шаги приёмки SKL-122 для метки «Про» (критерий приёмки 8).
 *
 * Метка — элемент дизайн-системы (`core-ui`), но её проверка живёт в `app`: там доступны
 * и ресурсы всех модулей, и стенд композиции (`SKL-26/TDD.md §4.3`). Расстановка метки
 * по платным возможностям — задачи 117–121, поэтому проверяется сам элемент, и проверяется
 * не «выглядит похоже», а три вещи, которые норма называет прямо (§5.9, Р-79):
 * слово «Про» есть; **нажатия у метки нет** (одна цель нажатия у элемента — сама строка);
 * **высота метки меньше цели нажатия**, потому что она не элемент управления, а признак
 * платности; искра **декоративна** — чтению с экрана не объявляется (НФТ-33).
 *
 * Мягкий акцент и пара `accentSoft`/`onAccentSoft` здесь не проверяются цветом: пиксельный
 * эталон метки в потолок 110 не входит (Р-61), а значение токена проверено в `core-ui`
 * (`MarkPalette`-страж и токены палитры). Это названный предел, а не пропуск.
 */
@OptIn(ExperimentalTestApi::class)
class Sk122ProMarkSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Допустим("^метка «Про» показана на стенде$")
    fun theMarkIsShownOnTheStand() {
        runComposeUiTest {
            setContent { SklyankaTheme { ProMark() } }
            onNodeWithTag(TAG_PRO_MARK, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^в метке видно слово «Про»$")
    fun theWordIsVisible() {
        val word = context.getString(R.string.pro_mark)
        runComposeUiTest {
            setContent { SklyankaTheme { ProMark() } }
            onNodeWithText(word, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^у метки нет действия нажатия$")
    fun theMarkIsNotClickable() {
        runComposeUiTest {
            setContent { SklyankaTheme { ProMark() } }
            val mark = onNodeWithTag(TAG_PRO_MARK, useUnmergedTree = true).fetchSemanticsNode()
            assertFalse(
                "метка не элемент управления: действия нажатия у неё быть не может (§6.2, Р-79 п. 3)",
                mark.config.contains(SemanticsActions.OnClick),
            )
        }
    }

    @Тогда("^высота метки меньше цели нажатия, назначенной системе$")
    fun theMarkIsSmallerThanTheTouchTarget() {
        runComposeUiTest {
            setContent { SklyankaTheme { ProMark() } }
            val mark = onNodeWithTag(TAG_PRO_MARK, useUnmergedTree = true).fetchSemanticsNode()
            val heightDp = mark.size.height / density.density
            assertTrue(
                "высота метки ≈32 dp, а не чиповые 44: правило цели нажатия говорит о нажимаемых элементах (§6.2)",
                heightDp < SklyankaSizes.MinTouchTarget.value,
            )
        }
    }

    @Тогда("^искра чтению с экрана не объявлена$")
    fun theSparkIsDecorative() {
        runComposeUiTest {
            setContent { SklyankaTheme { ProMark() } }
            val described = onAllNodes(
                hasAnyAncestor(hasTestTag(TAG_PRO_MARK)) and
                    SemanticsMatcher("not announced to the screen reader") { node ->
                        node.config.contains(SemanticsProperties.ContentDescription)
                    },
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue(
                "искра декоративна: смысл несёт слово «Про», и глиф чтению не объявляется (НФТ-33)",
                described.isEmpty(),
            )
        }
    }
}
