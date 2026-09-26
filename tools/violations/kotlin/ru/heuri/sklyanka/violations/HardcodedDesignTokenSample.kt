package ru.heuri.sklyanka.violations

// Нарушает НФТ-47: литералы цвета, размера, радиуса и отступа вне пакета
// дизайн-системы. Правило — HardcodedDesignToken.

object HardcodedDesignTokenSample {
    val cardPadding = 16.dp
    val cardRadius = 18.dp
    val titleSize = 20.sp
    val accent = Color(0xFFB4512F)
    val taken = Color.Green
}
