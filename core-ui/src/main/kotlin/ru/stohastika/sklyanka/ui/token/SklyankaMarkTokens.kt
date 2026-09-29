package ru.stohastika.sklyanka.ui.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Геометрия и цвета фирменного знака — донца толстого стекла, вид сверху.
 *
 * Почему это живёт в пакете токенов. Знак — набор чисел: радиусы колец, углы дуг,
 * прозрачности слоёв. Оставить их в рисующем компоненте значит поставить полсотни
 * литералов размера за пределами дизайн-системы, а НФТ-47 запрещает ровно это.
 * Здесь же они законны, здесь их видно списком, и здесь их правят осознанно.
 *
 * **Одно поле на всю лестницу вариантов.** Все значения заданы на поле 200×200 —
 * ровно том, в котором знак нарисован в макетах `design/screens/SklyankaMark.dc.html`,
 * `SklyankaIcon.dc.html` и `SklyankaScale.dc.html`. Рисующий код переводит их в пиксели
 * умножением на `сторона / 200`. Отсюда же выводятся векторные ресурсы иконки
 * приложения и значка уведомления: у знака один источник геометрии, а не пять копий.
 *
 * Числа взяты из макета дословно, включая те, что выглядят случайными (96,78 центра
 * дуги отражения). Они и есть случайные: это пересчёт команды `A` из SVG в центр,
 * радиус и углы, которых Compose требует, а SVG — нет. Подгонять их «на глаз»
 * нельзя — разойдётся с макетом.
 */
object SklyankaMarkTokens {

    /** Сторона поля, в котором заданы все координаты знака. */
    const val FIELD: Float = 200f

    /** Центр поля. */
    const val CENTER: Float = 100f

    // --- Стеклянная отрисовка (docs/spec/08-design-system.md §7) ---

    /** Ободок — внешняя граница донца. */
    const val RIM_RADIUS: Float = 86f
    const val RIM_STROKE: Float = 3.5f

    /**
     * Три концентрических кольца.
     *
     * Толщина убывает от края к центру, а плотность нарастает: 7 при 0,13; 2 при 0,26;
     * 2,5 при 0,5. Это и даёт ощущение толщи стекла — ближние слои светлее и тоньше.
     */
    val RING_RADII: List<Float> = listOf(66f, 47f, 28f)
    val RING_STROKES: List<Float> = listOf(7f, 2f, 2.5f)
    val RING_ALPHAS_ON_DARK: List<Float> = listOf(0.13f, 0.26f, 0.5f)
    val RING_ALPHAS_ON_LIGHT: List<Float> = listOf(0.09f, 0.2f, 0.42f)

    /** Центральная точка. */
    const val DOT_RADIUS: Float = 8f
    const val DOT_ALPHA_ON_DARK: Float = 0.92f
    const val DOT_ALPHA_ON_LIGHT: Float = 0.72f

    /**
     * Тело: радиальный градиент, смещённый в верхний левый угол.
     *
     * В SVG он задан долями описанного прямоугольника круга (cx 34 %, cy 28 %, r 78 %
     * от стороны 172). Здесь доли уже пересчитаны в координаты поля 200 — Compose
     * принимает центр и радиус, а не доли.
     */
    val BODY_GRADIENT_CENTER: Offset = Offset(72.48f, 62.16f)
    const val BODY_GRADIENT_RADIUS: Float = 134.16f
    val BODY_STOPS_ON_DARK: List<Float> = listOf(0f, 0.52f, 1f)
    val BODY_ALPHAS_ON_DARK: List<Float> = listOf(0.24f, 0.07f, 0.015f)
    val BODY_STOPS_ON_LIGHT: List<Float> = listOf(0f, 0.58f, 1f)
    val BODY_ALPHAS_ON_LIGHT: List<Float> = listOf(0.95f, 0.35f, 0.06f)

    /** Ободок залит линейным градиентом: свет идёт наискось, из верхнего левого угла. */
    val RIM_GRADIENT_START: Offset = Offset(44.96f, 27.76f)
    val RIM_GRADIENT_END: Offset = Offset(161.92f, 175.68f)
    val RIM_STOPS: List<Float> = listOf(0f, 0.46f, 1f)
    val RIM_ALPHAS_ON_DARK: List<Float> = listOf(0.85f, 0.16f, 0.42f)
    val RIM_ALPHAS_ON_LIGHT: List<Float> = listOf(0.16f, 0.5f, 0.22f)

