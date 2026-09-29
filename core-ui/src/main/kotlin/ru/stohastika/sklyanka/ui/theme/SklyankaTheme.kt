package ru.stohastika.sklyanka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import ru.stohastika.sklyanka.ui.token.SklyankaColors
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
 * [dark] — параметр, а не системная тема устройства. Это прямое следствие §5
 * документа: тёмными бывают полноэкранное напоминание и знак, а не приложение
 * целиком. `isSystemInDarkTheme()` в проекте не вызывается — иначе человек с тёмной
 * системной темой получит одиннадцать экранов, которых никто не рисовал (ОВ-9).
 */
@Composable
fun SklyankaTheme(
    profile: ProfileAccent = ProfileAccent.Owner,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(profile, dark) {
        when {
            dark -> darkColors()
            profile.isForeign -> tintedColors(lightColors(), profile.color)
            else -> lightColors()
        }
    }
    CompositionLocalProvider(LocalSklyankaColors provides colors) {
        MaterialTheme(
            colorScheme = bridgeToMaterial(colors, dark),
            content = content,
        )
    }
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
        onPrimary = colors.background,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        outline = colors.line,
    )
} else {
    lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.surface,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        outline = colors.line,
    )
}
