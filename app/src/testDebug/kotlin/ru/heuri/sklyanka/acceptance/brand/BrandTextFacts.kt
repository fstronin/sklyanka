package ru.heuri.sklyanka.acceptance.brand

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult

/**
 * Разметка одной надписи, разобранная на факты (приём SKL-25).
 *
 * Compose уже посчитал разметку: сколько строк и поместилась ли она в отведённую ширину
 * и высоту. «Видно целиком» без устройства доказывается именно этим, а снимок обрезанный
 * текст показывает так же, как целый.
 *
 * Копия из `core-ui` (`ui/component/TextLayoutFacts.kt`) намеренная: тестовые наборы
 * модулей друг друга не видят, а шаги обязаны читать ту же разметку, что и сценарий.
 */
internal data class BrandTextFact(
    val text: String,
    val lines: List<String>,
    val cutHeight: Boolean,
    val cutWidth: Boolean,
    val lineWidth: Float,
    val layoutWidth: Float,
) {
    /**
     * Надпись обрезана.
     *
     * Высота — прямой признак; ширина — только когда строка шире своей разметки.
     * Флаг `didOverflowWidth` у Compose шумит: у одной строки, поместившейся целиком,
     * он поднимается из-за трекинга и округления (приём SKL-25 берёт одну высоту).
     */
    val cut: Boolean get() = cutHeight || (layoutWidth > 0f && lineWidth > layoutWidth + WIDTH_EPS)

    /** Что именно не так с надписью, или `null`, если всё в порядке. */
    fun complaint(): String? = if (cut) {
        "cut off (height=$cutHeight, width=$cutWidth, line=$lineWidth, layout=$layoutWidth): " +
            "[${lines.joinToString(" | ")}]"
    } else {
        null
    }
}

/** Разметка всех надписей композиции. */
internal fun SemanticsNodeInteractionsProvider.brandTextFacts(): List<BrandTextFact> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.brandTextFact() }

private const val WIDTH_EPS = 1f

private fun SemanticsNode.brandTextFact(): BrandTextFact? {
    val action = SemanticsActions.GetTextLayoutResult
    if (!config.contains(action)) return null
    val results = mutableListOf<TextLayoutResult>()
    config[action].action?.invoke(results)
    val layout = results.firstOrNull() ?: return null
    val text = layout.layoutInput.text.text
    return BrandTextFact(
        text = text,
        lines = (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        },
        cutHeight = layout.didOverflowHeight,
        cutWidth = layout.didOverflowWidth,
        lineWidth = if (layout.lineCount > 0) layout.multiParagraph.getLineWidth(0) else 0f,
        layoutWidth = layout.size.width.toFloat(),
    )
}
