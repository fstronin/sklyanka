package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize

/**
 * Разметка одной надписи, разобранная на факты (SKL-25).
 *
 * Compose уже посчитал разметку: сколько получилось строк, где проходит каждая
 * граница и поместилась ли разметка в отведённую высоту. Отсюда два свойства,
 * которые НФТ-31 требует, а снимок не доказывает.
 */
internal data class TextFacts(
    /** Исходная строка целиком. */
    val text: String,
    /** Строки разметки в том виде, в каком их перенесла вёрстка. */
    val lines: List<String>,
    /** Разметка не поместилась в отведённую высоту: нижние строки за обрезкой. */
    val cut: Boolean,
) {

    /**
     * Перенос разрезал слово.
     *
     * Перенос по пробелу оставляет пробел в конце строки, перенос по дефису — дефис.
     * Буква или цифра с обеих сторон границы означает, что слово разрезали посередине:
     * «При/нят», «заканчивае/тся».
     */
    val wordBroken: Boolean
        get() = lines.indices.drop(1).any { index ->
            val tail = lines[index - 1].lastOrNull()
            val head = lines[index].firstOrNull()
            tail != null && head != null && tail.isLetterOrDigit() && head.isLetterOrDigit()
        }

    /** Что именно не так с этой надписью, или `null`, если всё в порядке. */
    fun complaint(): String? {
        val problems = listOfNotNull(
            "cut off".takeIf { cut },
            "broken inside a word".takeIf { wordBroken },
        )
        if (problems.isEmpty()) return null
        return "the text \"$text\" is ${problems.joinToString(" and ")}: [${lines.joinToString(" | ")}]"
    }
}

/** Разметка всех надписей композиции. */
internal fun SemanticsNodeInteractionsProvider.textFacts(): List<TextFacts> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.textFacts() }

/** Габариты всех нажимаемых областей композиции. */
internal fun SemanticsNodeInteractionsProvider.touchTargets(): List<IntSize> =
    onAllNodes(hasClickAction()).fetchSemanticsNodes().map { node -> node.size }

private fun SemanticsNode.textFacts(): TextFacts? {
    val action = SemanticsActions.GetTextLayoutResult
    if (!config.contains(action)) return null
    val results = mutableListOf<TextLayoutResult>()
    config[action].action?.invoke(results)
    val layout = results.firstOrNull() ?: return null
    val text = layout.layoutInput.text.text
    return TextFacts(
        text = text,
        lines = (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        },
        cut = layout.didOverflowHeight,
    )
}
