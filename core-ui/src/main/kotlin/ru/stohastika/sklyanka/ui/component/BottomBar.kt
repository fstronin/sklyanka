// Вкладка — перечисление рядом с панелью, а не отдельный файл: имя файла даёт компонент,
// а объяснение, почему тип живёт здесь, — в его KDoc ниже.
@file:Suppress("MatchingDeclarationName")

package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.pressedTint

/**
 * Три вкладки нижней панели (§4.13, ОВ-91).
 *
 * Вкладка — не состояние компонента каталога, а его навигационная идентичность:
 * у панели нет четырёх состояний «выбрана/не выбрана × вкладка». Поэтому перечисление
 * живёт рядом с панелью, как и написано в `SKL-71/TDD.md §4`, а не в словаре состояний
 * `ComponentStates.kt`, по которому SKL-24 требует эталонный снимок на каждое значение.
 * Вид выбранной вкладки закреплён снимками корневых экранов (группа `screen` в `feature-` модулях).
 */
enum class BottomBarTab { Today, Meds, Settings }

/**
 * Нижняя панель на три вкладки — общий компонент дизайн-системы (§4.13, ОВ-91).
 *
 * Живёт в `core-ui`, а не на каждом экране: панель нужна корням всех трёх вкладок,
 * и вторая её копия разошлась бы с первой на первой же правке. Панель не знает ни
 * о маршрутах, ни о намерениях экранов — соответствие вкладки и действия задаёт тот,
 * кто её собирает.
 *
 * Панель — полоса во всю ширину, компактная в обычном шрифте: верхнее и нижнее поле
 * `SklyankaSpacing.Micro` вокруг содержимого, поэтому значок и подпись стоят по центру
 * полосы, а не прижимаются к верхнему краю. Поверхность панели продолжается **под**
 * системной навигацией ровно на `WindowInsets.navigationBars`: при нулевом инсете
 * дополнительной полосы нет, при трёхкнопочной панели белое поле растёт на её высоту,
 * ряд вкладок остаётся выше. Боковые инсеты сдвигают ряд, а не поверхность (SKL-71,
 * SKL-72, SKL-158).
 *
 * Подпись вкладки живёт на любом системном масштабе шрифта, а высота панели растёт
 * вместе с подписью (§6.3, Р-87 п. 4): крупный шрифт не отнимает у человека подписи-
 * ориентиры, значок остаётся с подписью и для чтения с экрана (НФТ-30).
 */
@Composable
fun BottomBar(
    selected: BottomBarTab,
    onSelect: (BottomBarTab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Системные инсеты навигации. Параметром, а не чтением внутри, чтобы проверка могла
     * подставить нулевой, трёхкнопочный и боковой варианты: поверхность обязана тянуться
     * ровно на инсет, а не на константу.
     */
    navigationBars: WindowInsets = WindowInsets.navigationBars,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .testTag(TAG_BOTTOM_BAR),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Боковые инсеты системной панели (ландшафт, боковая навигация) сдвигают
                // ряд вкладок, но не поверхность: она остаётся во всю ширину.
                .windowInsetsPadding(navigationBars.only(WindowInsetsSides.Horizontal))
                .padding(
                    start = SklyankaSpacing.Screen,
                    end = SklyankaSpacing.Screen,
                    top = SklyankaSpacing.Micro,
                    bottom = SklyankaSpacing.Micro,
                ),
        ) {
            Tab(
                label = stringResource(R.string.tab_today),
                selected = selected == BottomBarTab.Today,
                onClick = { onSelect(BottomBarTab.Today) },
                icon = { color -> CalendarGlyph(color) },
                modifier = Modifier.weight(1f),
            )
            Tab(
                label = stringResource(R.string.tab_meds),
                selected = selected == BottomBarTab.Meds,
                onClick = { onSelect(BottomBarTab.Meds) },
                icon = { color -> MedicationPictogram(color) },
                modifier = Modifier.weight(1f),
            )
            Tab(
                label = stringResource(R.string.tab_settings),
                selected = selected == BottomBarTab.Settings,
                onClick = { onSelect(BottomBarTab.Settings) },
                icon = { color -> GearGlyph(color) },
                modifier = Modifier.weight(1f),
            )
        }
        // Поверхность продолжается под системную навигацию ровно на её инсет: при нулевом
        // инсете (жесты без панели, аппаратные клавиши) лишней полосы нет; при трёхкнопочной
        // панели белое поле растёт на её высоту, а ряд вкладок остаётся выше.
        Spacer(modifier = Modifier.windowInsetsBottomHeight(navigationBars))
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Невыбранная вкладка — TextMuted, а не TextTertiary: третий уровень текста
    // не проходит AA на поверхности (3,52–3,80, §6.1 вывод 1).
    val base = if (selected) colors.accent else colors.textMuted
    val showPressed = pressed && !selected
    val animation = tabTint(base = base, pressed = showPressed)
    val tint by animateColorAsState(
        targetValue = animation.target,
        animationSpec = animation.spec,
        label = "tabTint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !selected,
                onClick = onClick,
            )
            .minTouchTarget()
            // Подпись живёт на любом масштабе, но значок остаётся названным и для чтения
            // с экрана, и как второй носитель смысла вкладки (НФТ-30).
            .semantics { contentDescription = label },
    ) {
        icon(tint)
        Text(
            text = label,
            style = if (selected) SklyankaType.MicroStrong else SklyankaType.Micro,
            color = tint,
        )
    }
}

internal data class TabTint(val target: Color, val spec: AnimationSpec<Color>)

internal fun tabTint(base: Color, pressed: Boolean): TabTint = TabTint(
    target = if (pressed) pressedTint(base) else base,
    spec = if (pressed) {
        tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingEnter)
    } else {
        tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingExit)
    },
)

const val TAG_BOTTOM_BAR = "bottom_bar"
