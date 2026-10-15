package ru.stohastika.sklyanka.feature.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.domain.pro.ProBenefitRow
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.domain.pro.ProPeriodOffer
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.ui.component.CalendarGlyph
import ru.stohastika.sklyanka.ui.component.ModalHeaderRow
import ru.stohastika.sklyanka.ui.component.ProfileGlyph
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.text.proDate
import ru.stohastika.sklyanka.ui.text.proText
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Витрина «Склянки Про» — одна модальная поверхность покупки (Р-67, `docs/norm/06-design.md` §5.9).
 *
 * Шторка одна на все состояния: «не оформлена», «активна» и «истекла» различаются подпиской,
 * а не поверхностью; отдельного полноэкранного «Про» нет, и второй поверхности покупки
 * не заводится. Все решения уже приняты в [ProSheetState] — экран только показывает состояние,
 * поэтому поверхность проверяема кадром и матрицей, а правила — прогоном без Android.
 *
 * Состав сверху вниз: модальная шапка → выгоды → выбор периода (или его отсутствие) →
 * тихие строки → неподвижный нижний блок с раскрытием магазина и главным действием.
 * Прокручивается середина: слова, которые магазин требует показать до покупки, не должны
 * уезжать от кнопки (Р-76 п. 3).
 */
@Composable
fun ProSheet(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit, modifier: Modifier = Modifier) {
    // Последнее непустое состояние держится на время ухода шторки: иначе содержимое исчезало бы
    // раньше затемнения (тот же приём, что у шторки отчёта врачу).
    var shown by remember { mutableStateOf(state) }
    if (state.visible) shown = state
    SklyankaBottomSheet(
        visible = state.visible,
        onDismiss = { onIntent(ProShowcaseIntent.Dismiss) },
        modifier = modifier,
    ) {
        ProSheetContent(shown, onIntent)
    }
}

/**
 * Витрина, подключённая к экрану: состояние сессии и её события.
 *
 * [onOpenPolicy] открывает документ «Условия и политика»: адрес и браузер живут в `app`,
 * а не в экране (`ADR-0007`). Без него строка вела бы в магазин — то есть обещала бы
 * документ, а открывала бы покупку (§5.9, П-7).
 */
@Composable
fun ProShowcaseRoute(viewModel: ProShowcaseViewModel, onOpenPolicy: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    ProSheet(
        state = state,
        onIntent = { intent ->
            if (intent is ProShowcaseIntent.OpenPolicy) onOpenPolicy() else viewModel.onIntent(intent)
        },
        modifier = modifier,
    )
}

@Composable
private fun ProSheetContent(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_PRO_SHEET)) {
        ModalHeaderRow(
            title = proText(ProNotice.SHEET_TITLE),
            modifier = Modifier.padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSpacing.Medium,
                bottom = SklyankaSpacing.Medium,
            ),
        )
        Column(
            // `fill = false`: середина занимает столько, сколько нужно содержимому, и лишь
            // при длинном списке забирает остаток высоты полотна и прокручивается. Без этого
            // полотно раздувалось бы до потолка 90 % всегда, и между составом и словами
            // магазина оставалась бы пустая середина — а норма §5 её прямо запрещает
            // («полотно по высоте содержимого, пустой середины не остаётся»).
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            if (state.management) {
                ManagementBlock(state, onIntent)
            } else {
                ShowcaseBlock(state, onIntent)
            }
        }
        BottomBlock(state, onIntent)
    }
}

/** Витрина: выгоды, выбор периода, восстановление покупки (состояния «не оформлена» и «истекла»). */
@Composable
private fun ShowcaseBlock(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    Benefits(state.benefits)
    // Выбора без цены нет: выбор вслепую, а покупка всё равно недоступна (Р-68 п. 4).
    // Слова причины стоят в нижнем блоке, у самой кнопки — там же, где они стояли бы
    // в состоянии покупки; на месте выбора поэтому ничего не рисуется.
    if (!state.storeUnavailable) {
        Periods(state, onIntent)
    }
    // Восстановление живёт в прокручиваемом теле и остаётся в обоих состояниях: сюда человек
    // попадает после переустановки, уже упёршись в покупку (Р-67 п. 4), а недоступный магазин
    // не отменяет самой возможности восстановить доступ (§5.9: «раскрытие и тихие строки
    // остаются»).
    RestoreRow(onIntent)
}

