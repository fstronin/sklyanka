package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Крупный блок-превью выбранного лекарства (§4.16, ОВ-94).
 *
 * Показывает, как лекарство будет узнаваться в списке и карточке; обновляется вместе
 * с черновиком; не нажимается. Тексты приходят строками: русского текста в `core-ui` нет
 * (НФТ-42), а заголовок и подпись собирает `feature-meds` из ресурсов и черновика.
 */
@Composable
fun MedicationPreview(
    form: MedicationForm,
    shape: TabletShape?,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.PreviewTile)
                .background(colors.accentSoft, SklyankaShapes.Preview),
            contentAlignment = Alignment.Center,
        ) {
            MedicationGlyph(
                form = form,
                shape = shape,
                color = colors.accent,
                size = SklyankaSizes.PreviewGlyph,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
        ) {
            Text(text = title, style = SklyankaType.BodyStrong, color = colors.text)
            Text(text = caption, style = SklyankaType.Caption, color = colors.textMuted)
        }
    }
}
