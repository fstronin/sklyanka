package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileAvatar
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Верхний инсет экрана, сообщённый обёрткой профиля (Р-54).
 *
 * Обёртка [ru.stohastika.sklyanka.ui.profile.ProfileChrome] уже резервирует высоту
 * системной панели полосой цвета шапки — и она же кладёт это значение сюда. Читать инсет
 * по месту экрана нельзя: тогда правило верхнего поля пришлось бы проверять на устройстве,
 * а на стенде инсет подставляется явно, и правило проверяется без него.
 *
 * Значение по умолчанию — `null`: это значит «обёртки нет». Тогда шапка читает настоящий
 * `WindowInsets.statusBars` ([screenTopInset]) и ведёт себя как раньше — но правило потолка
 * («инсет + полоса + поле» ≤ 56 dp) для такого экрана верно, только если полосу под системной
 * панелью зарезервировал он сам (`statusBarsPadding`, `windowInsetsTopHeight`). Экран, который
 * этого не сделал, потолком поле урежет, а полосу не зарезервирует — шапка уедет под системную
 * панель (находка ревью SKL-137: так было у шторки проверки доставки).
 */
val LocalScreenTopInset = staticCompositionLocalOf<Dp?> { null }

/**
 * Верхний инсет, действующий для этой шапки: сообщённый обёрткой либо настоящий.
 *
 * `null` в локале означает «обёртки нет»: только тогда читается платформенный инсет.
 *
 * Прочитанный инсет **не** резервируется: поле шапки отсчитывается от того места, где шапка
 * уже стоит. Обёртка профиля резервирует полосу сама, а экран вне обёртки обязан сделать это
 * своими средствами (`statusBarsPadding`) — иначе поле урежется потолком, а полоса над шапкой
 * останется под системной панелью (находка ревью SKL-137).
 */
@Composable
internal fun screenTopInset(): Dp = LocalScreenTopInset.current
    ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * Поле шапки сверху: базовое, урезанное потолком суммы «инсет + полоса + поле» (§4, Р-54).
 *
 * Сумма «инсет + [stripe] + поле» держится в `HeaderTopCap` 56 dp от верхней грани экрана,
 * а поле не уходит в минус. Потолок достижим, пока сам инсет с полосой его не превышает:
 * выше потолка поле обнуляется, и сумма упирается в них — ниже системной панели её не
 * опустить. На стенде и на телефоне без выреза (инсет 0) правило ничего не меняет;
 * на строке состояния 24 dp сумма остаётся 54 dp; на вырезе 49,5 dp поле урезается до 6,5 dp,
 * а в чужом профиле — до 0,5 dp: 6 dp из того же поля занимает полоса.
 *
 * [stripe] — высота полосы чужого профиля между системной панелью и шапкой. Она входит
 * в то же верхнее поле (§4: «30 dp, из них 6 dp — полоса»), и без неё сумма на вырезе
 * превысила бы потолок на её высоту. У профиля владельца полосы нет — [SklyankaSpacing.None].
 *
 * Чистая функция — одна на все шапки: будь она по месту экрана, на одиннадцати экранах
 * завелось бы одиннадцать чисел, и расхождение между ними было бы не видно (НФТ-47).
 * Базовые значения (30 dp, 24 dp в чужом профиле) она не меняет — только ограничивает.
 */
internal fun headerTopPadding(inset: Dp, base: Dp, stripe: Dp): Dp =
    minOf(base, (SklyankaSizes.HeaderTopCap - inset - stripe).coerceAtLeast(SklyankaSpacing.None))