/**
 * Тихая строка «Восстановить покупку».
 *
 * Смещение на [SklyankaSizes.QuietButtonPadding] гасит собственное поле тихой кнопки:
 * слово встаёт на боковое поле шторки `SklyankaSpacing.Section` 20 dp — тем же приёмом,
 * каким слот модальной шапки гасит своё поле (§5.9, §7). Без смещения тихие строки одной
 * поверхности стояли бы на разных полях: 28 dp у кнопки против 20 dp у строк-переходов.
 */
@Composable
private fun RestoreRow(onIntent: (ProShowcaseIntent) -> Unit) {
    QuietButton(
        text = proText(ProNotice.ACTION_RESTORE),
        onClick = { onIntent(ProShowcaseIntent.Restore) },
        modifier = Modifier
            .offset(x = -SklyankaSizes.QuietButtonPadding)
            .testTag(TAG_PRO_RESTORE),
    )
}

/** Состояние «активна» — это и есть экран управления автопродлением (Р-73 п. 1). */
@Composable
private fun ManagementBlock(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SklyankaShapes.Large)
            .background(colors.surface)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Large)
            .padding(SklyankaSpacing.Large)
            .testTag(TAG_PRO_ACTIVE_CARD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val until = (state.subscription as? ProSubscription.Active)?.until
        Text(
            text = proText(ProNotice.STATE_ACTIVE_UNTIL, until?.let { proDate(it) }.orEmpty()),
            style = SklyankaType.BodyStrong,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        // Число под ценой не повторяется: строка ниже говорит словами, а не второй цифрой
        // (Р-80 п. 3). Цены нет вовсе, если магазин её не назвал, — прочерка не бывает.
        state.renewalPrice?.let { price ->
            Spacer(modifier = Modifier.height(SklyankaSpacing.Micro))
            Text(text = price.text, style = SklyankaType.Stat, color = colors.text)
        }
        Spacer(modifier = Modifier.height(SklyankaSpacing.Micro))
        Text(
            text = proText(ProNotice.RENEW_AUTO),
            style = SklyankaType.CaptionStrong,
            color = colors.accent,
            textAlign = TextAlign.Center,
        )
    }
    // Состав остаётся и здесь: человек видит, что открыто (Р-75 п. 3); примера нет ни у одной
    // выгоды — пункта-повода в этом состоянии не существует.
    Benefits(state.benefits)
    RestoreRow(onIntent)
}

/** Выгоды: короткое имя, значок и разделитель между строками (Р-76 п. 2). */
@Composable
private fun Benefits(rows: List<ProBenefitRow>) {
    val colors = SklyankaTheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_PRO_BENEFITS)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                // Разделитель начинается от колонки текста, а не от бокового поля: он разделяет
                // строки, а не значки.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SklyankaSizes.IconPlateGlyph + SklyankaSpacing.Medium)
                        .height(SklyankaSizes.DividerThickness)
                        .background(colors.lineOnSurface),
                )
            }
            BenefitRow(row)
        }
    }
}

@Composable
private fun BenefitRow(row: ProBenefitRow) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = SklyankaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(SklyankaSizes.IconPlateGlyph)) {
            benefitGlyph(row.benefit, colors.textTertiary)
        }
        Spacer(modifier = Modifier.width(SklyankaSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = proText(row.name),
                style = SklyankaType.BodyDefaultStrong,
                color = colors.text,
            )
            row.example?.let { example ->
                Spacer(modifier = Modifier.height(SklyankaSpacing.Hair))
                Text(
                    text = proText(example),
                    style = SklyankaType.Caption,
                    color = colors.textMuted,
                )
            }
        }
    }
}

