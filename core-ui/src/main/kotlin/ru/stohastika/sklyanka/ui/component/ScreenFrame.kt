package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Правило порога (§4.9, ОВ-133): шапка сжата, когда нижняя грань строки развёрнутого
 * заголовка поднялась до нижней грани закреплённого ряда в его сжатой геометрии.
 *
 * Граница принадлежит сжатому состоянию («равно»): иначе возникает кадр, где крупный
 * заголовок уже закрыт непрозрачным рядом, а компактный ещё не показан, — окно без
 * названия экрана (§4.1).
 *
 * [threshold] — константа каркаса ([headerThresholdPx]), а не измерение ряда: высота ряда
 * зависит от признака (§8.1), и признак, посчитанный от живой грани, замкнул бы обратную
 * связь «высота ряда → порог → высота ряда». Функция чистая и вынесена из композиции по
 * образцу [tabTint] и `motionTiming`: правило порога проверяется на JVM, а не «на глаз»
 * по снимку.
 */
internal fun headerCollapsed(titleRowBottom: Float, threshold: Float): Boolean = titleRowBottom <= threshold

/**
 * Порог сжатия в пикселях координат окна: верх области экрана, инсет системной панели и
 * высота компактного ряда, взятая токеном (§4.1 — формула утверждённого прототипа).
 *
 * Высота ряда в покое в формулу не входит: у корневого экрана она нулевая, у вложенного —
 * [SklyankaSizes.HeaderCompactRowHeight], а порог на оба случая один. Координаты окна, а
 * не корня, нужны затем, чтобы грань строки заголовка и порог лежали в одном пространстве:
 * тогда отступы панелей и положение экрана внутри `AnimatedLayer` сокращаются (§5).
 */
internal fun headerThresholdPx(frameTopInWindowPx: Float, statusInsetPx: Int, compactRowHeightPx: Int): Float =
    frameTopInWindowPx + statusInsetPx + compactRowHeightPx

/**
 * Каркас экрана с правилом прокрутки шапки (§4.9, ОВ-133).
 *
 * Владеет закреплённым компактным рядом и отдаёт экрану развёрнутую шапку как первый блок
 * прокручиваемого содержимого. Крупный заголовок и служебные блоки — содержимое; возврат и
 * функциональное действие — каркас.
 *
 * Экран сохраняет свой контейнер прокрутки (`LazyColumn` или `Column.verticalScroll`):
 * каркас читает положение шапки из раскладки и не требует от экрана ни состояния прокрутки,
 * ни потока смещения (§5).
 *
 * Параметры `labels`, `kind`, `onBack`, `leading`, `trailing` — дословно параметры
 * [ScreenHeader]: миграция экрана сводится к замене вызова и переносу шапки внутрь
 * прокрутки, а не к переписыванию аргументов. `kind = Modal` каркас отклоняет: модальная
 * шапка — другой вид, и правило прокрутки на неё не распространяется (§6.1).
 *
 * [expandedTitle] — слот крупного заголовка для экранов, у которых он нестандартный
 * (карточка лекарства, «Настроить надёжность»). Надпись «Профиль · Имя», баннер и
 * разделитель при этом остаются за каркасом: индикацию профиля нельзя отдавать на откуп
 * экрану (ПРФ-12).
 */
