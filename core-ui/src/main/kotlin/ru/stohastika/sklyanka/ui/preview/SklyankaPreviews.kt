package ru.stohastika.sklyanka.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Окружения превью.
 *
 * Две строки аннотации и три темы внутри [PreviewEnvironments] дают матрицу из шести
 * снимков на компонент: светлая, чужой профиль и тёмная среда — каждая при обычном
 * масштабе шрифта и при 200 % (TDD §8).
 *
 * Имена окружений английские: русский текст в коде запрещён правилом сборки
 * (НФТ-42), а имя превью — не текст интерфейса, а подпись для разработчика.
 */
@Preview(name = "Default", widthDp = PREVIEW_WIDTH, showBackground = true)
@Preview(name = "Font 200%", widthDp = PREVIEW_WIDTH, fontScale = 2.0f, showBackground = true)
annotation class SklyankaPreviews
