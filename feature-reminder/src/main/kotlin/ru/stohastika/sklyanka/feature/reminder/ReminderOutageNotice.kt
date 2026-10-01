package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.reminder.ReminderOutage
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import java.time.Instant
import java.time.LocalDateTime

/**
 * Сообщение о перерыве в напоминаниях при входе в приложение (НАП-38).
 *
 * **Почему оно живёт здесь, а ставится в корне приложения.** Признак перерыва принадлежит
 * подсистеме напоминаний, а место показа — навигации: человек обязан увидеть сообщение
 * там, куда он входит, а не только на экране диагностики, куда он сам не пойдёт. Текст
 * и вид — здесь (тексты напоминаний живут в этом модуле), решение «над всем содержимым» —
 * в `app`, как и все прочие решения о размещении экранов (`ADR-0007`).
 *
 * Сообщение прямое, как требует НАП-38: с какого момента напоминания не приходили и что
 * с этим делать. Действие ведёт одним шагом на «Настроить надёжность» с фокусом на первом
 * незакрытом шаге (ОВ-107): раньше оно вело в диагностику, откуда до нужной настройки EMUI
 * оставалось три-четыре перехода. Кнопка «Понятно», прячущая отказ, была бы ровно тем
 * молчанием, из-за которого жалоба «оно перестало напоминать» неразбираема.
 */
@Composable
fun ReminderOutageNotice(
    outage: ReminderOutage,
    onFix: () -> Unit,
    toLocal: (Instant) -> LocalDateTime,
    modifier: Modifier = Modifier,
) {
    Banner(
        text = stringResource(R.string.reminder_outage_notice, toLocal(outage.since).outageMoment()),
        actionLabel = stringResource(R.string.reminder_outage_notice_action),
        onAction = onFix,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Screen, vertical = SklyankaSpacing.Small)
            .testTag(TAG_OUTAGE_NOTICE),
    )
}

const val TAG_OUTAGE_NOTICE = "reminder_outage_notice"
