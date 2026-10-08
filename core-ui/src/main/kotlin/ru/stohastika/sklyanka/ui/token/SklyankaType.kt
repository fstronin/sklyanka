package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Типографическая шкала. Значения — `docs/v2/12-design.md` §2.1 и §2.2.
 *
 * Шкала — объект с константами, а не часть темы: она не зависит ни от профиля, ни
 * от среды. Лишний `CompositionLocal` — это лишний способ прочитать не то и лишняя
 * перерисовка; меняется только цвет, только он и живёт в теме (TDD §4.2).
 *
 * Где документ указывает два начертания на один размер («400 / 600»), заведено две
 * константы, а не параметр `bold: Boolean`: параметр открывает дорогу к «а можно
 * 500?», и шкала перестаёт быть шкалой.
 *
 * Все размеры — в `sp`: текст обязан расти вместе с системным шрифтом (НФТ-31).
 * Габариты и отступы — в `dp` и живут в [SklyankaSpacing] и [SklyankaSizes].
 */
object SklyankaType {

    // --- Заголовочная шкала, Literata 600 (§2.1) ---

    /** Время приёма на полноэкранном напоминании. Масштаб ограничен 130 % (§6.3). */
    val Display: TextStyle = heading(size = 64.sp, lineHeightRatio = 1.0f, tracking = -0.02f)

    /** Название лекарства на полноэкранном напоминании. */
    val Headline: TextStyle = heading(size = 32.sp, lineHeightRatio = 1.15f, tracking = -0.01f)

    /** Заголовок экрана: «Сегодня», «Лекарства», «История», «Настройки». */
    val TitleScreen: TextStyle = heading(size = 30.sp, lineHeightRatio = 1.15f, tracking = -0.01f)

    /** Название сущности и заголовок шага. Размер 27 sp из макета сведён к 28 sp. */
    val Title: TextStyle = heading(size = 28.sp, lineHeightRatio = 1.2f, tracking = -0.01f)

    /** Число в плитке показателя: «86 %», «9», «12». */
    val Stat: TextStyle = heading(size = 26.sp, lineHeightRatio = 1.0f, tracking = 0f)

    /** Заголовок пустого состояния. */
    val Subtitle: TextStyle = heading(size = 23.sp, lineHeightRatio = 1.25f, tracking = 0f)

    // --- Интерфейсная шкала, Golos Text (§2.2) ---

    /** Значение времени в строке расписания; доза под названием в напоминании. */
    val BodyLarge: TextStyle = ui(17.sp, FontWeight.SemiBold, lineHeightRatio = 1.3f, tracking = 0.01f)

    /** Текст в полях ввода и подпись основной кнопки. Ниже 16 sp опускать нельзя (§4.4). */
    val Body: TextStyle = ui(16.sp, FontWeight.Normal, lineHeightRatio = 1.4f)

    /** Он же в полужирном: ключевое значение в карточке. */
    val BodyStrong: TextStyle = ui(16.sp, FontWeight.SemiBold, lineHeightRatio = 1.4f)

    /** Рабочая лошадь: строка списка настроек, название лекарства в карточке приёма. */
    val BodyDefault: TextStyle = ui(15.sp, FontWeight.Normal, lineHeightRatio = 1.45f)

    /** Он же в полужирном: название лекарства в карточке приёма. */
    val BodyDefaultStrong: TextStyle = ui(15.sp, FontWeight.SemiBold, lineHeightRatio = 1.45f)

    /**
     * Он же в среднем начертании (500): подпись второстепенной кнопки тёмного
     * полноэкранного напоминания (§2.2, §5.1, ОВ-146).
     *
     * Среднее начертание — единственное новое сочетание шкалы; новых кеглей нет:
     * 15 sp — существующая ступень, а начертание назначает роль.
     */
    val BodyDefaultMedium: TextStyle = ui(15.sp, FontWeight.Medium, lineHeightRatio = 1.45f)

    /** Подписи кнопок и метки времени над группой. */
    val Label: TextStyle = ui(14.sp, FontWeight.Medium, lineHeightRatio = 1.3f)

    /**
     * Он же в полужирном.
     *
     * §2.2 задаёт для 14 sp два начертания («500 / 600»), а правило шкалы требует на
     * каждое — свою константу. В макетах это подпись основной кнопки (600) рядом с
     * подписью второстепенной (500).
     */
    val LabelStrong: TextStyle = ui(14.sp, FontWeight.SemiBold, lineHeightRatio = 1.3f)

