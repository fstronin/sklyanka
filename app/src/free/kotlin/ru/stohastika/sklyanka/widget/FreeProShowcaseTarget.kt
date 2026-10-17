package ru.stohastika.sklyanka.widget

import androidx.compose.runtime.Composable
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import javax.inject.Inject

/**
 * Слот витрины «Про» на экране настройки виджета в бесплатной сборке (Р-86, `ADR-0016`).
 *
 * Виджет теперь доступен всем, и гейт установки проходит; показать поверх экрана нечего,
 * поэтому слот пуст. Это не «заглушка вместо поведения»: поведения нет по решению продукта —
 * в `free` не существует ни шторки, ни покупки (П-7).
 */
class FreeProShowcaseTarget @Inject constructor() : ProShowcaseTarget {

    @Composable
    override fun Showcase(visible: Boolean, benefit: ProBenefit, onDismiss: () -> Unit) = Unit
}