    /**
     * Световой серп по верхнему краю — кольцевой сектор между радиусами 76 и 67.
     *
     * В макете это заливка `M25.1 86.8 A76 ... L88.4 34 A67 ... Z`. Внешняя дуга идёт
     * по часовой стрелке от 190° к 260° (0° — три часа, углы растут вниз), внутренняя
     * возвращается обратно. Спецификация называет те же 70° «от 25° до 87°», считая
     * от другой оси; проверять следует по точкам, а не по названию.
     */
    const val CRESCENT_OUTER_RADIUS: Float = 76f
    const val CRESCENT_INNER_RADIUS: Float = 67f
    const val CRESCENT_START_ANGLE: Float = 190f
    const val CRESCENT_SWEEP: Float = 70f
    const val CRESCENT_ALPHA_ON_DARK: Float = 0.82f
    const val CRESCENT_ALPHA_ON_LIGHT: Float = 0.95f

    /**
     * Слабая дуга отражения по нижнему левому краю.
     *
     * Единственный слой, центр которого не совпадает с центром знака: в SVG дуга задана
     * концами и радиусом, и её настоящий центр — (96,78; 97,91). Смещение в три единицы
     * незаметно глазом и заметно в снимке, поэтому оно сохранено, а не округлено.
     *
     * Слой есть только в тёмной отрисовке: в светлой ему нечем быть — блик на светлом
     * фоне не читается, и в макете светлой плитки его нет.
     */
    val REFLECTION_CENTER: Offset = Offset(96.78f, 97.91f)
    const val REFLECTION_RADIUS: Float = 62f
    const val REFLECTION_START_ANGLE: Float = 147.3f
    const val REFLECTION_SWEEP: Float = -24.3f
    const val REFLECTION_STROKE: Float = 3f
    const val REFLECTION_ALPHA: Float = 0.3f

    /**
     * Тёплая дуга преломлённого луча по нижней правой четверти.
     *
     * Единственный акцентный штрих знака и единственное, что совпадает в тёмной
     * и светлой версиях дословно, — она их и связывает (`task.md`, критерий 4).
     */
    const val WARM_ARC_RADIUS: Float = 86f
    const val WARM_ARC_START_ANGLE: Float = 20f
    const val WARM_ARC_SWEEP: Float = 60f
    const val WARM_ARC_STROKE: Float = 4.5f

    /** Каустика — световое пятно под знаком, эллипс 58×19 радиальным градиентом акцента. */
    const val CAUSTIC_CENTER_Y: Float = 163f
    const val CAUSTIC_RADIUS_X: Float = 58f
    const val CAUSTIC_RADIUS_Y: Float = 19f
    const val CAUSTIC_ALPHA_ON_DARK: Float = 0.62f
    const val CAUSTIC_ALPHA_ON_LIGHT: Float = 0.55f

    // --- Границы лестницы вариантов ---

    /**
     * Нижняя граница стеклянной отрисовки.
     *
     * Ни один источник её не задаёт: спецификация обещает плоскую редукцию с 28
     * пикселей, макет `SklyankaScale.dc.html` рисует её уже на 72, и между 29 и 89
     * правила нет (`task.md`, открытый вопрос «Граница стекло / плоско»). Взят
     * предложенный в постановке вариант — стекло от 90. Число живёт здесь одно,
     * и когда дизайн ответит, правится одна строка.
     */
    val GLASS_FROM: Dp = 90.dp

    /** Ступень «два кольца и точка», тонкая: 45–89 пикселей (в макете — размер 72). */
    val FLAT_RINGS_AND_DOT_FROM: Dp = 45.dp

    /** Ступень «два кольца и точка», толстая: 29–44 пикселя (в макете — размер 44). */
    val FLAT_RINGS_AND_DOT_BOLD_FROM: Dp = 29.dp

    /** Ступень «два кольца без точки»: 19–28 пикселей (в макете — размер 28). */
    val FLAT_TWO_RINGS_FROM: Dp = 19.dp
}

/**
 * Среда, в которой стоит знак.
 *
 * Не тема оформления приложения, а свойство поверхности (§5): тёмными бывают
 * напоминание, заставка и плитка иконки, а не одиннадцать экранов.
 */
enum class MarkEnvironment {
    /** Знак на тёмном полотне: кольца и серп светлые. */
    Dark,

    /** Знак на светлой плитке: кольца и точка тёмные с прозрачностью, серп белый. */
    Light,
}

