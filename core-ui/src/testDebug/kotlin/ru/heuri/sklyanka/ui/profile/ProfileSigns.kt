package ru.heuri.sklyanka.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.snapshot.SnapshotSurface
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaSpacing

/**
 * Шесть признаков чужого профиля на одном листе (ПРФ-11).
 *
 * Общий лист для эталонных снимков и для проверок состава: снимок фиксирует изменение,
 * проверка утверждает свойство, и утверждать они обязаны об одной и той же композиции —
 * иначе снимок показывает одно, а проверка молчит о другом.
 *
 * Признаки 2, 3 и 4 — подкраска шапки, надпись «Профиль · Имя» и баннер — по построению
 * неразделимы: шапка получает имя профиля из окружения и рисует все три сразу. Это
 * не упрощение листа, а само требование: отключаемый по отдельности признак — это признак,
 * который однажды отключат (ПРФ-10).
 *
 * Имя профиля берётся из заглушки каталога, а не из макета: правдоподобных имён людей среди
 * заглушек нет намеренно — снимок экрана разработчика не должен превращаться в медицинские
 * данные (НФТ-19). Шаблон надписи и баннера от этого не меняется, а проверяется именно он.
 */

/** Оформление чужого профиля для снимков и проверок. */
@Composable
internal fun foreignProfileChrome(): ProfileChromeState = ProfileChromeState.Foreign(
    name = stringResource(R.string.preview_profile_name),
    colour = ProfileColor.Terracotta,
)

/**
 * Поверхность одного признака: оформление профиля вокруг фрагмента листа.
 *
 * Полосы здесь нет: её ставит [ProfileChrome], и отдельный признак снимается без неё.
 */
@Composable
internal fun ProfileSignSurface(state: ProfileChromeState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalProfileChrome provides state) {
        SnapshotSurface(profile = state.accent) { content() }
    }
}

/** Признак 1: цветная полоса вверху экрана. */
@Composable
internal fun ProfileStripeSign() {
    ProfileStripe()
}

/** Признаки 2, 3 и 4: подкрашенная шапка, надпись «Профиль · Имя» и несворачиваемый баннер. */
@Composable
internal fun ProfileHeaderSign() {
    ScreenHeader(
        labels = ScreenHeaderLabels(
            title = stringResource(R.string.preview_screen_title),
            overline = stringResource(R.string.preview_date),
        ),
        trailing = { ProfileAvatar(onOpenProfiles = {}) },
    )
}

/** Признак 5: акцентный цвет действий. */
@Composable
internal fun ProfileActionSign() {
    PrimaryButton(text = stringResource(R.string.action_taken), onClick = {})
}

/** Признак 6: подпись «отметили вы» под фактическим временем отметки. */
@Composable
internal fun ProfileMarkedByYouSign(markedByYou: Boolean) {
    val context = LocalContext.current
    IntakeCard(
        state = IntakeCardState.Taken,
        labels = IntakeCardLabels(
            medication = context.getString(R.string.preview_medication),
            dose = context.getString(R.string.preview_dose),
            time = context.getString(R.string.preview_marked_time),
        ),
        markedByYou = markedByYou,
        actions = IntakeCardActions(),
    )
}

/**
 * Все шесть признаков разом, как их видит человек: полоса, шапка с надписью и баннером,
 * карточка с подписью и акцентное действие.
 *
 * Собирается через [ProfileChrome] — ту самую обёртку, что стоит в корне приложения,
 * а не через отдельную сборку «как будто»: лист обязан проверять то, что работает.
 */
@Composable
internal fun ProfileSignsSheet(state: ProfileChromeState) {
    ProfileChrome(state) {
        ProfileHeaderSign()
        Column(
            modifier = Modifier.fillMaxWidth().padding(SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            ProfileMarkedByYouSign(markedByYou = state.foreignName != null)
            ProfileActionSign()
        }
    }
}
