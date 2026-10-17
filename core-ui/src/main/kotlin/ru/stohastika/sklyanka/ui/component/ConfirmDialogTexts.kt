package ru.stohastika.sklyanka.ui.component

/**
 * Подписи диалога подтверждения о двух действиях (§4.14, Р-87 п. 1).
 *
 * Диалог общий для любого подтверждения «остаться / сделать», поэтому текст приходит
 * параметрами из ресурсов вызывающего, а не из ресурсов системы: компонент отвечает
 * только за вид. Отдельный файл — единственная декларация класса в файле обязана
 * совпадать с его именем (MatchingDeclarationName).
 */
public data class ConfirmDialogTexts(
    public val title: String,
    public val message: String,
    public val confirmLabel: String,
    public val dismissLabel: String,
)
