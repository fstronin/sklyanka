package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Метка «Про» — слово, искра и мягкий акцент (§5.9, Р-67 п. 5, Р-79).
 *
 * Ставится у элемента, ведущего к платной функции, чтобы человек узнавал о плате **до**
 * нажатия. Замка нет: элемент под меткой нажимаем и открывает витрину, а не отказ (П-7).
 *
 * Метка **не элемент управления**: нажатие у строки одно — сама строка, поэтому размера цели
 * нажатия у метки нет, семантики выбора и состояния «недоступна» она не несёт, и в чужих
 * профиль-цветах подкрашивается как всё семейство мягкого акцента (Р-79 пп. 2–3).
 *
 * Собрана из токенов, а не из [SklyankaChip]: у чипа высота и состояния выбора, у метки —
 * ни того, ни другого; подложка здесь `accentSoft`, а не заливка `accent`, потому что заливка
 * значит «выбрано» и «главное действие», и четыре залитые метки в форме лекарства обесценили бы
 * акцент (Р-79, «Почему»).
 *
 * Чтению с экрана объявляется только слово: искра декоративна и смысла не несёт (НФТ-33).
 *
 * **В бесплатной сборке метки нет.** [LocalProUiEnabled] по умолчанию `false`, и тогда
 * компонент не рисует ничего: ни плашки, ни слова, ни места в строке. Вызовы метки при этом
 * остаются на местах — платный вариант сборки включает флаг, и метки возвращаются без правки
 * экранов (Р-86, ADR-0016).
 */
@Composable
fun ProMark(modifier: Modifier = Modifier) {
    if (!LocalProUiEnabled.current) return
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .clip(SklyankaShapes.Pill)
            .background(colors.accentSoft)
            .padding(horizontal = SklyankaSizes.ChipPadding, vertical = SklyankaSizes.ProMarkPadding)
            .semantics(mergeDescendants = true) {}
            .testTag(TAG_PRO_MARK),
    ) {
        SparkGlyph(color = colors.onAccentSoft, modifier = Modifier.alignByBaseline())
        Spacer(modifier = Modifier.width(SklyankaSpacing.Micro))
        Text(
            text = stringResource(R.string.pro_mark),
            style = SklyankaType.CaptionStrong,
            color = colors.onAccentSoft,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** Тег метки для экранных проверок и кадра. */
const val TAG_PRO_MARK = "pro_mark"