/**
 * Значок выгоды.
 *
 * Глиф есть у тех выгод, для которых знак в системе уже существует; остальные строки идут
 * без значка, а не с чужим: глиф, не называющий тему, читался бы как случайная картинка
 * (§5.9, «Значки строк — декоративные»). Чтению с экрана глиф не объявляется — смысл несёт
 * короткое имя рядом (НФТ-33).
 */
@Composable
private fun benefitGlyph(benefit: ProBenefit, color: Color) {
    when (benefit) {
        ProBenefit.SCHEDULES -> CalendarGlyph(color = color, size = SklyankaSizes.IconPlateGlyph)
        ProBenefit.PROFILES -> ProfileGlyph(color = color, size = SklyankaSizes.IconPlateGlyph)
        else -> Unit
    }
}

/** Две равные карточки периодов с ценами магазина; период при открытии не выбран (Р-75 п. 1). */
@Composable
private fun Periods(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    Row(
        // Карточки одной высоты (Р-77 п. 1): высота ряда — по самой высокой карточке,
        // и обе тянутся к ней. Без этого годовая карточка с плашкой «Год дешевле» свисала бы
        // ниже месячной, а норма требует равных карточек и одной линии имён и цен.
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        state.periods.forEach { offer ->
            PeriodCard(
                offer = offer,
                selected = offer.id == state.selected,
                cheaper = offer.id == ProProductId.YEARLY && state.yearlyCheaper,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onIntent(ProShowcaseIntent.SelectPeriod(offer.id)) },
            )
        }
    }
}

@Composable
private fun PeriodCard(
    offer: ProPeriodOffer,
    selected: Boolean,
    cheaper: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SklyankaTheme.colors
    // У карточки года верхние углы прямые: над ней лежит залитая лента, и вместе они
    // читаются одной фигурой (§5.9, Р-78 п. 1). У месяца верхние углы скруглены — лента
    // над ним пуста. Когда утверждение о выгоде неверно, ленты нет ни у кого.
    val shape = RoundedCornerShape(
        topStart = if (cheaper) SklyankaSpacing.None else SklyankaShapes.LargeRadius,
        topEnd = if (cheaper) SklyankaSpacing.None else SklyankaShapes.LargeRadius,
        bottomStart = SklyankaShapes.LargeRadius,
        bottomEnd = SklyankaShapes.LargeRadius,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (selected) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin,
                color = if (selected) colors.accent else colors.lineOnSurface,
                shape = shape,
            )
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .testTag(periodTag(offer.id)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RibbonStrip(label = if (cheaper) proText(ProNotice.YEAR_RIBBON) else null)
        Column(
            modifier = Modifier.fillMaxWidth().padding(SklyankaSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = proText(periodName(offer.id)),
                style = if (selected) SklyankaType.BodyDefaultStrong else SklyankaType.BodyDefault,
                color = colors.text,
            )
            Spacer(modifier = Modifier.height(SklyankaSpacing.Micro))
            // Цена — строка магазина, период — короткая форма рядом с ней (Р-76 п. 4).
            // Числа в ресурсах нет вовсе, поэтому формат-ресурса с числом не заводится.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = offer.price.text, style = SklyankaType.Stat, color = colors.text)
                Spacer(modifier = Modifier.width(SklyankaSpacing.Micro))
                // Разделитель — пунктуация, а не текст языка: числа в нём нет, и второй
                // ресурс с ценой не заводится (Р-68 п. 3). Ступень у короткой формы та же,
                // что у цены: в макете «149 ₽ / мес» — одна строка одной ступени.
                Text(text = PRICE_PERIOD_SEPARATOR, style = SklyankaType.Stat, color = colors.text)
                Spacer(modifier = Modifier.width(SklyankaSpacing.Micro))
                Text(
                    text = proText(periodShortName(offer.id)),
                    style = SklyankaType.Stat,
                    color = colors.text,
                )
            }
            if (cheaper) {
                Spacer(modifier = Modifier.height(SklyankaSpacing.Small))
                CheaperBadge()
            }
        }
    }
}

