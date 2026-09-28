package ru.heuri.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag

/**
 * Чтение собранной композиции, общее для стендов списка и формы лекарств.
 *
 * Тексты собираются по немёрженому дереву: внутренние теги и подписи для чтения
 * с экрана лежат у узлов, которые слитая семантика прячет.
 */
@OptIn(ExperimentalTestApi::class)
internal object MedsProbe {

    /** Тексты всех узлов с тегом, в порядке разметки. */
    fun texts(test: ComposeUiTest, tag: String): List<String> = test
        .onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { textsOf(it) }

    /** Весь текст узла и его поддерева, в порядке разметки. */
    fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    /** Подписи узла для программы чтения с экрана: и текст, и описание содержания. */
    fun labelsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            textsOf(node)
}
