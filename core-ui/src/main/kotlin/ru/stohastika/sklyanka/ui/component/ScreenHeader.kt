package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Шапка экрана.
 *
 * Три признака чужого профиля появляются здесь **сами**, без участия экрана: подкраска
 * берётся из роли `profileHeader`, которая в теме владельца совпадает с обычным фоном,
 * а надпись «Профиль · Имя» и несворачиваемый баннер рисуются, когда оформление профиля
 * несёт имя (`ProfileChrome`, ПРФ-11, ПРФ-12).
 *
 * Имя профиля приходит из окружения, а не параметром. Параметр можно забыть на одном
 * экране из одиннадцати, и отказ будет незаметен: экран выглядит нормально, просто
 * индикация пропала. Отсюда же следует, что баннер нельзя отключить отдельно от надписи
 * (ПРФ-13): у шапки нет параметра, которым это делается, и не появится — избыточность
 * индикации и есть защита (ПРФ-10).
 *
 * Заголовок не обрезается и переносится на вторую строку при увеличенном системном
 * шрифте: шапка растёт по высоте (§6.3, критерий 5).
 */
@Composable
fun ScreenHeader(
    labels: ScreenHeaderLabels,
    modifier: Modifier = Modifier,
    kind: ScreenHeaderKind = ScreenHeaderKind.Root,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    val profileName = LocalProfileChrome.current.foreignName
    val overline = labels.overline
    val foreign = profileName != null
    // Вложенная шапка с действием «Назад» разводит действие и заголовок по строкам:
    // так нарисованы макеты вложенных экранов (`DataTransfer.dc.html`, `History.dc.html`,
    // `MedCard.dc.html`). В одной строке заголовок «Перенос данных» не помещался рядом
    // с кнопкой и переносился с висячим отступом (SKL-60, дефект пилота).
    val nestedWithBack = kind == ScreenHeaderKind.Nested && onBack != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.profileHeader)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = if (foreign) SklyankaSizes.HeaderTopPaddingProfile else SklyankaSizes.HeaderTopPadding,
                bottom = SklyankaSizes.HeaderBottomPadding,
            )
            .testTag(TAG_SCREEN_HEADER),
        // Зазор макета между строкой заголовка и баннером. В шапке владельца ребёнок
        // ровно один, и зазор ни на что не влияет.
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        if (nestedWithBack) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
            ) {
                BackAction(kind, onBack)
                Box(modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            if (!nestedWithBack && kind != ScreenHeaderKind.Root && onBack != null) {
                BackAction(kind, onBack)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
            ) {
                when {
                    profileName != null -> ProfileOverline(profileName)

                    overline != null -> Text(
                        text = overline.uppercased(),
                        style = SklyankaType.OverlineDate,
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = labels.title,
                    style = if (kind == ScreenHeaderKind.Modal) SklyankaType.BodyStrong else SklyankaType.TitleScreen,
                    color = colors.text,
                )
            }
            if (!nestedWithBack) trailing?.invoke()
        }
        if (profileName != null) {
            // Баннер стоит внутри подкрашенной шапки — так он нарисован в макете
            // `MainProfile.dc.html`. Действия у него нет: закрыть его нечем и незачем
            // (ПРФ-13). Вторая фраза называет последствие — куда попадёт отметка.
            ProfileBanner(profileName)
        }
        if (foreign) {
            // Нижняя граница появляется только у подкрашенной шапки: на обычном фоне
            // она отделяла бы шапку от того же самого цвета.
            HeaderDivider()
        }
    }
}

/**
 * Надпись «Профиль · Имя» (ПРФ-11, критерий 4).
 *
 * Публичная и единственная: экранов в контексте профиля одиннадцать, и текст, стиль
 * и тег надписи обязаны быть в одном месте. Собранная по месту копия расходится
 * с оригиналом молча — а расхождение здесь означает, что человек не узнал, чей экран
 * перед ним.
 */
@Composable
fun ProfileOverline(profileName: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.profile_label, profileName).uppercased(),
        style = SklyankaType.Overline,
        color = SklyankaTheme.colors.accent,
        modifier = modifier.testTag(TAG_PROFILE_LABEL),
    )
}

/**
 * Индикация чужого профиля для экрана, чья шапка собрана по месту, а не [ScreenHeader].
 *
 * Появилась по итогам SKL-26: исполнимая приёмка SKL-15 (`Структура сценария` «Признаки
 * чужого профиля есть на всех экранах профиля») показала, что карточка лекарства
 * надписи «Профиль · Имя» и баннера не показывала вовсе. Её шапка собрана по месту
 * намеренно (заголовок карточки — название лекарства, и рисовать его дважды нельзя),
 * и вместе с шапкой из экрана выпала индикация — то есть ровно та дыра в защите,
 * о которой говорит `ПРФ-12`.
 *
 * В профиле владельца не рисует ничего: отсутствие индикации само по себе сигнал
 * (`ПРФ-16`).
 */
@Composable
fun ProfileIndication(modifier: Modifier = Modifier) {
    val profileName = LocalProfileChrome.current.foreignName ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.profileHeader)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSizes.HeaderTopPaddingProfile,
                bottom = SklyankaSizes.HeaderBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        ProfileOverline(profileName)
        ProfileBanner(profileName)
        HeaderDivider()
    }
}

/**
 * Несворачиваемый баннер чужого профиля (ПРФ-13, критерий 5).
 *
 * Текст — дословно из `design/screens/MainProfile.dc.html`, а не короткая форма
 * текстовой спецификации («Это не ваши приёмы»): вторая фраза называет **последствие**,
 * и человек узнаёт не только, что он не там, но и что случится, если он нажмёт кнопку.
 *
 * Действия у баннера нет ни в каком виде. Кнопка «скрыть» превратила бы шесть признаков
 * в пять, а после первой недели — в ноль: закрытый однажды баннер человек закрывает
 * каждый раз не глядя.
 */
@Composable
private fun ProfileBanner(profileName: String) {
    Banner(
        text = stringResource(R.string.profile_banner, profileName),
        kind = BannerKind.Profile,
    )
}

@Composable
private fun BackAction(kind: ScreenHeaderKind, onBack: () -> Unit) {
    val label = when (kind) {
        ScreenHeaderKind.Modal -> stringResource(R.string.action_cancel)
        else -> stringResource(R.string.action_back)
    }
    QuietButton(text = label, onClick = onBack, muted = kind == ScreenHeaderKind.Modal)
}

@Composable
private fun HeaderDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.line),
    )
}

/**
 * Верхний регистр меток задаётся здесь, а не `uppercase()` по месту на экране.
 *
 * `TextStyle` в Compose не умеет `text-transform`, поэтому правило §2.2 («ВЕРХНИЙ
 * РЕГИСТР») исполняется преобразованием — но ровно в одном месте дизайн-системы и
 * с учётом локали: `uppercase()` без локали ломается на второй локали (НФТ-43).
 */
private fun String.uppercased(): String = uppercase(InterfaceLocale.VALUE)

const val TAG_SCREEN_HEADER = "screen_header"
const val TAG_PROFILE_LABEL = "profile_label"
