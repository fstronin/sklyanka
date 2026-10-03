package ru.stohastika.sklyanka.feature.onboarding.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.onboarding.R
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Экран «Согласие на аналитику» (`03-screens.md`, ОВ-123).
 *
 * Один экран на два входа: первый запуск и включение из настроек. До разрешения
 * не отправляется ни одного события и не запрашивается ни одного варианта; отказная
 * кнопка — того же габарита, что основная, иерархия задана заливкой, а не размером
 * (ОВ-90). Тумблеров и галочек нет: согласие — действие кнопки (ПМ-18).
 *
 * Тексты — нейтральные формулировки структуры и плейсхолдеры: полные даёт заказчик
 * с юристом (ПМ-19, `ADR-0011` п. 8); правовой блок и адрес политики помечены
 * `translatable="false"` и содержат пометку, а не выдуманную формулировку.
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    onOpenPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag(TAG_CONSENT_SCREEN),
    ) {
        DisclosureColumn(onOpenPolicy = onOpenPolicy, modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SklyankaSpacing.Screen)
                .padding(bottom = SklyankaSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            PrimaryButton(
                text = stringResource(R.string.consent_allow),
                onClick = onAllow,
                form = ButtonForm.Screen,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(
                    if (state.fromSettings) R.string.consent_decline_settings else R.string.consent_decline,
                ),
                onClick = onDecline,
                form = ButtonForm.Screen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Раскрытие: заголовок, три блока, баннер гарантий, правовой плейсхолдер и ссылка политики. */
@Composable
private fun DisclosureColumn(onOpenPolicy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
    ) {
        Text(
            text = stringResource(R.string.consent_title),
            style = SklyankaType.Title,
            color = colors.text,
            modifier = Modifier
                .padding(top = SklyankaSpacing.Screen)
                .testTag(TAG_CONSENT_TITLE),
        )
        Text(
            text = stringResource(R.string.consent_lead),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
        )
        DisclosureBlock(R.string.consent_sent_title, R.string.consent_sent_body)
        DisclosureBlock(R.string.consent_not_sent_title, R.string.consent_not_sent_body)
        DisclosureBlock(R.string.consent_variants_title, R.string.consent_variants_body)
        Banner(text = stringResource(R.string.consent_banner), kind = BannerKind.Neutral)
        Text(
            text = stringResource(R.string.consent_legal_placeholder),
            style = SklyankaType.CaptionSmall,
            color = colors.textMuted,
        )
        QuietButton(
            text = stringResource(R.string.consent_policy),
            onClick = onOpenPolicy,
            modifier = Modifier.testTag(TAG_CONSENT_POLICY),
        )
    }
}

/** Блок раскрытия: подзаголовок и текст одним оформлением на все три блока. */
@Composable
private fun DisclosureBlock(title: Int, body: Int) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
        Text(text = stringResource(title), style = SklyankaType.BodyStrong, color = colors.text)
        Text(text = stringResource(body), style = SklyankaType.BodyDefault, color = colors.text)
    }
}

/** Метки экрана — точки опоры экранных проверок. */
const val TAG_CONSENT_SCREEN = "consent_screen"
const val TAG_CONSENT_TITLE = "consent_title"
const val TAG_CONSENT_POLICY = "consent_policy"