    /** Вторичная строка карточки, доза и дозировка, текст баннера. */
    val Caption: TextStyle = ui(13.sp, FontWeight.Normal, lineHeightRatio = 1.35f)

    /** Он же в полужирном: время отметки рядом с галочкой. */
    val CaptionStrong: TextStyle = ui(13.sp, FontWeight.SemiBold, lineHeightRatio = 1.35f)

    /** Пояснения под строкой, статусы, подписи под полями. */
    val CaptionSmall: TextStyle = ui(12.sp, FontWeight.Normal, lineHeightRatio = 1.45f)

    /** Заголовок секции: «РАСПИСАНИЕ», «ОСТАТОК», «ПРОФИЛИ». Верхний регистр — §2.2. */
    val Overline: TextStyle = ui(12.sp, FontWeight.SemiBold, lineHeightRatio = 1.2f, tracking = 0.06f)

    /** Дата над заголовком «Сегодня». Верхний регистр — §2.2. */
    val OverlineDate: TextStyle = ui(12.sp, FontWeight.Normal, lineHeightRatio = 1.2f, tracking = 0.08f)

    /** Подписи нижней панели, легенда недели, подпись «отметили вы». */
    val Micro: TextStyle = ui(11.sp, FontWeight.Medium, lineHeightRatio = 1.3f)

    /** Он же в полужирном: подпись активной вкладки нижней панели. */
    val MicroStrong: TextStyle = ui(11.sp, FontWeight.SemiBold, lineHeightRatio = 1.3f)

    /** Дни недели в календаре месяца. Верхний регистр — §2.2. */
    val MicroOverline: TextStyle = ui(10.sp, FontWeight.Normal, lineHeightRatio = 1.2f, tracking = 0.16f)

    /** Все стили шкалы — опора теста полноты и единиц измерения. */
    val all: List<TextStyle>
        get() = listOf(
            Display, Headline, TitleScreen, Title, Stat, Subtitle,
            BodyLarge, Body, BodyStrong, BodyDefault, BodyDefaultStrong, BodyDefaultMedium,
            Label, LabelStrong, Caption, CaptionStrong, CaptionSmall,
            Overline, OverlineDate, Micro, MicroStrong, MicroOverline,
        )

    /**
     * Высота строки цифр тайм-пикера, равная полю M3 96×72 dp (ОВ-119).
     *
     * Не ступень шкалы, а геометрия моста: подложку слота 96×80 dp строка 72 dp
     * центрирует (ОВ-118). Вынесена токеном, потому что размер вне пакета токенов
     * запрещён НФТ-47.
     */
    val TimePickerLineHeight: TextUnit = 72.sp

    /**
     * Число-показатель внутри строки карточки (§2.3, ОВ-125): Literata 600 поверх
     * интерфейсного стиля. Не ступень шкалы, а начертание фрагмента `AnnotatedString`:
     * «86 из 100 приёмов (86 %)» набирается одним текстом, но цифры читаются показателем.
     */
    val NumberSpan: SpanStyle = SpanStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold)

    /** Стили, набираемые заголовочным шрифтом. Опора теста правила пары шрифтов (§2.3). */
    val headings: List<TextStyle>
        get() = listOf(Display, Headline, TitleScreen, Title, Stat, Subtitle)

    /**
     * Межстрочный в макетах задан долей от кегля, а Compose принимает его в `sp`.
     * Пересчёт здесь один на всю шкалу — иначе он разойдётся по двадцати константам.
     */
    private fun heading(size: TextUnit, lineHeightRatio: Float, tracking: Float): TextStyle =
        style(Literata, FontWeight.SemiBold, size, lineHeightRatio, tracking)

    private fun ui(size: TextUnit, weight: FontWeight, lineHeightRatio: Float, tracking: Float = 0f): TextStyle =
        style(GolosText, weight, size, lineHeightRatio, tracking)

    private fun style(
        family: FontFamily,
        weight: FontWeight,
        size: TextUnit,
        lineHeightRatio: Float,
        tracking: Float,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = (size.value * lineHeightRatio).sp,
        letterSpacing = tracking.em,
        // Системный отступ шрифта ломает плотную вёрстку макета: расстояния между
        // строками карточки в нём считались от самого текста, а не от его коробки.
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
