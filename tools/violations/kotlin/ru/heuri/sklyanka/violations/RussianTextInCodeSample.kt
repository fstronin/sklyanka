package ru.heuri.sklyanka.violations

// Нарушает НФТ-42: пользовательский текст обязан жить в strings.xml.
// Правило — RussianTextInCode.

object RussianTextInCodeSample {
    const val BUTTON_LABEL = "Принято"
    const val PLACEHOLDER = "Сегодня приёмов нет"
}