// Параметров семь: заголовки, вид, возврат, ведущий слот, действие и состояние ряда.
// Свернуть их в контейнер можно, но контейнер здесь — тот же список под другим именем,
// и он спрятал бы, что именно экран объявляет (то же снятие, что у `SklyankaTextField`).
/**
 * Шапка экрана.
 *
 * Три признака чужого профиля появляются здесь **сами**, без участия экрана: подкраска
 * берётся из роли `profileHeader`, которая в теме владельца совпадает с обычным фоном,
 * а надпись «Профиль · Имя» и несворачиваемый баннер рисуются, когда оформление профиля
 * несёт имя (`ProfileChrome`, ПРФ-11, ПРФ-12).
 *
 * Имя профиля приходит из окружения, а не параметром. Параметр можно забыть на одном
 * экране из одиннадцати, и отказ будет незаметен: экран выглядит нормально, просто
 * индикация пропала. Отсюда же следует, что баннер нельзя отключить отдельно от надписи
 * (ПРФ-13): у шапки нет параметра, которым это делается, и не появится — избыточность
 * индикации и есть защита (ПРФ-10).
 *
 * Заголовок не обрезается и переносится на вторую строку при увеличенном системном
 * шрифте: шапка растёт по высоте (§6.3, критерий 5).
 *
 * **Строка вида `Modal` — одна сетка слотов (§7, Р-53).** Ведущее действие стоит слева —
 * у той поверхности, у которой оно есть (у шторки отчёта врачу это «Отмена»); название —
 * сразу за ним слева, замыкающее действие — справа; слова действий встают на боковое поле
 * шапки `SklyankaSpacing.Section` 20 dp, на одной горизонтальной линии с названием. Пустой
 * слот не заполняется ничем: невидимых заглушек и поддельной центровки в модальной шапке
 * нет (V-15). Строку собирает [ModalHeaderRow] — одна часть на обе оставшиеся модальные
 * поверхности: шторка проверки доставки (заголовок без действий: её выход — системный
 * «Назад») и шторка отчёта врачу (её собственные действия отправки). Второй сборки строки
 * по месту быть не должно: именно она и разошлась молча.
 *
 * **Слово «Отмена» у вида `Modal` не обязательно (Р-54).** Его имеет та поверхность,
 * у которой оно есть: у шторки отчёта врачу — как ведущее действие до отправки. Форма
 * лекарства из этого вида вышла: у неё вложенная шапка **без возврата** (Р-54), и слова
 * в шапке нет вовсе — выход даёт системный «Назад», сохранение — нижняя кнопка (Д-3).
 *
 * **Верхнее поле шапки ограничено суммой с инсетом и полосой (§4, Р-54).** Поле считается
 * [headerTopPadding] от инсета, который сообщает обёртка профиля ([LocalScreenTopInset]),
 * и от полосы чужого профиля: сумма «инсет + полоса + поле» держится в потолке
 * `SklyankaSizes.HeaderTopCap` 56 dp, пока сам инсет с полосой его не превышает (выше
 * потолка поле обнуляется — ниже системной панели сумма не опускается). На стенде и на
 * телефоне без выреза (инсет 0) вид не меняется — 30 dp, как в макете.
 *
 * **Закрепление модальной шапки.** Модальная шапка уезжает вместе с содержимым, как
 * корневые и вложенные (§7): крупного заголовка у неё нет, поэтому под закреплённый ряд
 * уходит сама строка, а порог считается по её нижней грани.
 *
 * @param pinning состояние закреплённого ряда (§7), когда шапка стоит **внутри**
 *   прокрутки: тогда шапка сообщает ряду нижнюю грань того, что обязано уйти под ряд
 *   (у `Root`/`Nested` — крупный заголовок, у `Modal` — сама строка), а ряд решает,
 *   показывать ли компактный заголовок. У несжатого экрана этого параметра нет, и шапка
 *   ведёт себя как прежде — рисует себя целиком, включая баннер чужого профиля.
 */
