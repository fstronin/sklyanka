package ru.stohastika.sklyanka.feature.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Шторка запуска самопроверки доставки (ОВ-108, SKL-86).
 *
 * Инструкция не сворачивается и не прячется за «ещё»: условие проверки — закрытое
 * приложение, и человек обязан прочитать его до нажатия, а не после. Основная кнопка
 * назначает тестовое срабатывание, тихая «Отмена» закрывает шторку и не ставит ничего.
 *
 * По образцу `DoctorReportSheet`: `ScreenHeader(kind = Modal)` с «Отменой» в шапке.
 */
@Composable
fun DeliveryCheckSheet(onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    // Системная «Назад» закрывает шторку, а не экран под ней: шторка — часть экрана,
    // и возврат обязан оставить человека на «Проверке напоминаний» (ОВ-108).
    BackHandler(onBack = onDismiss)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_CHECK_SHEET),
    ) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.diagnostics_check_sheet_title)),
            kind = ScreenHeaderKind.Modal,
            // Тихая «Отмена» стоит под инструкцией и дублируется системным «Назад»:
            // второй крестик в шапке был бы третьим способом сделать то же самое.
            onBack = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_check_sheet_text),
                style = SklyankaType.BodyDefault,
                color = colors.text,
                modifier = Modifier.testTag(TAG_CHECK_SHEET_TEXT),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SklyankaSpacing.Section, vertical = SklyankaSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        ) {
            PrimaryButton(
                text = stringResource(R.string.diagnostics_check_start),
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CHECK_START),
            )
            QuietButton(
                text = stringResource(R.string.diagnostics_check_sheet_cancel),
                onClick = onDismiss,
                kind = QuietButtonKind.Muted,
                modifier = Modifier.testTag(TAG_CHECK_SHEET_CANCEL),
            )
        }
    }
}

const val TAG_CHECK_SHEET = "delivery_check_sheet"
const val TAG_CHECK_SHEET_TEXT = "delivery_check_sheet_text"
const val TAG_CHECK_START = "delivery_check_start"
const val TAG_CHECK_SHEET_CANCEL = "delivery_check_sheet_cancel"
