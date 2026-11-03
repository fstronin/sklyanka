package ru.stohastika.sklyanka.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Вопрос о согласии на аналитику — одна поверхность для первого запуска и настроек
 * (SKL-165, TDD §5.4, ПМ-25).
 *
 * Поверхность без состояния: она не знает ни о согласии, ни о том, откуда её открыли.
 * Два действия и короткое пояснение — всё, что человеку нужно для решения; отказ ничего
 * не запирает и ничем не отличается по весу от согласия (АН-1).
 *
 * Ссылка на политику необязательна: на первом запуске политика только что принята
 * отдельным диалогом (ПВ-1), а при включении из настроек человек вправе перечитать её
 * до решения (ПМ-25 п. 4), поэтому там её подставляет вызывающий.
 */
@Composable
fun AnalyticsConsentSurface(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPolicy: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = SklyankaSpacing.Screen)
            .testTag(TAG_ANALYTICS_CONSENT_SCREEN),
    ) {
        Explanation(modifier = Modifier.weight(1f))
        ConsentActions(onAllow = onAllow, onDecline = onDecline, onOpenPolicy = onOpenPolicy)
    }
}

/** Заголовок и короткое пояснение по центру свободного места: вопрос, а не реклама. */
@Composable
private fun Explanation(modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.analytics_consent_title),
            style = SklyankaType.TitleScreen,
            color = colors.text,
            modifier = Modifier.testTag(TAG_ANALYTICS_CONSENT_TITLE),
        )
        Spacer(modifier = Modifier.padding(top = SklyankaSpacing.Block))
        Text(
            text = stringResource(R.string.analytics_consent_body),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
            modifier = Modifier.testTag(TAG_ANALYTICS_CONSENT_BODY),
        )
    }
}

/** Два действия внизу: согласие и отказ — обе кнопки доступны одинаково (АН-1). */
@Composable
private fun ConsentActions(onAllow: () -> Unit, onDecline: () -> Unit, onOpenPolicy: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SklyankaSpacing.Block, bottom = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onOpenPolicy != null) {
            QuietButton(
                text = stringResource(R.string.analytics_consent_policy),
                onClick = onOpenPolicy,
                kind = QuietButtonKind.Muted,
                modifier = Modifier.testTag(TAG_ANALYTICS_CONSENT_POLICY),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.analytics_consent_allow),
            onClick = onAllow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_ANALYTICS_CONSENT_ALLOW),
        )
        // Отказ — тихая кнопка, но такая же доступная: «Сейчас не надо» не спрятано
        // ни за «ещё», ни за повторным вопросом (АН-1, ПМ-25 п. 3).
        QuietButton(
            text = stringResource(R.string.analytics_consent_decline),
            onClick = onDecline,
            kind = QuietButtonKind.Muted,
            modifier = Modifier.testTag(TAG_ANALYTICS_CONSENT_DECLINE),
        )
    }
}

/** Опора экранного стенда и эталонных снимков: за что зацепиться, кроме слов. */
const val TAG_ANALYTICS_CONSENT_SCREEN = "analytics_consent_screen"
const val TAG_ANALYTICS_CONSENT_TITLE = "analytics_consent_title"
const val TAG_ANALYTICS_CONSENT_BODY = "analytics_consent_body"
const val TAG_ANALYTICS_CONSENT_ALLOW = "analytics_consent_allow"
const val TAG_ANALYTICS_CONSENT_DECLINE = "analytics_consent_decline"
const val TAG_ANALYTICS_CONSENT_POLICY = "analytics_consent_policy"
