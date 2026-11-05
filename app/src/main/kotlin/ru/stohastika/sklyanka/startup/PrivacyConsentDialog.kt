package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.ui.component.AppDialogScrim
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Диалог политики конфиденциальности при первом запуске (Р-89, правило магазина 7.5).
 *
 * Магазин требует явно предложить прочитать политику до начала работы; учётной записи
 * у продукта нет, поэтому страницы входа, на которой это можно было бы показать, тоже нет.
 * Отсюда единственная поверхность — диалог поверх первого экрана.
 *
 * **Согласие или отказ, без обхода.** Системная «Назад» и тап вне окна не закрывают
 * диалог: иначе замечание проверки «нет явного согласия» осталось бы в силе, а человек
 * мог бы проскочить предложение, не прочитав его. В диалоге два действия: «Принимаю»
 * записывает факт и открывает приложение, «Отказываюсь» закрывает его, не записывая
 * ничего (Р-90). При следующем запуске отказник снова видит диалог — отказа нет.
 * Затемнение и фон окна — роли дизайн-системы, а не чёрная подложка прошивки
 * (`AppDialogScrim`).
 *
 * Ссылка — не действие согласия: она открывает тот же документ, что и строка настроек,
 * и не снимает диалог. Адрес и браузер остаются у `app` (`PolicyPage`), поэтому диалог
 * не знает ни URL, ни `Intent`.
 *
 * @param onOpenPolicy открыть политику; диалог остаётся на экране.
 * @param onAgree человек принял политику — записать факт и снять диалог.
 * @param onDecline человек отказался — закрыть приложение, ничего не записывая.
 */
@Composable
fun PrivacyConsentDialog(
    onOpenPolicy: () -> Unit,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        // Пустой обработчик, а не закрытие: диалог не закрывается ничем, кроме кнопки.
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppDialogScrim(onDismiss = { })
            PrivacySurface(
                onOpenPolicy = onOpenPolicy,
                onAgree = onAgree,
                onDecline = onDecline,
                modifier = modifier,
            )
        }
    }
}

/** Поверхность диалога: заголовок, объяснение, ссылка, согласие и явный отказ. */
@Composable
private fun PrivacySurface(
    onOpenPolicy: () -> Unit,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(SklyankaSpacing.Screen)
            .testTag(TAG_PRIVACY_CONSENT),
        shape = SklyankaShapes.Large,
        color = colors.surface,
        border = BorderStroke(SklyankaSizes.BorderThin, colors.lineOnSurface),
    ) {
        Column(
            modifier = Modifier.padding(SklyankaSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.privacy_consent_title),
                style = SklyankaType.BodyDefaultStrong,
                color = colors.text,
                modifier = Modifier.fillMaxWidth().testTag(TAG_PRIVACY_CONSENT_TITLE),
            )
            Text(
                text = stringResource(R.string.privacy_consent_message),
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth().testTag(TAG_PRIVACY_CONSENT_MESSAGE),
            )
            Text(
                text = stringResource(R.string.privacy_consent_open_policy),
                style = SklyankaType.CaptionSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickable(onClick = onOpenPolicy)
                    .minTouchTarget()
                    .testTag(TAG_PRIVACY_CONSENT_POLICY),
            )
            PrimaryButton(
                text = stringResource(R.string.privacy_consent_agree),
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_PRIVACY_CONSENT_AGREE),
            )
            SecondaryButton(
                text = stringResource(R.string.privacy_consent_decline),
                onClick = onDecline,
                form = ButtonForm.Screen,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_PRIVACY_CONSENT_DECLINE),
            )
        }
    }
}

/** Теги диалога: проверки отличают сам диалог, ссылку и единственное действие. */
const val TAG_PRIVACY_CONSENT: String = "privacy_consent"
const val TAG_PRIVACY_CONSENT_TITLE: String = "privacy_consent_title"
const val TAG_PRIVACY_CONSENT_MESSAGE: String = "privacy_consent_message"
const val TAG_PRIVACY_CONSENT_POLICY: String = "privacy_consent_policy"
const val TAG_PRIVACY_CONSENT_AGREE: String = "privacy_consent_agree"
const val TAG_PRIVACY_CONSENT_DECLINE: String = "privacy_consent_decline"
