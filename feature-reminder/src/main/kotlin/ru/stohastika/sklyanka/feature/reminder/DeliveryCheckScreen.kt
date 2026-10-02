package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.modifier.withCappedFontScale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDateTime

/**
 * Карточка тестового напоминания (ОВ-108, SKL-86).
 *
 * Тёмная, как боевое напоминание, но с одним действием «Понятно»: отмечать нечего —
 * проверка не приём. Состава слота и списка лекарств здесь нет вовсе: названий
 * и дозировок в проверке не существует, а выдать её за приём нельзя ни одной строкой.
 *
 * Надзаголовок «Проверка доставки» и строка «Тестовое напоминание. Отмечать ничего
 * не нужно.» — дословно по ОВ-108.
 */
@Composable
fun DeliveryCheckScreen(at: LocalDateTime, onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = SklyankaSpacing.Screen)
            .testTag(TAG_DELIVERY_CHECK),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(top = SklyankaSpacing.Screen * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.reminder_check_overline),
                style = SklyankaType.Overline,
                color = colors.textTertiary,
                modifier = Modifier.testTag(TAG_CHECK_OVERLINE),
            )
            Text(
                text = at.timeLabel(),
                // Тот же потолок 130 %, что у боевого напоминания (§6.3, ОВ-108).
                style = SklyankaType.Display.withCappedFontScale(),
                color = colors.text,
                modifier = Modifier.testTag(TAG_CHECK_TIME),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.reminder_check_text),
                style = SklyankaType.BodyLarge,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(TAG_CHECK_TEXT),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.diagnostics_check_acknowledge),
            onClick = onAcknowledge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SklyankaSpacing.EmptyStateSide)
                .testTag(TAG_CHECK_ACKNOWLEDGE),
        )
    }
}

const val TAG_DELIVERY_CHECK = "delivery_check_card"
const val TAG_CHECK_OVERLINE = "delivery_check_overline"
const val TAG_CHECK_TIME = "delivery_check_time"
const val TAG_CHECK_TEXT = "delivery_check_text"
const val TAG_CHECK_ACKNOWLEDGE = "delivery_check_acknowledge"
