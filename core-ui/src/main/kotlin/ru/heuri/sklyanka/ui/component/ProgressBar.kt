package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Полоса прогресса.
 *
 * [label] обязателен, а не опционален: цвет никогда не единственный носитель
 * значения (НФТ-33, критерий 9). Полоса, показанная без числа, — это полоса,
 * недоступная человеку, который её не видит, и человеку, который видит её иначе.
 *
 * Сама полоса убрана из дерева доступности (`clearAndSetSemantics`): читалке
 * достаточно подписи, а «прогресс-бар без значения» она озвучивает шумом.
 */
@Composable
fun SklyankaProgressBar(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    purpose: ProgressPurpose = ProgressPurpose.Day,
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
        Text(text = label, style = SklyankaType.Caption, color = colors.textMuted)
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
const val TAG_ONBOARDING_STEPS = "onboarding_steps"
