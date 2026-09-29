package ru.stohastika.sklyanka.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.semanticsLabel
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Аватар активного профиля в шапке — **индикатор, а не быстрый переключатель** (`ОВ-54`).
 *
 * Расхождение, которое пришлось решать. `Main.dc.html` и `MainProfile.dc.html` рисуют
 * в шапке кнопку с аватаром и шевроном вниз, а таблица действий `docs/spec/03-screens.md`
 * приписывает ей переключение профиля. ПРФ-19 утверждает обратное: отдельного быстрого
 * переключателя в шапке нет, переключение выполняется из настроек и должно быть осознанным
 * действием.
 *
 * Взят вариант (а) постановки: элемент остаётся узнаваемым и нажимаемым, но ведёт
 * **в список профилей настроек**, а не раскрывает меню. Шеврон при этом честен — он
 * обещает продолжение, и продолжение есть; смена же контекста по-прежнему требует
 * отдельного экрана и отдельного нажатия. Меню в шапке означало бы смену профиля
 * в одно касание — ровно то, что ПРФ-19 запрещает, и ровно то, после чего человек
 * отмечает чужой приём как свой.
 *
 * Инициал, а не имя целиком: имя стоит рядом надписью «Профиль · Имя» (ПРФ-11), и второй
 * его экземпляр в шапке занял бы место заголовка. Цвет кружка — цвет профиля у чужого
 * и мягкий «принято» у владельца (`docs/spec/08-design-system.md` §1.1), то есть аватар
 * тоже меняется при смене профиля и работает шестым носителем контекста.
 */
@Composable
fun ProfileAvatar(onOpenProfiles: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val chrome = LocalProfileChrome.current
    val foreign = chrome.foreignName != null
    Row(
        modifier = modifier
            .height(SklyankaSizes.ProfileAvatarButton)
            .background(colors.surface, SklyankaShapes.Pill)
            .border(SklyankaSizes.BorderThin, colors.lineInteractive, SklyankaShapes.Pill)
            .clickable(onClick = onOpenProfiles)
            .padding(horizontal = SklyankaSizes.ProfileAvatarPadding)
            .semanticsLabel(stringResource(R.string.cd_profile_avatar))
            .testTag(TAG_PROFILE_AVATAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSizes.ProfileAvatarPadding),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.ProfileAvatar)
                .background(
                    color = if (foreign) colors.accent else colors.takenSoft,
                    shape = SklyankaShapes.Pill,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chrome.initial(),
                style = SklyankaType.MicroStrong,
                color = if (foreign) colors.surface else colors.taken,
            )
        }
        ChevronDownGlyph(color = colors.textTertiary)
    }
}

/**
 * Первая буква имени профиля прописной.
 *
 * У владельца имени в оформлении нет (ПРФ-16), и инициал берётся из ресурса: «Я» —
 * подпись профиля владельца в макете настроек, а не первая буква его имени, которое
 * человек может переименовать (ПРФ-27 не решён).
 */
@Composable
private fun ProfileChromeState.initial(): String {
    val fallback = stringResource(R.string.profile_self_initial)
    val name = foreignName ?: return fallback
    return name.trim().take(1).uppercase(InterfaceLocale.VALUE).ifEmpty { fallback }
}

/** Шеврон вниз: он обещает продолжение, и продолжение есть — экран списка профилей. */
@Composable
private fun ChevronDownGlyph(color: Color, size: Dp = SklyankaSizes.ProfileAvatarChevron) {
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHEVRON_LEFT_X, side * CHEVRON_TOP_Y)
                lineTo(side * CHEVRON_MID_X, side * CHEVRON_BOTTOM_Y)
                lineTo(side * CHEVRON_RIGHT_X, side * CHEVRON_TOP_Y)
            },
            color = color,
            style = Stroke(
                width = side * CHEVRON_STROKE_RATIO,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** Пропорции шеврона из макета: `m6 9 6 6 6-6` в поле 24×24. */
private const val CHEVRON_LEFT_X = 0.25f
private const val CHEVRON_MID_X = 0.5f
private const val CHEVRON_RIGHT_X = 0.75f
private const val CHEVRON_TOP_Y = 0.375f
private const val CHEVRON_BOTTOM_Y = 0.625f
private const val CHEVRON_STROKE_RATIO = 0.09f

const val TAG_PROFILE_AVATAR = "profile_avatar"