@Suppress("LongParameterList")
@Composable
fun ScreenHeader(
    labels: ScreenHeaderLabels,
    modifier: Modifier = Modifier,
    kind: ScreenHeaderKind = ScreenHeaderKind.Root,
    onBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    pinning: ScreenHeaderState? = null,
) {
    val colors = SklyankaTheme.colors
    val profileName = LocalProfileChrome.current.foreignName
    val overline = labels.overline
    val foreign = profileName != null
    if (pinning != null) {
        // Заголовок ушёл из композиции — он прокручен за верхнюю грань окна (§7): ряд обязан
        // показать компактный заголовок и после свитка, когда последнее измерение устарело.
        DisposableEffect(pinning) { onDispose { pinning.reportTitleScrolledAway() } }
    }
    // Вложенная шапка с действием разводит действие и заголовок по строкам:
    // так нарисованы макеты вложенных экранов (`DataTransfer.dc.html`, `History.dc.html`,
    // `MedCard.dc.html`). В одной строке заголовок «Перенос данных» не помещался рядом
    // с кнопкой и переносился с висячим отступом (SKL-60, дефект пилота).
    val actionRow: (@Composable () -> Unit)? = when {
        leading != null -> leading
        onBack != null -> ({ BackAction(kind, onBack) })
        else -> null
    }
    val nestedWithBack = kind == ScreenHeaderKind.Nested && actionRow != null
    // Верхний отступ берётся один раз на шапку: у закреплённого ряда он свой (2 dp), а шапка
    // под ним отделяется малым зазором — как ряд действия в развёрнутой шапке (§7). Иначе
    // крупный заголовок уезжал бы вниз на два отступа сразу. Поле урезается потолком суммы
    // «инсет + полоса + поле» (§4, Р-54): на вырезе шапка не растёт вместе с ним.
    val basePadding = if (foreign) SklyankaSizes.HeaderTopPaddingProfile else SklyankaSizes.HeaderTopPadding
    // Полоса чужого профиля стоит между системной панелью и шапкой и входит в то же верхнее
    // поле (§4: «30 dp, из них 6 dp — полоса»): без неё на вырезе сумма превысила бы потолок
    // на её высоту (находка ревью SKL-137).
    val stripe = if (foreign) SklyankaSizes.ProfileStripeHeight else SklyankaSpacing.None
    val topPadding = headerTopPadding(screenTopInset(), basePadding, stripe)
    // Шапка в прокрутке своего нижнего поля не несёт: она первый элемент прокрутки, и зазор
    // до содержимого даёт её ритм (§7). Шапка вне прокрутки поле сохраняет — оно и есть
    // воздух над нижней границей `line` у чужого профиля (находка прогона 09.10.2026: поле
    // складывалось с ритмом).
    val bottomPadding = if (pinning != null) SklyankaSpacing.None else SklyankaSizes.HeaderBottomPadding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.profileHeader)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = topPadding,
                bottom = bottomPadding,
            )
            .testTag(TAG_SCREEN_HEADER),
        // Зазор макета между строкой заголовка и баннером. В шапке владельца ребёнок
        // ровно один, и зазор ни на что не влияет.
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        if (nestedWithBack) {
            StackedHeaderRows(actionRow, trailing, labels, profileName, overline, pinning)
        } else {
            SingleHeaderRow(actionRow, trailing, labels, kind, profileName, overline, pinning)
        }
        // Баннер чужого профиля виден всегда: он несворачиваем (ПРФ-13), а в развёрнутом
        // состоянии он часть шапки. На пороге роль берёт ряд, и он ставит баннер туда же,
        // где тот стоял под крупным заголовком, — рисует его ровно один из двоих (§7).
        if (profileName != null && pinning?.collapsed != true) {
            // Баннер стоит внутри подкрашенной шапки — так он нарисован в макете
            // `MainProfile.dc.html`. Действия у него нет: закрыть его нечем и незачем
            // (ПРФ-13). Вторая фраза называет последствие — куда попадёт отметка.
            ProfileBanner(profileName)
        }
        if (foreign) {
            // Нижняя граница появляется только у подкрашенной шапки: на обычном фоне
            // она отделяла бы шапку от того же самого цвета.
            HeaderDivider()
        }
    }
}

