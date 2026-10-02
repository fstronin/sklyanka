package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize

/**
 * Разметка одной надписи, разобранная на факты (SKL-25).
 *
 * Compose уже посчитал разметку: сколько получилось строк, где проходит каждая
 * граница, поместилась ли разметка в отведённую высоту и была ли строка усечена.
 * Отсюда три свойства, которые НФТ-31 и локализационная матрица (SKL-91) требуют,
 * а снимок не доказывает.
 */
internal data class TextFacts(
    /** Исходная строка целиком. */
    val text: String,
    /** Строки разметки в том виде, в каком их перенесла вёрстка. */
    val lines: List<String>,
    /** Разметка не поместилась в отведённую высоту: нижние строки за обрезкой. */
    val cut: Boolean,
    /** Хотя бы одна строка усечена многоточием (`isLineEllipsized`). */
    val ellipsized: Boolean = false,
    /** Предел строк, выставленный разметке; у безлимитной — максимум. */
    val maxLines: Int = Int.MAX_VALUE,
    /** Что делает разметка с не поместившимся: усекает, обрезает или показывает целиком. */
    val overflow: TextOverflow = TextOverflow.Clip,
    /**
     * Сколько символов видно до многоточия. Нулевой остаток означает, что надпись
     * исчезла за «…» целиком, — такое усечение не читается и разрешённым не бывает.
     */
    val visibleCharacters: Int = text.length,
) {

    /**
     * Усечение разрешено самим автором разметки: одна строка и многоточие.
     *
     * §6.3 дизайн-системы разрешает это ровно одному месту — названию лекарства
     * в карточке. Разрешение не перечисляет тексты: название — данные человека,
     * а не перевод, и опознаётся по явному выбору разметки, а не по строке.
     */
    val allowedEllipsis: Boolean
        get() = ellipsized && overflow == TextOverflow.Ellipsis && maxLines == ONE_LINE

    /**
     * Перенос разрезал слово.
     *
     * Перенос по пробелу оставляет пробел в конце строки, перенос по дефису — дефис.
     * Буква или цифра с обеих сторон границы означает, что слово разрезали посередине:
     * «При/нят», «заканчивае/тся». Языки без пробелов (китайский) переносятся по знакам,
     * и иероглиф на границе разрывом не считается; усечённая строка обрезана
     * многоточием, и стык с ней ни о чём не говорит.
     */
    val wordBroken: Boolean
        get() = !ellipsized &&
            lines.indices.drop(1).any { index ->
                val tail = lines[index - 1].lastOrNull()
                val head = lines[index].firstOrNull()
                tail != null &&
                    head != null &&
                    tail.isWordCharacter() &&
                    head.isWordCharacter() &&
                    !tail.isSyllabic() &&
                    !head.isSyllabic()
            }

    /**
     * Что именно не так с этой надписью, или `null`, если всё в порядке.
     *
     * Разрешённое усечение (название лекарства, §6.3) дефектом не считается; отчёт
     * о нём можно запросить параметром — им пользуется сценарий приёмки, доказывающий,
     * что усечение вообще случилось и именно там, где разрешено.
     */
    fun complaint(reportAllowedEllipsis: Boolean = false): String? {
        val problems = listOfNotNull(
            // Усечённая строка поднимает и `didOverflowHeight`: об обрезке по высоте
            // говорится только там, где усечения нет.
            "cut off".takeIf { cut && !ellipsized },
            "truncated with an ellipsis".takeIf { ellipsized && (!allowedEllipsis || reportAllowedEllipsis) },
            "broken inside a word".takeIf { wordBroken },
        )
        if (problems.isEmpty()) return null
        return "the text \"$text\" is ${problems.joinToString(" and ")}: [${lines.joinToString(" | ")}]"
    }

    private companion object {
        const val ONE_LINE = 1
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
    val ellipsizedLine = (0 until layout.lineCount).lastOrNull { line -> layout.isLineEllipsized(line) }
    return TextFacts(
        text = text,
        lines = (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        },
        cut = layout.didOverflowHeight,
        ellipsized = ellipsizedLine != null,
        maxLines = layout.layoutInput.maxLines,
        overflow = layout.layoutInput.overflow,
        visibleCharacters = ellipsizedLine?.let { line -> layout.getLineEnd(line, visibleEnd = true) } ?: text.length,
    )
}

/** Письменности, переносящиеся по знакам: граница строки в них — не граница слова. */
private fun Char.isSyllabic(): Boolean = Character.UnicodeScript.of(code) in SYLLABIC_SCRIPTS

private fun Char.isWordCharacter(): Boolean = isLetterOrDigit()

private val SYLLABIC_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)
