package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Полоса цвета профиля в самом верху экрана — первый признак чужого профиля
 * (`docs/norm/06-design.md` §4.10, ПРФ-11).
 *
 * Не нажимается, не анимируется, не скрывается при прокрутке. В профиле владельца
 * отсутствует полностью: роль `profileStripe` там прозрачна, и компонент не рисует
 * ничего — её появление и есть сигнал (ПРФ-16, критерий 2).
 *
 * Обратите внимание: экран не решает, показывать полосу или нет. Он всегда ставит
 * её вверху, а решает тема. Решение на стороне экрана — это одиннадцать мест, где
 * можно забыть.
 */
@Composable
fun ProfileStripe(modifier: Modifier = Modifier) {
    val stripe = SklyankaTheme.colors.profileStripe
    if (stripe.alpha == 0f) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SklyankaSizes.ProfileStripeHeight)
            .background(stripe)
            .testTag(TAG_PROFILE_STRIPE),
    )
}

const val TAG_PROFILE_STRIPE = "profile_stripe"