@Suppress("LongParameterList")
@Composable
fun ScreenFrame(
    labels: ScreenHeaderLabels,
    modifier: Modifier = Modifier,
    kind: ScreenHeaderKind = ScreenHeaderKind.Root,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    expandedTitle: (@Composable () -> Unit)? = null,
    content: @Composable ScreenFrameScope.() -> Unit,
) {
    require(kind != ScreenHeaderKind.Modal) { "ScreenFrame never draws the modal header (§6.1)" }
    val density = LocalDensity.current
    val foreignProfile = LocalProfileChrome.current.foreignName != null
    val rowPermanent = kind == ScreenHeaderKind.Nested
    val compactRowHeightPx = with(density) { SklyankaSizes.HeaderCompactRowHeight.roundToPx() }
    val collapsed = remember { mutableStateOf(false) }
    // Инсет считается разрешённым: хозяйский отступ, уже сокращённый `ProfileChrome`
    // (`TDD.md §7.2`), — ровно тот, что резервирует ряд. Иначе порог и нижняя грань ряда
    // разъехались бы на высоту статус-бара (§10: инсет берётся один раз на каркас).
    val consumedStatusTopPx = remember { mutableIntStateOf(0) }
    val statusInsetPx = (WindowInsets.statusBars.getTop(density) - consumedStatusTopPx.intValue).coerceAtLeast(0)
    // Порог — константа каркаса (§4.1): верх его области, инсет статус-бара и 56 dp токеном.
    val thresholdPx = remember { mutableFloatStateOf(0f) }
    // Единственное измерение на кадр прокрутки (§4.2, §5): сравнение и запись признака.
    // Равное значение снимок Compose не публикует, поэтому рекомпозиция идёт на пересечении.
    val onTitleRowPlaced: (Float) -> Unit = remember {
        { titleRowBottom -> collapsed.value = headerCollapsed(titleRowBottom, thresholdPx.floatValue) }
    }
    // Управление компонуются ровно в одном месте — здесь и только здесь (§4.3, §8.1).
    val controlsInRow = rowPermanent || collapsed.value
    // Выше порога системного шрифта скрывается дубль названия, а не смысл: ряду важнее
    // действие, чем заголовок, который человек только что прочёл крупным (§9.4).
    val compactTitle = collapsed.value && density.fontScale <= SklyankaSizes.COMPACT_TITLE_HIDE_FONT_SCALE
    val back = headerBack(kind, onBack, leading)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onConsumedWindowInsetsChanged { consumed -> consumedStatusTopPx.intValue = consumed.getTop(density) }
            // Верх области экрана: узел неподвижен, значение меняется со сменой инсетов,
            // конфигурации или ротации (§4.1). Порог считается здесь и больше не меняется
            // от прокрутки: ряд себя не измеряет.
            .onGloballyPositioned { coordinates ->
                val frameTopInWindowPx = coordinates.boundsInWindow().top
                thresholdPx.floatValue = headerThresholdPx(frameTopInWindowPx, statusInsetPx, compactRowHeightPx)
            },
    ) {
        content(
            ScreenFrameScopeImpl(
                labels = labels,
                kind = kind,
                rowPermanent = rowPermanent,
                foreignProfile = foreignProfile,
                controlsInRow = controlsInRow,
                titleMuted = collapsed.value,
                back = back,
                action = trailing,
                expandedTitle = expandedTitle,
                onTitleRowPlaced = onTitleRowPlaced,
            ),
        )
        // Ряд стоит вне скроллера поверх содержимого: его узлы доступны при любом уровне
        // прокрутки (§13.4), а непрозрачный фон накрывает уезжающий заголовок (§9.1).
        PinnedHeaderRow(
            title = labels.title,
            compactTitle = compactTitle,
            tall = controlsInRow,
            leading = if (controlsInRow) back else null,
            action = if (controlsInRow) trailing else null,
        )
    }
}

/**
 * Область видимости содержимого каркаса.
 *
 * Экран обязан поставить [ExpandedHeader] первым блоком прокручиваемого содержимого: вне
 * прокрутки строка заголовка не уезжает за порог, и правило просто не действует (§13.2).
 */
interface ScreenFrameScope {

    /**
     * Развёрнутая шапка: крупный заголовок, надстрочник или имя профиля, баннер и
     * разделитель чужого профиля. Ставится первым блоком прокручиваемого содержимого.
     *
     * Модификатор обязателен, без значения по умолчанию: плагин Compose падает на
     * синтетическом мосте параметра по умолчанию interface-метода, когда метод вызывают
     * из другого модуля компиляции (`NotImplementedError: Unknown file`, проверено на
     * стенде `core-ui`). Экрану модификатор почти не нужен — для него есть перегрузка
     * [ExpandedHeader] без аргументов.
     */
    @Composable
    fun ExpandedHeader(modifier: Modifier)
}

/** Развёрнутая шапка без собственного модификатора — обычный случай первого блока прокрутки. */
@Composable
fun ScreenFrameScope.ExpandedHeader() = ExpandedHeader(Modifier)

/**
 * Развёрнутая шапка каркаса собирается тем же [ScreenHeaderBlock], что и статичная
 * [ScreenHeader]: двух сборок шапки задача не допускает (§7.1).
 *
 * Отдельным объектом, а не наборами параметров: экран вызывает [ExpandedHeader] из своей
 * прокрутки, и все значения шапки должны приходить туда одним списком, который собрал каркас.
 */
@Suppress("LongParameterList")
private class ScreenFrameScopeImpl(
    private val labels: ScreenHeaderLabels,
    private val kind: ScreenHeaderKind,
    private val rowPermanent: Boolean,
    private val foreignProfile: Boolean,
    private val controlsInRow: Boolean,
    private val titleMuted: Boolean,
    private val back: (@Composable () -> Unit)?,
    private val action: (@Composable () -> Unit)?,
    private val expandedTitle: (@Composable () -> Unit)?,
    private val onTitleRowPlaced: (Float) -> Unit,
) : ScreenFrameScope {

    @Composable
    override fun ExpandedHeader(modifier: Modifier) {
        ScreenHeaderBlock(
            labels = labels,
            kind = kind,
            // §8.2: полоса постоянного ряда уже взяла своё поле, развёрнутой шапке остаётся
            // Hair — крупный заголовок встаёт на 56 + 2 dp от верха области экрана.
            topPadding = when {
                rowPermanent -> SklyankaSizes.HeaderCompactRowHeight + SklyankaSpacing.Hair
                foreignProfile -> SklyankaSizes.HeaderTopPaddingProfile
                else -> SklyankaSizes.HeaderTopPadding
            },
            // §8.3: шапка стала первым блоком контейнера, её собственное нижнее поле
            // снимается — иначе зазор сложился бы дважды.
            bottomPadding = SklyankaSpacing.None,
            leading = if (controlsInRow) null else back,
            trailing = if (controlsInRow) null else action,
            title = expandedTitle,
            onTitleRowPlaced = onTitleRowPlaced,
            titleMuted = titleMuted,
            modifier = modifier,
        )
    }
}

