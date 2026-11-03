package ru.stohastika.sklyanka.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.ui.analytics.AnalyticsConsentSurface

/**
 * Экран «Помочь сделать приложение лучше?» (SKL-165, АН-1, ПМ-25).
 *
 * Композиция живёт в дизайн-системе ([AnalyticsConsentSurface]): та же поверхность
 * открывается из настроек, когда человек включает аналитику осознанно. Здесь — только
 * поведение первого запуска: системная «Назад» означает отказ, а не выход в никуда
 * (ПМ-25 п. 3), и ни одно из двух действий не запирает онбординг.
 */
@Composable
fun AnalyticsConsentScreen(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPolicy: (() -> Unit)? = null,
) {
    // «Назад» = «Сейчас не надо»: других исходов у этого экрана нет, и молчаливый выход
    // оставил бы решение нерешённым — то есть вопрос повторялся бы при каждом запуске.
    BackHandler { onDecline() }
    AnalyticsConsentSurface(
        onAllow = onAllow,
        onDecline = onDecline,
        modifier = modifier,
        onOpenPolicy = onOpenPolicy,
    )
}