/**
 * Закреплённый компактный ряд шапки (§7).
 *
 * Стоит **неподвижно** над прокручиваемым содержимым: возврат и функциональное действие
 * доступны на любом уровне прокрутки, а крупный заголовок уезжает вместе с содержимым.
 * Когда крупный заголовок ушёл под ряд целиком, ряд показывает компактный заголовок
 * и нижнюю линию; порог — геометрия, а не число пикселей, поэтому он верен при любом
 * масштабе шрифта и в любой локали ([ScreenHeaderState]).
 *
 * Фон — роль `profileHeader`: у владельца она равна фону экрана, у чужого профиля несёт
 * подкраску, и она же продолжалась под статус-баром до появления ряда. Баннер чужого
 * профиля стоит ниже ряда и не сворачивается (§7, ПРФ-13).
 *
 * **Ведущий слот — только под настоящий переход (П-7, Р-54).** Шеврон вложенной шапки
 * рисуется, лишь когда возврат есть: у формы лекарства и отчёта о переносе возврата нет,
 * и шеврон без нажатия обещал бы путь, которого нет. Слово действия модальной строки
 * («Отмена» шторки отчёта врачу) остаётся: у него действие есть.
 *
 * @param onOpenProfiles действие аватара-индикатора: в чужом профиле ряд показывает его
 *   сам, если экран назвал, куда ведёт нажатие. Экран без такого перехода аватара не
 *   получает — нажимаемый элемент без перехода обещал бы функцию, которой нет (П-7).
 */
// Тот же набор, что у `ScreenHeader`, плюс переход аватара: ряд — та же шапка, только сжатая.
@Suppress("LongParameterList")
@Composable
fun PinnedHeaderBar(
    labels: ScreenHeaderLabels,
    state: ScreenHeaderState,
    modifier: Modifier = Modifier,
    kind: ScreenHeaderKind = ScreenHeaderKind.Root,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onOpenProfiles: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    val profileName = LocalProfileChrome.current.foreignName
    val collapsed = state.collapsed
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Фон рисуется по порогу: до него ряда на экране нет, и непрозрачная полоса
            // закрыла бы дату, заголовок и аватара, которые стоят в шапке (§7).
            .then(if (collapsed) Modifier.background(colors.profileHeader) else Modifier)
            .testTag(TAG_PINNED_HEADER_BAR),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Высота ряда стоит **до** полей: габарит 56 dp включает поля, и внутри
                // остаётся ровно 44 dp под цели нажатия (§7). Обратный порядок прибавлял бы
                // поля к габариту и ряд вырос бы до 68 dp.
                .heightIn(min = SklyankaSizes.PinnedHeaderBar)
                .padding(
                    start = SklyankaSpacing.Section,
                    end = SklyankaSpacing.Section,
                    top = SklyankaSpacing.Hair,
                    bottom = SklyankaSpacing.Default,
                )
                // Порог считается по нижней грани **полосы ряда**, а не всей закреплённой
                // части: в чужом профиле ниже полосы стоит несворачиваемый баннер, и порог
                // по всей части гас бы на высоту баннера позже (§7).
                .onGloballyPositioned { state.reportBarBottom(it.boundsInRoot().bottom.toInt()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            // Содержимое — только по порогу: до него ряд служит одной мерой геометрии,
            // и его пустая полоса не ловит нажатия (детей нет вовсе).
            if (collapsed) {
                // Шеврон — только там, где возврат есть (§7, П-7): у вложенной шапки без
                // возврата (форма лекарства, Р-54) он выглядел бы живым и не нажимался —
                // нажимаемый на вид элемент без перехода. В корне вкладки возвращаться
                // некуда по той же причине.
                if (kind == ScreenHeaderKind.Nested && onBack != null) {
                    BackChevronAction(label = stringResource(R.string.cd_back), onBack = onBack)
                } else if (kind == ScreenHeaderKind.Modal) {
                    // Модальная шапка шеврона не получает ни в своей строке, ни в ряду (§7, Р-53):
                    // ведущее действие — то же слово «Отмена», на том же боковом поле.
                    onBack?.let { action ->
                        Box(modifier = Modifier.modalActionSlot(start = true)) {
                            BackAction(ScreenHeaderKind.Modal, action)
                        }
                    }
                }
                CompactHeaderTitle(title = labels.title, collapsed = true, modifier = Modifier.weight(1f))
                trailing?.invoke()
                // Аватар — шестой признак чужого профиля, и в сжатом ряду он не снимается
                // ради чистоты экрана (П-2, §5.8, ПРФ-11): в чужом профиле ряд показывает его
                // сам, если экран умеет открыть список профилей. У владельца подкраски нет,
                // и аватар остаётся действием «Сегодня» (§7, ПРФ-16).
                if (trailing == null && profileName != null && onOpenProfiles != null) {
                    ProfileAvatar(onOpenProfiles = onOpenProfiles)
                }
            }
        }
        if (collapsed) {
            // Закреплённая часть в чужом профиле непрозрачна насквозь — полоса, зазор
            // и баннер: иначе уезжающий баннер выглядывал бы из-под закреплённого (§7).
            if (profileName != null) {
                ProfileBanner(profileName, modifier = Modifier.padding(horizontal = SklyankaSpacing.Section))
            }
            // Нижняя линия есть только в сжатом состоянии: в развёрнутом шапку от содержимого
            // отделяет сам крупный заголовок (§7).
            HeaderDivider()
        }
    }
}

