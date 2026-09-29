package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Подпись для чтения с экрана у графики без видимого текста (НФТ-30).
 *
 * Отдельная функция, чтобы формулировка приходила строкой из ресурсов, а не
 * собиралась в компоненте: текст для читалки — такой же текст интерфейса, как
 * подпись кнопки, и живёт он в `strings.xml` (НФТ-42).
 */
internal fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