/**
 * Ступень лестницы знака.
 *
 * Лестница, а не один масштабируемый файл: слои стекла держатся на градиентах,
 * а градиент на 18 пикселях даёт один усреднённый цвет (`TDD.md` §4). Нарисованная
 * ступень предсказуема, отключаемые по размеру слои — нет.
 */
enum class MarkVariant {
    /** Полная стеклянная отрисовка: тело, ободок, три кольца, серп, дуга, каустика, точка. */
    Glass,

    /** Плоско: два кольца и точка, толщины 10 и 8 (макет, размер 72). */
    FlatRingsAndDot,

    /** Плоско: два кольца и точка, толщины 13 и 10 (макет, размер 44). */
    FlatRingsAndDotBold,

    /** Плоско: два кольца без точки — на 28 пикселях точка сливается с внутренним кольцом. */
    FlatTwoRings,

    /** Плоско: одно кольцо и крупная точка — предел различимости (макет, размер 18). */
    FlatRingAndLargeDot,
    ;

    /** Плоская ступень рисуется одной краской без прозрачности и градиентов. */
    val isFlat: Boolean get() = this != Glass
}

/**
 * Состав и толщины одной плоской ступени на поле 200×200.
 *
 * Радиусы ступеней не повторяют радиусы стекла (86/66/47/28) намеренно: редукция —
 * не копия знака с отключёнными слоями, а его упрощение, и её внутреннее кольцо шире
 * (`TDD.md` §3). Значения — из `design/screens/SklyankaScale.dc.html` дословно.
 */
@Immutable
data class FlatMarkShape(
    val outerRadius: Float,
    val outerStroke: Float,
    val innerRadius: Float?,
    val innerStroke: Float?,
    val dotRadius: Float?,
) {
    /** Сколько элементов в ступени: по ним видно, что состав убывает с размером. */
    val elementCount: Int
        get() = 1 + (if (innerRadius != null) 1 else 0) + (if (dotRadius != null) 1 else 0)
}

/** Геометрия плоских ступеней; ключ — ступень лестницы. */
val FLAT_MARK_SHAPES: Map<MarkVariant, FlatMarkShape> = mapOf(
    MarkVariant.FlatRingsAndDot to FlatMarkShape(
        outerRadius = 86f,
        outerStroke = 10f,
        innerRadius = 50f,
        innerStroke = 8f,
        dotRadius = 15f,
    ),
    MarkVariant.FlatRingsAndDotBold to FlatMarkShape(
        outerRadius = 85f,
        outerStroke = 13f,
        innerRadius = 49f,
        innerStroke = 10f,
        dotRadius = 16f,
    ),
    MarkVariant.FlatTwoRings to FlatMarkShape(
        outerRadius = 83f,
        outerStroke = 18f,
        innerRadius = 46f,
        innerStroke = 14f,
        dotRadius = null,
    ),
    MarkVariant.FlatRingAndLargeDot to FlatMarkShape(
        outerRadius = 80f,
        outerStroke = 26f,
        innerRadius = null,
        innerStroke = null,
        dotRadius = 30f,
    ),
)

/**
 * Правило выбора варианта по размеру — единственное место, где оно записано.
 *
 * Экран просит знак нужного размера и получает правильный вариант; подбор «на глаз»
 * по месту — это тот самый способ, которым знак становится разным в разных местах
 * (`task.md`, критерий 2). Границы: 90 и выше — стекло, дальше вниз три ступени
 * редукции, 18 и меньше — предельная.
 */
fun markVariantFor(size: Dp): MarkVariant = when {
    size >= SklyankaMarkTokens.GLASS_FROM -> MarkVariant.Glass
    size >= SklyankaMarkTokens.FLAT_RINGS_AND_DOT_FROM -> MarkVariant.FlatRingsAndDot
    size >= SklyankaMarkTokens.FLAT_RINGS_AND_DOT_BOLD_FROM -> MarkVariant.FlatRingsAndDotBold
    size >= SklyankaMarkTokens.FLAT_TWO_RINGS_FROM -> MarkVariant.FlatTwoRings
    else -> MarkVariant.FlatRingAndLargeDot
}

/**
 * Цвета слоёв стеклянной отрисовки для одной среды.
 *
 * Собираются из палитры дизайн-системы: собственных цветов у знака нет (`TDD.md` §9).
 * [reflection] — единственный необязательный слой: в светлой среде его нет.
 */
