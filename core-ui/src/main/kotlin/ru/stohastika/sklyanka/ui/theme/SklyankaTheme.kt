package ru.stohastika.sklyanka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.ui.modifier.withCappedFontScale
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.tintedColors

/**
 * Набор ролей цвета, действующий в этом поддереве.
 *
 * `static`, а не обычный local: набор меняется редко (только при смене профиля или
 * входе в тёмную поверхность), и перечитывать его на каждом чтении незачем.
 */
val LocalSklyankaColors = staticCompositionLocalOf { lightColors() }

/**
 * Тема приложения.
 *
 * Как акцент доходит до компонента. Рассматривался путь «параметр компонента»
 * (`Button(accent = …)`) и отвергнут: он выполняет ПРФ-12 ровно до первого
 * компонента, которому цвет забыли передать, и отказ незаметен — экран выглядит
 * нормально, просто индикация пропала. Здесь подменяется значение в теме:
 * компонент читает `SklyankaTheme.colors.accent`, а что им окажется, решает тема.
 * Забыть нечего — читать неоткуда.
 *
 * [dark] — параметр, а не чтение системы здесь же. Так решено намеренно (ОВ-9, SKL-120):
 * тёмная среда интерфейса приходит **сверху** — из системной настройки на каркасе
 * приложения (Р-23, §9), — а четыре тёмные поверхности (напоминание, карточка проверки,
 * заставка, фирменный блок) передают `dark = true` всегда, в любой среде. Чтение
 * `isSystemInDarkTheme()` внутри темы смешало бы эти два случая: неизменяемо тёмная
 * поверхность стала бы зависеть от настройки телефона.
 *
 * [theme] — акцентная тема «Про» (§2.5, Р-31). Тема меняет семью акцента и ничего
 * больше: фон, текст, роли исхода и палитра профилей от неё не зависят, и в чужом
 * профиле подмена акцента идёт поверх ролей — цвета профиля теме не подчиняются (§2.2).
 */
@Composable
fun SklyankaTheme(
    profile: ProfileAccent = ProfileAccent.Owner,
    theme: AccentTheme = AccentTheme.DEFAULT,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(profile, theme, dark) {
        // Порядок веток — правило, а не приём: среда выбирает **роли**, а профиль
        // подкрашивает уже выбранные. Обратный порядок (среда после профиля) стирал бы
        // индикацию чужого профиля ночью — полоса, подкраска и акцент действий пропадали бы
        // там, где их легче всего не заметить (ПРФ-10, §9).
        val base = if (dark) darkColors(theme) else lightColors(theme)
        if (profile.isForeign) tintedColors(base, profile.color, dark) else base
    }
    CompositionLocalProvider(LocalSklyankaColors provides colors) {
        MaterialTheme(
            colorScheme = bridgeToMaterial(colors, dark),
            typography = sklyankaTypography(),
            content = content,
        )
    }
}

/**
 * Мост типографики для механизма M3 (SKL-69, §4.14).
 *
 * Диалоги выбора времени и даты берут начертание не из `style` экрана, а из
 * `MaterialTheme.typography`. Без моста M3 подставляет свой шрифт, и подписи пикера
 * читаются чужими. Мост односторонний, как и цветовой: шкала проекта задаёт M3.
 *
 * Числовые слоты (`displayLarge`, `displayMedium`, `headlineLarge`) получают
 * [SklyankaType.Display] с потолком масштаба 130 %: ими набраны цифры времени, и при
 * системном шрифте 200 % они не помещаются на экране (§6.3). Остальные слоты —
 * ближайшие стили Golos Text и масштабируются свободно.
 */
@Composable
private fun sklyankaTypography(): Typography {
    val display = SklyankaType.Display.withCappedFontScale()
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = SklyankaType.Headline,
        headlineSmall = SklyankaType.Subtitle,
        titleLarge = SklyankaType.Title,
        titleMedium = SklyankaType.BodyStrong,
        titleSmall = SklyankaType.Label,
        bodyLarge = SklyankaType.Body,
        bodyMedium = SklyankaType.BodyDefault,
        bodySmall = SklyankaType.Caption,
        labelLarge = SklyankaType.LabelStrong,
        labelMedium = SklyankaType.Label,
        labelSmall = SklyankaType.Micro,
    )
}

/** Доступ к ролям цвета из компонента: `SklyankaTheme.colors.accent`. */
object SklyankaTheme {
    val colors: SklyankaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSklyankaColors.current
}

/**
 * Мост к Material 3 — тонкий и односторонний.
 *
 * От `MaterialTheme` нужны только системные мелочи, которые иначе придут
 * фиолетовыми по умолчанию: цвет ряби нажатия, выделение текста в поле ввода, цвет
 * курсора. Роли M3 при этом **не** переиспользуются как источник цвета: `primary` и
 * `surfaceVariant` не совпадают по смыслу с ролями §1, и сведение одного к другому
 * даёт неверные пары контраста (§6.1). Направление всегда одно — токены проекта
 * задают Material, а не наоборот.
 */
private fun bridgeToMaterial(colors: SklyankaColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        outline = colors.line,
    )
} else {
    lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        outline = colors.line,
    )
}
