package ru.heuri.sklyanka.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SklyankaTextField

/**
 * Лист среды: то, на чём среда видна — шапка, карточка приёма, поле, баннер, кнопка.
 *
 * Общий для эталонного снимка среды (`EnvironmentSnapshotTest`) и для проверки
 * вёрстки при увеличенном системном шрифте (`LargeFontTest`, SKL-25). Общий
 * намеренно: снимок фиксирует изменение, проверка утверждает свойство, и утверждать
 * они обязаны об одной и той же композиции — иначе снимок показывает одно, а
 * проверка молчит о другом.
 */
@Composable
internal fun EnvironmentSheet() {
    val context = LocalContext.current
    ProfileStripe()
    ScreenHeader(
        labels = ScreenHeaderLabels(
            title = stringResource(R.string.preview_screen_title),
            overline = stringResource(R.string.preview_date),
        ),
    )
    IntakeCard(
        state = IntakeCardState.DueNow,
        labels = IntakeCardLabels(
            medication = context.getString(R.string.preview_medication),
            dose = context.getString(R.string.preview_dose),
            time = context.getString(R.string.preview_marked_time),
        ),
        actions = IntakeCardActions(onMark = {}, onSkip = {}),
    )
    SklyankaTextField(
        value = stringResource(R.string.preview_field_value),
        onValueChange = {},
        hint = stringResource(R.string.preview_field_label),
    )
    Banner(
        text = stringResource(R.string.preview_banner_warning),
        kind = BannerKind.Warning,
        actionLabel = stringResource(R.string.preview_quiet_action),
        onAction = {},
    )
    PrimaryButton(text = stringResource(R.string.preview_primary_action), onClick = {})
}