/**
 * Лента над карточкой года; у месяца тот же слот пуст.
 *
 * Верхняя полоса одинаковой высоты у обоих вариантов — не украшение: без неё имена и цены
 * карточек разъехались бы по вертикали (Р-77 п. 1, Р-78 п. 1). Лента не переносится
 * и не нажимается: она не состояние выбора, а знак выгоды.
 */
@Composable
private fun RibbonStrip(label: String?) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Верхние углы радиусом Large, нижние прямые: зазор до карточки нулевой, и вместе
            // они читаются одной фигурой (§5.9, `.ribbon{border-radius:16px 16px 0 0}` макета).
            .then(if (label != null) Modifier.clip(SklyankaShapes.TopLarge).background(colors.accent) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.orEmpty(),
            style = SklyankaType.CaptionStrong,
            color = colors.onAccent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(
                horizontal = SklyankaSpacing.Small,
                // Поле ленты — 11 × 8 dp (§5.9). Вертикаль берётся существующим токеном
                // полосы с подписью той же ступени; своего имени норма ленте не заводит,
                // а второй токен с тем же значением разошёлся бы с первым при правке.
                vertical = SklyankaSizes.BannerPaddingVertical,
            ),
        )
    }
}

/** «Год дешевле»: залитая плашка, единственная заливка в карточке кроме ленты (Р-77 п. 3). */
@Composable
private fun CheaperBadge() {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Small)
            .clip(SklyankaShapes.Pill)
            .background(colors.accent)
            .testTag(TAG_PRO_YEAR_CHEAPER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = proText(ProNotice.YEAR_CHEAPER),
            style = SklyankaType.CaptionStrong,
            color = colors.onAccent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(
                horizontal = SklyankaSpacing.Small,
                // Поле плашки — 11 × 8 dp, как у ленты и в макете (`.badge-solid`):
                // норма вертикального поля не называет, а макет задаёт его общим с лентой.
                vertical = SklyankaSizes.BannerPaddingVertical,
            ),
        )
    }
}

private fun periodName(id: ProProductId): ProNotice = when (id) {
    ProProductId.MONTHLY -> ProNotice.PERIOD_MONTH
    ProProductId.YEARLY -> ProNotice.PERIOD_YEAR
}

internal fun periodShortName(id: ProProductId): ProNotice = when (id) {
    ProProductId.MONTHLY -> ProNotice.PERIOD_MONTH_SHORT
    ProProductId.YEARLY -> ProNotice.PERIOD_YEAR_SHORT
}

private fun periodTag(id: ProProductId): String = when (id) {
    ProProductId.MONTHLY -> TAG_PRO_PERIOD_MONTH
    ProProductId.YEARLY -> TAG_PRO_PERIOD_YEAR
}

/** Разделитель «цена / период»: пунктуация, а не текст для человека (Р-68 п. 3). */
private const val PRICE_PERIOD_SEPARATOR = "/"

const val TAG_PRO_SHEET = "pro_sheet"

const val TAG_PRO_BENEFITS = "pro_benefits"

const val TAG_PRO_PERIOD_MONTH = "pro_period_month"

const val TAG_PRO_PERIOD_YEAR = "pro_period_year"

const val TAG_PRO_YEAR_CHEAPER = "pro_year_cheaper"

const val TAG_PRO_BUY = "pro_buy"

const val TAG_PRO_RESTORE = "pro_restore"

const val TAG_PRO_MANAGE = "pro_manage"

const val TAG_PRO_CANCEL = "pro_cancel"

const val TAG_PRO_TERMS = "pro_terms"

const val TAG_PRO_MORE = "pro_more"

const val TAG_PRO_NOTICE = "pro_notice"

const val TAG_PRO_ACTIVE_CARD = "pro_active_card"
