package ru.stohastika.sklyanka.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Маршрут экрана согласия (SKL-165, TDD §5.5).
 *
 * Наружу — одна лямбда [onDone]: экран не знает, что за ним (онбординг разрешений или
 * закрытие настроек), и хозяин навигации решает это сам. `app` ставит маршрут после
 * диалога политики и до онбординга (АН-1); порядок — часть требования.
 *
 * Пока домен не ответил, показывать нечего, но и «ничего» не должно мелькать чужим цветом:
 * ожидание рисуется фоном темы. Обычно ответ приходит до первого кадра, потому что
 * `app` уже спросил то же правило при сборке маршрута запуска.
 *
 * @param onDone экран закрыт — решением или потому, что спрашивать нечего.
 * @param onOpenPolicy ссылка на политику; `null` — ссылки нет (на первом запуске политика
 *   только что принята отдельным диалогом, ПВ-1).
 */
@Composable
fun AnalyticsConsentRoute(
    viewModel: AnalyticsConsentViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    source: AnalyticsConsentSource = AnalyticsConsentSource.FIRST_LAUNCH,
    onOpenPolicy: (() -> Unit)? = null,
) {
    val visible by viewModel.visible.collectAsState()

    LaunchedEffect(source) { viewModel.show(source) }
    // Выход — только на решённое «показывать нечего»: `null` означает «ещё не знаем».
    LaunchedEffect(visible) { if (visible == false) onDone() }

    if (visible != true) {
        val colors = SklyankaTheme.colors
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        )
        return
    }

    AnalyticsConsentScreen(
        onAllow = { viewModel.choose(AnalyticsConsentState.ALLOWED, source) },
        onDecline = { viewModel.choose(AnalyticsConsentState.DECLINED, source) },
        modifier = modifier,
        onOpenPolicy = onOpenPolicy,
    )
}
