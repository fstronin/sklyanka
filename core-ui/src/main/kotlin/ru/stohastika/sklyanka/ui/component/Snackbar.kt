package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Снэкбар — всплывающее подтверждение отметки с обратным ходом (`08-design-system.md` §4.12).
 *
 * Один вид: о случившемся говорит текст, а не цвет и не значок (НФТ-30). Действие ровно
 * одно — сообщение без обратного хода было бы тостом, а тоста в системе нет. Тени нет:
 * система плоская, всплытие передаётся положением. Тёмного варианта тоже нет — снэкбар
 * живёт только на светлом экране «Сегодня».
 *
 * Сообщение временное и фокус чтения не переводится: для чтения с экрана это live region
 * `Polite`, иначе человек с TalkBack его не услышит. Кнопка имеет видимую подпись,
 * отдельного `contentDescription` ей не нужно.
 *
 * Действие берёт `colors.accent` из темы: в чужом профиле — цвет профиля, и компонент
 * о профиле не знает ни одной строкой (ПРФ-11).
 */
@Composable
fun Snackbar(text: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    // Ряд тот же, что у карточки приёма и баннера: при крупном системном шрифте
    // действие уезжает под текст, а текст не сжимается уже своего самого длинного
    // слова (НФТ-31, §6.3).
    StackingRow(
        leading = {},
        text = {
            Text(
                text = text,
                style = SklyankaType.BodyDefault,
                color = colors.text,
            )
        },
        action = {
            // Цель нажатия 44 dp доводится модификатором внутри кнопки (§3.3, НФТ-29).
            QuietButton(text = actionLabel, onClick = onAction)
        },
        gap = SklyankaSpacing.Default,
        minTextWidth = SklyankaSizes.MinTextColumn,
        modifier = modifier
            .background(colors.surface, SklyankaShapes.Large)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Large)
            .padding(
                horizontal = SklyankaSpacing.Block,
                vertical = SklyankaSpacing.Medium,
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(TAG_SNACKBAR),
    )
}

const val TAG_SNACKBAR = "snackbar"