@Immutable
data class GlassMarkColors(
    val body: List<Color>,
    val bodyStops: List<Float>,
    val rim: List<Color>,
    val rimStops: List<Float>,
    val rings: List<Color>,
    val crescent: Color,
    val reflection: Color?,
    val warmArc: Color,
    val caustic: Color,
    val dot: Color,
)

/**
 * Цвета стеклянного знака для среды.
 *
 * [accent] приходит снаружи — ролью темы, а не константой: в тёмной среде это
 * `AccentOnDark`, а при появлении палитры профилей (ОВ-10) знак обязан будет с ней
 * согласоваться, и менять придётся вызов, а не эту таблицу.
 */
fun glassMarkColors(environment: MarkEnvironment, accent: Color): GlassMarkColors = when (environment) {
    MarkEnvironment.Dark -> GlassMarkColors(
        body = SklyankaMarkTokens.BODY_ALPHAS_ON_DARK.map { Palette.Background.copy(alpha = it) },
        bodyStops = SklyankaMarkTokens.BODY_STOPS_ON_DARK,
        rim = SklyankaMarkTokens.RIM_ALPHAS_ON_DARK.map { Palette.Background.copy(alpha = it) },
        rimStops = SklyankaMarkTokens.RIM_STOPS,
        rings = SklyankaMarkTokens.RING_ALPHAS_ON_DARK.map { Palette.Background.copy(alpha = it) },
        crescent = Palette.Background.copy(alpha = SklyankaMarkTokens.CRESCENT_ALPHA_ON_DARK),
        reflection = Palette.Background.copy(alpha = SklyankaMarkTokens.REFLECTION_ALPHA),
        warmArc = accent,
        caustic = accent.copy(alpha = SklyankaMarkTokens.CAUSTIC_ALPHA_ON_DARK),
        dot = Palette.Background.copy(alpha = SklyankaMarkTokens.DOT_ALPHA_ON_DARK),
    )

    MarkEnvironment.Light -> GlassMarkColors(
        // Первые две остановки тела в светлой версии — чистый белый блик, третья
        // уже тёмная: стекло на светлом читается тенью, а не свечением.
        body = SklyankaMarkTokens.BODY_ALPHAS_ON_LIGHT.mapIndexed { index, alpha ->
            if (index == SklyankaMarkTokens.BODY_ALPHAS_ON_LIGHT.lastIndex) {
                Palette.Text.copy(alpha = alpha)
            } else {
                Palette.Surface.copy(alpha = alpha)
            }
        },
        bodyStops = SklyankaMarkTokens.BODY_STOPS_ON_LIGHT,
        rim = SklyankaMarkTokens.RIM_ALPHAS_ON_LIGHT.map { Palette.Text.copy(alpha = it) },
        rimStops = SklyankaMarkTokens.RIM_STOPS,
        rings = SklyankaMarkTokens.RING_ALPHAS_ON_LIGHT.map { Palette.Text.copy(alpha = it) },
        crescent = Palette.Surface.copy(alpha = SklyankaMarkTokens.CRESCENT_ALPHA_ON_LIGHT),
        reflection = null,
        warmArc = accent,
        caustic = accent.copy(alpha = SklyankaMarkTokens.CAUSTIC_ALPHA_ON_LIGHT),
        dot = Palette.Text.copy(alpha = SklyankaMarkTokens.DOT_ALPHA_ON_LIGHT),
    )
}

/**
 * Краска плоской ступени.
 *
 * Ступень рисуется одной краской без прозрачности и градиентов: в тёмной среде
 * светлой (§5, «фон и текст меняются местами»), в светлой — цветом текста `#241F1B`,
 * ровно как в макете `SklyankaScale.dc.html`.
 *
 * Иллюстративные применения знака (пустое состояние, плашка «О приложении»,
 * диагностика часов) краску задают сами — там знак подчинён тону экрана, а не среде;
 * для них есть [ru.stohastika.sklyanka.ui.component.FlatMark].
 */
fun flatMarkColor(environment: MarkEnvironment): Color = when (environment) {
    MarkEnvironment.Dark -> Palette.Background
    MarkEnvironment.Light -> Palette.Text
}

/** Подложка плитки иконки: тёмная — основная, светлая — для системы на тёмном фоне. */
fun markTileBackground(environment: MarkEnvironment): Color = when (environment) {
    MarkEnvironment.Dark -> Palette.DarkBackground
    // §7 сводит светлую подложку иконки #F1E9DD к токену линии на поверхности.
    MarkEnvironment.Light -> Palette.LineOnSurface
}
