package ru.heuri.sklyanka.ui.token

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Заглушка темы.
 *
 * Настоящие токены — цвета, типографика Literata и Golos Text, радиусы, отступы —
 * приходят в SKL-05. Здесь зафиксировано только место, где литералы разрешены:
 * пакет ru.heuri.sklyanka.ui.token, и только он (НФТ-47).
 */
@Composable
fun SklyankaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color(0xFFFBF7F1),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF241F1B),
            primary = Color(0xFFB4512F),
        ),
        content = content,
    )
}