/**
 * Состояние закреплённого компактного ряда (§7).
 *
 * Порог появления компактного заголовка — **геометрия**, а не число пикселей: заголовок
 * появляется, когда нижняя грань того, что обязано уйти под ряд, пересекла нижнюю грань ряда,
 * и тем же порогом гаснет при прокрутке вверх. Под ряд уходит крупный заголовок
 * (`Root`/`Nested`) либо сама строка модальной шапки (`Modal`, Р-53) — состояние различает
 * их одной мерой, а не двумя. Порог по пикселям врал бы при смене масштаба шрифта,
 * длины локали и высоты шапки в чужом профиле.
 *
 * Обе границы приходят от раскладки (`onGloballyPositioned`, координаты одного корня),
 * и состояние не знает, прокручивается ли содержимое: не прокручивается — измеряемый
 * элемент всегда ниже ряда, и шапка остаётся развёрнутой.
 */
@Stable
class ScreenHeaderState internal constructor() {

    /** То, что обязано уйти под ряд, ушло под него. */
    var collapsed: Boolean by mutableStateOf(false)
        private set

    private var barBottom: Int? = null
    private var collapsibleBottom: Int? = null

    internal fun reportBarBottom(bottomInRoot: Int) {
        barBottom = bottomInRoot
        settle()
    }

    /**
     * Нижняя грань того, что обязано уйти под ряд: у `Root`/`Nested` — крупный заголовок,
     * у `Modal` — сама строка шапки (крупного заголовка у неё нет, Р-53).
     */
    internal fun reportCollapsibleBottom(bottomInRoot: Int) {
        collapsibleBottom = bottomInRoot
        settle()
    }

    /**
     * Порог: нижняя грань того, что обязано уйти под ряд, пересекла нижнюю грань ряда (§7).
     *
     * Обе границы хранятся как «измерено / не измерено», а не как число с нулём-признаком:
     * прокрученный за верхнюю грань заголовок сообщает ровно ноль (границы обрезаны окном),
     * и ноль обязан означать «ушёл под ряд», а не «ещё не мерили».
     */
    private fun settle() {
        val bar = barBottom ?: return
        val content = collapsibleBottom ?: return
        collapsed = content <= bar
    }

    /**
     * То, что обязано уйти под ряд, ушло из композиции: оно прокручено за верхнюю грань окна.
     *
     * Быстрая прокрутка (свиток) выбрасывает элемент шапки из композиции в том же кадре,
     * в котором он уходит из виду, и последнее измерение остаётся от кадра, где он ещё виден.
     * Состояние «мерить больше нечего» приходит из [androidx.compose.runtime.DisposableEffect]
     * шапки: элемент ушёл — ряд обязан показать компактный заголовок (§7).
     */
    internal fun reportTitleScrolledAway() {
        collapsed = true
    }
}

/** Состояние ряда для экрана с крупным заголовком (§7). */
@Composable
fun rememberScreenHeaderState(): ScreenHeaderState = remember { ScreenHeaderState() }