/**
 * Закреплённый компактный ряд: фон, возврат, компактный заголовок и действие экрана.
 *
 * Внутренний и в одном месте намеренно: второе место, где собирается ряд, означало бы
 * второе правило (§6.2). Ряд себя не измеряет — `onGloballyPositioned` стоит только на
 * строке развёрнутого заголовка, а порог считает каркас ([headerThresholdPx]).
 *
 * [tall] — `rowPermanent || collapsed`: у корневого экрана без возврата ряд в покое нулевой
 * и вырастает ровно тогда, когда заголовок дошёл до его будущей нижней грани (§8.1).
 * [compactTitle] — признак сжатия плюс системный шрифт не выше порога (§9.4).
 */
@Composable
internal fun PinnedHeaderRow(
    title: String,
    compactTitle: Boolean,
    tall: Boolean,
    leading: (@Composable () -> Unit)?,
    action: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Роль `profileHeader`, а не `background`: у владельца они равны, поэтому фон
            // ряда есть фон экрана, а в чужом профиле ряд наследует подкраску шапки —
            // одна роль закрывает критерии 7 и 11 без ветвления (§9.1).
            //
            // Фон рисуется до инсета и всегда: в покое под рядом ничего нет, а уезжающий
            // заголовок не должен просвечивать под текстом ни на одном кадре. Анимировать
            // фон означало бы 150 ms полупрозрачного ряда над карточками (§9.1).
            .background(colors.profileHeader)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag(TAG_HEADER_ROW),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn, а не height: при крупном системном шрифте ряд растёт по
                // кнопкам, и цель нажатия 44 dp сохраняется (§9.4, НФТ-29).
                .heightIn(min = if (tall) SklyankaSizes.HeaderCompactRowHeight else SklyankaSpacing.None)
                .padding(horizontal = SklyankaSpacing.Section),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            leading?.invoke()
            // Доля заголовка постоянна и не зависит от его видимости: действие не
            // смещается, когда заголовок проявляется или гаснет (§6.1).
            Box(modifier = Modifier.weight(1f)) {
                CompactTitle(title = title, visible = compactTitle, color = colors.text)
            }
            action?.invoke()
        }
        // Линия появляется тем же кадром, что и компактный заголовок: в покое и на коротком
        // содержимом её нет (критерий 6, §9.1).
        AnimatedVisibility(
            visible = compactTitle,
            enter = compactTitleEnter(),
            exit = compactTitleExit(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SklyankaSizes.DividerThickness)
                    .background(colors.line)
                    .testTag(TAG_HEADER_ROW_LINE),
            )
        }
    }
}

/**
 * Компактный заголовок ряда: `BodyLarge` — интерфейсная метка, а не уменьшенная Literata
 * (критерий 5, §9.2).
 *
 * Одна строка с многоточием — по рисунку макета. Второй строки ресурса не появляется: ряд
 * печатает то же значение `labels.title`, что и крупный заголовок (§9.2, критерий 16).
 *
 * Отдельной функцией, а не телом ряда: внутри `Box` в `Row` неявный получатель `RowScope`
 * не даёт вызвать немодифицированный `AnimatedVisibility`, а доля заголовка обязана жить в
 * постоянном `Box(weight = 1f)` — иначе действие смещалось бы, когда заголовка не видно.
 */
@Composable
private fun CompactTitle(title: String, visible: Boolean, color: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = compactTitleEnter(),
        exit = compactTitleExit(),
    ) {
        Text(
            text = title,
            style = SklyankaType.BodyLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TAG_HEADER_COMPACT_TITLE),
        )
    }
}

/**
 * Проявление и гашение — только fade по §9.3: `DurationFast` с `EasingEnter`/`EasingExit`,
 * без сдвига. `translateY(4px)` живого прототипа — оформление демо, а не правило системы
 * (решение дизайнера, ОВ-151).
 *
 * Собственный `MotionDurationScale` не подставляется: `tween` умножается на системный
 * масштаб анимаций самим Compose, поэтому при масштабе 0 переход мгновенен, а конечные
 * состояния остаются целевыми (§9.3). Новых фаз в `MotionPhase` не заведено: отклик на
 * жест внутри одного компонента — не смена слоя.
 */
private fun compactTitleEnter(): EnterTransition =
    fadeIn(tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingEnter))

private fun compactTitleExit(): ExitTransition =
    fadeOut(tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingExit))

/** Закреплённый компактный ряд (§6.2): виден всегда, состав зависит от состояния шапки. */
const val TAG_HEADER_ROW = "header_row"

/** Компактный заголовок ряда: только в сжатом состоянии (§6.4). */
const val TAG_HEADER_COMPACT_TITLE = "header_compact_title"

/** Нижняя линия ряда: только в сжатом состоянии (§6.4). */
const val TAG_HEADER_ROW_LINE = "header_row_line"
