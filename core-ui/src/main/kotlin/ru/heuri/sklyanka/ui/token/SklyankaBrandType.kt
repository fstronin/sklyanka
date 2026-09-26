package ru.heuri.sklyanka.ui.token

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Типографика фирменного блока: заставка и логотип с названием.
 *
 * Почему отдельно от [SklyankaType]. Шкала §2 — типографика **интерфейса**: её
 * размеры перечислены спецификацией поимённо, и тест сверяет список целиком.
 * Название «Склянка» на заставке (42) и в логотипе (62) в эту шкалу не входит
 * и входить не должно: это не текст экрана, а знак, набранный буквами, и он
 * встречается ровно в двух местах (`design/screens/Splash.dc.html`,
 * `SklyankaLockup.dc.html`).
 *
 * Размеры — в `sp`, а не в `dp`: надписи заставки обязаны расти вместе с системным
 * шрифтом и не обрезаться на 130 % и 200 % (НФТ-31, критерий 14).
 */
object SklyankaBrandType {

    /** «Склянка» на заставке: Literata 42, полужирный, плотный трекинг. */
    val SplashTitle: TextStyle = brand(Literata, FontWeight.SemiBold, 42.sp, tracking = -0.015f)

    /** «приём лекарств» на заставке: прописными, разрядка 0,2em. */
    val SplashTagline: TextStyle = brand(GolosText, FontWeight.Normal, 12.sp, tracking = 0.2f)

    /** «Heuri» у нижнего края заставки. */
    val SplashVendor: TextStyle = brand(GolosText, FontWeight.Normal, 12.sp, tracking = 0f)

    /** «Склянка» в логотипе с названием: Literata 62. */
    val LockupTitle: TextStyle = brand(Literata, FontWeight.SemiBold, 62.sp, tracking = -0.02f)

    /** «приём лекарств» в логотипе: прописными, разрядка 0,2em, кегль крупнее заставочного. */
    val LockupTagline: TextStyle = brand(GolosText, FontWeight.Normal, 14.sp, tracking = 0.2f)

    /** Все стили фирменного блока — опора теста единиц измерения. */
    val all: List<TextStyle>
        get() = listOf(SplashTitle, SplashTagline, SplashVendor, LockupTitle, LockupTagline)

    /**
     * Межстрочный у фирменных надписей равен кеглю: в макете строка одна,
     * `line-height: 1`, и лишний межстрочный сдвинул бы блок относительно знака.
     */
    private fun brand(family: FontFamily, weight: FontWeight, size: TextUnit, tracking: Float) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size,
        letterSpacing = tracking.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