/**
 * Компактный заголовок ряда.
 *
 * Рисуется только в сжатом состоянии и **только если помещается целиком**: у него нет
 * многоточия, и обрезанный заголовок не сообщал бы, где человек находится. Порог — по
 * фактической измеренной ширине подписи, а не по масштабу шрифта: иначе немецкое
 * «Benachrichtigungen prüfen» обрезалось бы уже на 130 % (§6.3, §7).
 */
@Composable
private fun CompactHeaderTitle(title: String, collapsed: Boolean, modifier: Modifier = Modifier) {
    if (!collapsed) return
    val colors = SklyankaTheme.colors
    val style = SklyankaType.BodyLarge
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        val fits = remember(title, available, measurer, style) {
            available > 0 &&
                measurer.measure(
                    text = AnnotatedString(title),
                    style = style,
                    maxLines = COMPACT_TITLE_LINES,
                    softWrap = false,
                    constraints = Constraints(maxWidth = available),
                ).hasVisualOverflow.not()
        }
        if (fits) {
            Text(
                text = title,
                style = style,
                color = colors.text,
                maxLines = COMPACT_TITLE_LINES,
                softWrap = false,
                modifier = Modifier.testTag(TAG_COMPACT_TITLE),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun StackedHeaderRows(
    actionRow: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    labels: ScreenHeaderLabels,
    profileName: String?,
    overline: String?,
    pinning: ScreenHeaderState?,
) {
    // Верхний отступ берётся один раз на всю шапку: у блока заголовка повторного
    // `HeaderTopPadding` нет (ОВ-91).
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            actionRow?.invoke()
            Box(modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            HeaderTitleColumn(labels, profileName, overline, pinning)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SingleHeaderRow(
    actionRow: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    labels: ScreenHeaderLabels,
    kind: ScreenHeaderKind,
    profileName: String?,
    overline: String?,
    pinning: ScreenHeaderState?,
) {
    // Модальная строка — своя сетка слотов (Р-53), и собирается она общей частью: у неё нет
    // крупного заголовка, вопрос «помещается ли заголовок рядом с действием» (SKL-60) не стоит,
    // а под закреплённый ряд уходит сама строка.
    if (kind == ScreenHeaderKind.Modal) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
        ) {
            // Надпись профиля и надстрочник стоят **над** строкой, а не в ней: строка одна
            // и делится на слоты действия и названия, а имя профиля — не слот строки, а
            // признак чужого профиля (ПРФ-11, ПРФ-12). Убранная отсюда надпись пропала бы
            // молча: экран выглядел бы нормально, а индикация — нет.
            when {
                profileName != null -> ProfileOverline(profileName)

                overline != null -> Text(
                    text = overline.uppercased(),
                    style = SklyankaType.OverlineDate,
                    color = SklyankaTheme.colors.textMuted,
                )
            }
            ModalHeaderRow(
                title = labels.title,
                leading = actionRow,
                trailing = trailing,
                modifier = if (pinning == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned {
                        pinning.reportCollapsibleBottom(it.boundsInRoot().bottom.toInt())
                    }
                },
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        if (kind != ScreenHeaderKind.Root) actionRow?.invoke()
        HeaderTitleColumn(labels, profileName, overline, pinning)
        trailing?.invoke()
    }
}

/**
 * Строка модальной шапки: одна сетка слотов на все модальные поверхности (§7, Р-53).
 *
 * Ведущее действие слева, название сразу за ним слева, замыкающее действие справа — все
 * на одной горизонтальной линии. Слова действий встают на боковое поле шапки
 * `SklyankaSpacing.Section` 20 dp: слот гасит собственное поле тихой кнопки
 * ([SklyankaSizes.QuietButtonPadding] 8 dp) смещением — тем же приёмом, каким возврат-шеврон
 * гасит своё ([BackChevronAction]). Сдвигается место, а не размер: цель нажатия остаётся
 * не меньше 44 dp (НФТ-29).
 *
 * Пустой слот не заполняется ничем: невидимой заглушки ради центровки названия здесь нет
 * (Р-53, V-15). Название прижато влево и занимает всю оставшуюся ширину.
 *
 * Публичная, потому что строку берут обе оставшиеся модальные поверхности: шторка проверки
 * доставки приходит через [ScreenHeader], а шторка отчёта врачу собирает шапку по месту —
 * и обязана брать ту же строку, а не вторую её копию.
 */
@Composable
fun ModalHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // Вертикальная центровка, а не выравнивание по верхнему краю: кнопка центрует подпись
        // внутри своей цели 44 dp, и при выравнивании по верху слово уходило ниже названия
        // на 11 dp — та самая кривизна, которую нашёл заказчик (Р-53).
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        leading?.let {
            Box(modifier = Modifier.modalActionSlot(start = true)) { it() }
        }
        Text(
            text = title,
            style = SklyankaType.BodyDefaultStrong,
            color = SklyankaTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Box(modifier = Modifier.modalActionSlot(start = false)) { it() }
        }
    }
}

/**
 * Смещение слота действия модальной строки: слово встаёт на боковое поле шапки (Р-53).
 *
 * Ведущий слот тянется влево, замыкающий — вправо, оба на собственное поле тихой кнопки.
 * Размер и цель нажатия не меняются: смещается только место слова.
 */
private fun Modifier.modalActionSlot(start: Boolean): Modifier = offset(
    x = if (start) -SklyankaSizes.QuietButtonPadding else SklyankaSizes.QuietButtonPadding,
)

/**
 * Колонка заголовка шапки: надпись профиля либо надстрочник и сам заголовок.
 *
 * Вынесена из [ScreenHeader], чтобы функция шапки оставалась обозримой (detekt
 * `LongMethod`): порядок строк и перенос заголовка при крупном шрифте не меняются.
 * Нижняя грань заголовка сообщается закреплённому ряду: по ней ряд решает, показывать ли
 * компактный заголовок (§7). Вид `Modal` сюда не приходит: у него своя строка ([ModalHeaderRow]).
 */
@Composable
private fun RowScope.HeaderTitleColumn(
    labels: ScreenHeaderLabels,
    profileName: String?,
    overline: String?,
    pinning: ScreenHeaderState?,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
    ) {
        when {
            profileName != null -> ProfileOverline(profileName)

            overline != null -> Text(
                text = overline.uppercased(),
                style = SklyankaType.OverlineDate,
                color = colors.textMuted,
            )
        }
        Text(
            text = labels.title,
            style = SklyankaType.TitleScreen,
            color = colors.text,
            modifier = if (pinning == null) {
                Modifier
            } else {
                Modifier.onGloballyPositioned { pinning.reportCollapsibleBottom(it.boundsInRoot().bottom.toInt()) }
            },
        )
    }
}

/**
 * Надпись «Профиль · Имя» (ПРФ-11, критерий 4).
 *
 * Публичная и единственная: экранов в контексте профиля одиннадцать, и текст, стиль
 * и тег надписи обязаны быть в одном месте. Собранная по месту копия расходится
 * с оригиналом молча — а расхождение здесь означает, что человек не узнал, чей экран
 * перед ним.
 */
@Composable
fun ProfileOverline(profileName: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.profile_label, profileName).uppercased(),
        style = SklyankaType.Overline,
        color = SklyankaTheme.colors.accent,
        modifier = modifier.testTag(TAG_PROFILE_LABEL),
    )
}

