package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Полоса прогресса.
 *
 * [label] обязателен, а не опционален: цвет никогда не единственный носитель
 * значения (НФТ-33, критерий 9). Полоса, показанная без числа, — это полоса,
 * недоступная человеку, который её не видит, и человеку, который видит её иначе.
 *
 * Сама полоса убрана из дерева доступности (`clearAndSetSemantics`): читалке
 * достаточно подписи, а «прогресс-бар без значения» она озвучивает шумом.
 *
 * [action] — необязательная ссылка в строке подписи (ОВ-84): при `null` строка
 * остаётся прежним текстом, а с действием вся строка становится одной целью
 * нажатия, невидимо расширенной до 44 dp (§3.3). Шеврон нарисован и в семантику
 * не попадает — чтением с экрана он не объявляется.
 */
@Composable
fun SklyankaProgressBar(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    purpose: ProgressPurpose = ProgressPurpose.Day,
    action: ProgressAction? = null,
) {
    val colors = SklyankaTheme.colors
    val fill = when (purpose) {
        ProgressPurpose.StockCritical -> colors.warning
        else -> colors.accent
    }
    Column(
        modifier = modifier.testTag(TAG_PROGRESS_BAR),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.ProgressHeight)
                .background(colors.progressTrack, SklyankaShapes.Pill)
                .clearAndSetSemantics { },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(fill, SklyankaShapes.Pill),
            )
        }
        if (action == null) {
            Text(text = label, style = SklyankaType.Caption, color = colors.textMuted)
        } else {
            ProgressLinkRow(label = label, action = action)
        }
    }
}

/** Строка подписи со ссылкой: слева подпись, справа слово и шеврон (ОВ-84). */
@Composable
private fun ProgressLinkRow(label: String, action: ProgressAction) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = action.onClick)
            .minTouchTarget()
            .testTag(TAG_PROGRESS_LINK),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SklyankaType.Caption,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(text = action.label, style = SklyankaType.LabelStrong, color = colors.accent)
        ChevronLinkGlyph(color = colors.accent)
    }
}

/** «›» ссылки: нарисован и потому в дерево доступности не попадает. */
@Composable
private fun ChevronLinkGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.Chevron) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHEVRON_LEFT, side * CHEVRON_TOP)
                lineTo(side * CHEVRON_RIGHT, side * MIDDLE)
                lineTo(side * CHEVRON_LEFT, side * CHEVRON_BOTTOM)
            },
            color = color,
            style = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val STROKE_RATIO = 0.09f
private const val MIDDLE = 0.5f
private const val CHEVRON_LEFT = 0.28f
private const val CHEVRON_RIGHT = 0.72f
private const val CHEVRON_TOP = 0.34f
private const val CHEVRON_BOTTOM = 0.66f

/**
 * Полоса без собственной подписи: число показано рядом, в шапке блока.
 *
 * Расширение каталога, объявленное вслух — как состояние `Error` у поля ввода (SKL-09).
 * У [SklyankaProgressBar] подпись обязательна, и это правильно для полосы, стоящей
 * отдельно. Но в блоке «Курс» карточки лекарства число «осталось 17 дней» нарисовано
 * справа от заголовка (`design/screens/MedCard.dc.html`), и вторая такая же подпись под
 * полосой — не доступность, а дубль.
 *
 * Поэтому подпись здесь не исчезает, а переезжает: [description] обязателен и уходит
 * в дерево доступности вместо полосы. Полосы без числа по-прежнему не существует
 * (НФТ-33) — оно либо под ней, либо рядом с ней и в подписи для чтения с экрана.
 */
@Composable
fun SklyankaProgressTrack(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    purpose: ProgressPurpose = ProgressPurpose.Day,
) {
    val colors = SklyankaTheme.colors
    val fill = when (purpose) {
        ProgressPurpose.StockCritical -> colors.warning
        else -> colors.accent
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SklyankaSizes.ProgressHeight)
            .background(colors.progressTrack, SklyankaShapes.Pill)
            .testTag(TAG_PROGRESS_TRACK)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fill, SklyankaShapes.Pill),
        )
    }
}

/**
 * Индикатор шагов онбординга: сегменты вместо сплошной полосы (§4.7).
 *
 * Отдельным компонентом, а не параметром полосы: у него нет доли заполнения, есть
 * пройденные и непройденные шаги, и попытка выразить это числом от нуля до единицы
 * даёт «полтора шага».
 */
@Composable
fun OnboardingSteps(total: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier.testTag(TAG_ONBOARDING_STEPS),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SklyankaSpacing.Micro)
                    .background(
                        if (index < current) colors.accent else colors.progressTrack,
                        SklyankaShapes.Pill,
                    ),
            )
        }
    }
}

const val TAG_PROGRESS_BAR = "progress_bar"
const val TAG_PROGRESS_TRACK = "progress_track"
const val TAG_PROGRESS_LINK = "progress_link"
const val TAG_ONBOARDING_STEPS = "onboarding_steps"
