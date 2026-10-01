package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing

/**
 * Результат самопроверки доставки баннером над содержимым (ОВ-108, SKL-86).
 *
 * Успех закрывается «Понятно»: делать по нему нечего, и притворяться, что есть, —
 * ложь. Неудача ведёт «Исправить» на «Настроить надёжность»: это единственный исход,
 * по которому человеку есть что чинить. «Проверка не состоялась» баннера не даёт
 * вовсе — человек в приложении, и исход говорится прямо в блоке.
 *
 * Баннер ставится корнем приложения (`app`), а живёт здесь: текст и вид — часть
 * экрана проверки, а решение о месте показа — навигации (`ADR-0007`).
 */
@Composable
fun DeliveryCheckNotice(
    state: DeliveryCheckState.Finished,
    onAcknowledge: () -> Unit,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.outcome == DeliveryCheckOutcome.NOT_COMPLETED) return
    val failed = state.outcome != DeliveryCheckOutcome.ON_TIME
    Banner(
        text = state.resultText(),
        kind = if (failed) BannerKind.Warning else BannerKind.Neutral,
        actionLabel = stringResource(
            if (failed) R.string.diagnostics_fix else R.string.diagnostics_check_acknowledge,
        ),
        onAction = if (failed) onFix else onAcknowledge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Screen, vertical = SklyankaSpacing.Small)
            .testTag(TAG_CHECK_NOTICE),
    )
}

const val TAG_CHECK_NOTICE = "delivery_check_notice"
