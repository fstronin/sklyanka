package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.flatMarkColor

/**
 * Пустое состояние (`docs/v2/12-design.md` §4.8).
 *
 * Варианты — нет лекарств, пустой архив, пустая история, часы не подключены —
 * отличаются текстом и действием, а не устройством: заводить на каждый свой
 * компонент значит получить четыре расходящихся экрана из одного.
 *
 * Знак — плоская редукция краской знака светлой плиты: знак остаётся холодным
 * островом внутри тёплого интерфейса (Р-56). Ресурсы стеклянной версии приходят в
 * SKL-21; переключение по размеру — контракт знака, а не подбор по месту (§7).
 */
@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.EmptyStateSide)
            .testTag(TAG_EMPTY_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Screen),
    ) {
        FlatMark(
            color = flatMarkColor(MarkEnvironment.Light),
            modifier = Modifier.semanticsLabel(stringResource(R.string.cd_app_mark)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        ) {
            Text(
                text = title,
                style = SklyankaType.Subtitle,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = text,
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

const val TAG_EMPTY_STATE = "empty_state"
