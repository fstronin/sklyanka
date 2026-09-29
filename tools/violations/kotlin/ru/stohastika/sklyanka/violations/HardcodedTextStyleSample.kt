package ru.stohastika.sklyanka.violations

// Нарушает НФТ-47: начертание собрано по месту, мимо шкалы SklyankaType.
// Правило — HardcodedTextStyle.

object HardcodedTextStyleSample {
    val title = TextStyle(fontFamily = GolosText, fontWeight = FontWeight.SemiBold)
    val caption = SpanStyle(fontWeight = FontWeight.Medium)
}
