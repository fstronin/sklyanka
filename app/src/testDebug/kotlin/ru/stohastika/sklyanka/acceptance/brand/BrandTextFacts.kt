package ru.stohastika.sklyanka.acceptance.brand

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow

/**
 * Разметка одной надписи, разобранная на факты (приём SKL-25).
 *
 * Compose уже посчитал разметку: сколько строк и поместилась ли она в отведённые ширину
 * и высоту. «Видно целиком» без устройства доказывается именно этим, а снимок обрезанный
 * текст показывает так же, как целый.
 *
 * Копия из `core-ui` (`ui/component/TextLayoutFacts.kt`) намеренная: тестовые наборы
 * модулей друг друга не видят, а шаги обязаны читать ту же разметку, что и сценарий.
 * Ширина читается `isLineEllipsized`, а не `didOverflowWidth`: флаг ширины шумит
 * на округлении трекинга у строки, поместившейся целиком.
 */
internal data class BrandTextFact(
    val text: String,
    val lines: List<String>,
    val cutHeight: Boolean,
    val ellipsized: Boolean = false,
    val maxLines: Int = Int.MAX_VALUE,
    val overflow: TextOverflow = TextOverflow.Clip,
    /**
     * Сколько символов текста видно до многоточия. У усечённой надписи это остаток,
     * который вёрстка оставила от названия; нулевой остаток означает, что имя исчезло
     * целиком, и разрешённое усечение таким быть не может (находка визревью SKL-91).
     */
    val visibleCharacters: Int = text.length,
) {
    /**
     * Надпись обрезана.
     *
     * Высота — прямой признак; усечение многоточием считается обрезкой, кроме случая,
     * разрешённого самим автором разметки: одна строка и многоточие — так усекается
     * название лекарства (§6.3 дизайн-системы).
     */
    val cut: Boolean get() = cutHeight || (ellipsized && !allowedEllipsis)

    /** Усечение разрешено: ровно то место, которое дизайн-система назвала допустимым. */
    val allowedEllipsis: Boolean
        get() = ellipsized && overflow == TextOverflow.Ellipsis && maxLines == ONE_LINE

    /**
     * Перенос разрезал слово: буква или цифра на стыке строк.
     *
     * Языки без пробелов (китайский) переносятся по знакам — иероглиф на границе
     * разрывом не считается; усечённая строка обрезана многоточием, и стык с ней
     * ни о чём не говорит.
     */
    val wordBroken: Boolean
        get() = !ellipsized &&
            lines.indices.drop(1).any { index ->
                val tail = lines[index - 1].lastOrNull()
                val head = lines[index].firstOrNull()
                tail != null &&
                    head != null &&
                    tail.isLetterOrDigit() &&
                    head.isLetterOrDigit() &&
                    !tail.isSyllabic() &&
                    !head.isSyllabic()
            }

    /**
     * Что именно не так с надписью, или `null`, если всё в порядке.
     *
     * Разрешённое усечение (название лекарства, §6.3) дефектом не считается; отчёт
     * о нём можно запросить параметром — им пользуется сценарий приёмки, доказывающий,
     * что усечение вообще случилось и именно там, где разрешено.
     */
    fun complaint(reportAllowedEllipsis: Boolean = false): String? {
        val problems = listOfNotNull(
            // Усечённая строка поднимает и `didOverflowHeight`: об обрезке по высоте
            // говорится только там, где усечения нет.
            "cut off".takeIf { cutHeight && !ellipsized },
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
internal fun SemanticsNodeInteractionsProvider.brandTextFacts(): List<BrandTextFact> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node -> node.brandTextFact() }

/** Разметка одной надписи узла: ею пользуется локализационная матрица (SKL-91). */
internal fun SemanticsNode.brandTextFact(): BrandTextFact? {
    val action = SemanticsActions.GetTextLayoutResult
    if (!config.contains(action)) return null
    val results = mutableListOf<TextLayoutResult>()
    config[action].action?.invoke(results)
    val layout = results.firstOrNull() ?: return null
    val text = layout.layoutInput.text.text
    val ellipsizedLine = (0 until layout.lineCount).lastOrNull { line -> layout.isLineEllipsized(line) }
    return BrandTextFact(
        text = text,
        lines = (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        },
        cutHeight = layout.didOverflowHeight,
        ellipsized = ellipsizedLine != null,
        maxLines = layout.layoutInput.maxLines,
        overflow = layout.layoutInput.overflow,
        visibleCharacters = ellipsizedLine?.let { line -> layout.getLineEnd(line, visibleEnd = true) } ?: text.length,
    )
}

/** Письменности, переносящиеся по знакам: граница строки в них — не граница слова. */
private fun Char.isSyllabic(): Boolean = Character.UnicodeScript.of(code) in SYLLABIC_SCRIPTS

private val SYLLABIC_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)