/**
 * Индикация чужого профиля для экрана, чья шапка собрана по месту, а не [ScreenHeader].
 *
 * Появилась по итогам SKL-26: исполнимая приёмка SKL-15 (`Структура сценария` «Признаки
 * чужого профиля есть на всех экранах профиля») показала, что карточка лекарства
 * надписи «Профиль · Имя» и баннера не показывала вовсе. Её шапка собрана по месту
 * намеренно (заголовок карточки — название лекарства, и рисовать его дважды нельзя),
 * и вместе с шапкой из экрана выпала индикация — то есть ровно та дыра в защите,
 * о которой говорит `ПРФ-12`.
 *
 * В профиле владельца не рисует ничего: отсутствие индикации само по себе сигнал
 * (`ПРФ-16`).
 */
@Composable
fun ProfileIndication(modifier: Modifier = Modifier) {
    val profileName = LocalProfileChrome.current.foreignName ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.profileHeader)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSizes.HeaderTopPaddingProfile,
                bottom = SklyankaSizes.HeaderBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        ProfileOverline(profileName)
        ProfileBanner(profileName)
        HeaderDivider()
    }
}

/**
 * Несворачиваемый баннер чужого профиля (ПРФ-13, критерий 5).
 *
 * Текст — дословно из `design/screens/MainProfile.dc.html`, а не короткая форма
 * текстовой спецификации («Это не ваши приёмы»): вторая фраза называет **последствие**,
 * и человек узнаёт не только, что он не там, но и что случится, если он нажмёт кнопку.
 *
 * Действия у баннера нет ни в каком виде. Кнопка «скрыть» превратила бы шесть признаков
 * в пять, а после первой недели — в ноль: закрытый однажды баннер человек закрывает
 * каждый раз не глядя.
 */
