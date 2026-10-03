package ru.stohastika.sklyanka.feature.onboarding.consent

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.feature.onboarding.R

/**
 * Хозяин экрана согласия.
 *
 * Системная «Назад» равна отказу (ПМ-25 п. 3): экран не ловушка, а уход не оставляет
 * вопрос открытым — безопасное умолчание «выключено». Куда уходить после решения,
 * решает вызывающий: `app` знает и цепочку первого запуска, и настройки.
 *
 * Адрес политики берётся из ресурса-плейсхолдера и открывается системным действием —
 * встроенного веб-просмотрщика в приложении нет (ОВ-123).
 */
@Composable
fun ConsentRoute(
    viewModel: ConsentViewModel,
    source: ConsentSource,
    onDecided: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val decided by viewModel.decided.collectAsState()
    val uriHandler = LocalUriHandler.current
    val policyUrl = stringResource(R.string.consent_policy_url)

    BackHandler { viewModel.onDecline() }
    LaunchedEffect(source) { viewModel.start(source) }
    LaunchedEffect(decided) { if (decided) onDecided() }

    ConsentScreen(
        state = state,
        onAllow = viewModel::onAllow,
        onDecline = viewModel::onDecline,
        onOpenPolicy = { uriHandler.openUri(policyUrl) },
        modifier = modifier,
    )
}