@Composable
private fun ProfileBanner(profileName: String, modifier: Modifier = Modifier) {
    Banner(
        text = stringResource(R.string.profile_banner, profileName),
        kind = BannerKind.Profile,
        modifier = modifier,
    )
}

@Composable
private fun BackAction(kind: ScreenHeaderKind, onBack: () -> Unit) {
    val modal = kind == ScreenHeaderKind.Modal
    if (modal) {
        QuietButton(
            text = stringResource(R.string.action_cancel),
            onClick = onBack,
            kind = QuietButtonKind.Muted,
            // Действие модальной шапки — форма шапки, а не строки: 15 sp против 14 (§3.2, §7, ОВ-146).
            form = QuietButtonForm.Header,
        )
    } else {
        BackChevronAction(label = stringResource(R.string.cd_back), onBack = onBack)
    }
}

/**
 * Возврат — шеврон (§7, Р-30, V-7).
 *
 * Аффорданс возврата — глиф направления «влево» 22 dp в цели 44×44 dp, роль `text`:
 * возврат — действие, а не украшение, и `iconFaint` для информативного значка запрещён
 * (§6.1). Слова «Назад» на экране нет: смысл несёт доступное имя `cd_back`, а сам глиф
 * декоративен и чтению не объявляется. Поле цели сдвинуто на 10 dp влево от бокового
 * поля шапки, чтобы глиф встал на одну вертикаль с началом заголовка.
 *
 * `onBack` не нулевой: шеврон рисуется только там, где возврат есть (П-7). Нулевой
 * колбэк, выключавший нажатие (`clickable(enabled = false)`), оставлял живой на вид глиф
 * без перехода — того состояния у компонента больше нет (Р-54).
 */
@Composable
private fun BackChevronAction(label: String, onBack: () -> Unit) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .offset(x = -SklyankaSpacing.Default)
            .size(SklyankaSizes.MinTouchTarget)
            .clip(SklyankaShapes.Pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ChevronGlyph(
            color = colors.text,
            size = SklyankaSizes.BackChevron,
            direction = ChevronDirection.Left,
        )
    }
}

@Composable
private fun HeaderDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.line),
    )
}

/**
 * Верхний регистр меток задаётся здесь, а не `uppercase()` по месту на экране.
 *
 * `TextStyle` в Compose не умеет `text-transform`, поэтому правило §2.2 («ВЕРХНИЙ
 * РЕГИСТР») исполняется преобразованием — но ровно в одном месте дизайн-системы и
 * с учётом локали: `uppercase()` без локали ломается на второй локали (НФТ-43).
 */
@Composable
private fun String.uppercased(): String = uppercase(interfaceLocale())

const val TAG_SCREEN_HEADER = "screen_header"
const val TAG_PROFILE_LABEL = "profile_label"
const val TAG_PINNED_HEADER_BAR = "pinned_header_bar"
const val TAG_COMPACT_TITLE = "pinned_header_title"

/** Компактный заголовок — одна строка: многоточия у него нет, он либо влезает, либо не рисуется. */
private const val COMPACT_TITLE_LINES = 1
