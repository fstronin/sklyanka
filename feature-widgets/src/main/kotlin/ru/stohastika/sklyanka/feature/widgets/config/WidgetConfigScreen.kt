package ru.stohastika.sklyanka.feature.widgets.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.feature.widgets.R
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.ChevronGlyph
import ru.stohastika.sklyanka.ui.component.ExpandedHeader
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ProMark
import ru.stohastika.sklyanka.ui.component.ScreenFrame
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/** Теги стенда экрана настройки виджета (SKL-24, SKL-26). */
public const val TAG_WIDGET_CONFIG: String = "widget_config"
public const val TAG_WIDGET_TYPE_ROW: String = "widget_type_row"
public const val TAG_WIDGET_MEDICATION_ROW: String = "widget_medication_row"
public const val TAG_WIDGET_PRO_REQUIRED: String = "widget_pro_required"
public const val TAG_WIDGET_ADD: String = "widget_add"

/**
 * Экран настройки виджета (`docs/norm/06-design.md` §5.12, Р-32).
 *
 * Экран открывается с домашнего экрана прошивки, и выхода у него два: системный «Назад»
 * (он же отмена установки — виджет не появляется) и завершение. Шеврона возврата и слова
 * «Отмена» у экрана нет: возвращаться внутри приложения некуда (Р-54).
 *
 * **В бесплатном продукте оплаты нет (Р-86).** Гейт состава проходит, и завершение доступно
 * сразу после выбора; метки «Про» не рисуются (`ProMark`), а слова причины не появляются
 * вместе с платным контуром (`LocalProUiEnabled`). В `pro`-варианте причина стоит словами
 * **над** кнопкой, а кнопка остаётся «Добавить виджет» и не приглушается: недоступное
 * действие говорит «нельзя», а правда — «можно, по подписке» (П-7). Нажатие открывает
 * витрину вместо завершения; вернувшись с покупкой, тот же экран завершает установку —
 * перезапуска и второго входа не требуется.
 *
 * **Раскладка — две части, а не наложение** (§4.1): прокручиваемое содержимое с весом
 * и панель действия соседом снизу, в потоке. Панель обязана идти после содержимого:
 * при крупном шрифте содержимое растёт, и наложенная панель перекрыла бы рубрику и карточку.
 *
 * @param onComplete завершение установки (запись привязки и `RESULT_OK`).
 * @param onOpenPro открыть витрину с пунктом-поводом [ProPoint.WIDGETS].
 */
@Composable
fun WidgetConfigScreen(
    state: WidgetConfigState,
    onSelectType: (WidgetType) -> Unit,
    onSelectMedication: (String) -> Unit,
    onComplete: () -> Unit,
    onOpenPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    ScreenFrame(
        labels = ScreenHeaderLabels(title = stringResource(R.string.widget_setup_title)),
        modifier = modifier.testTag(TAG_WIDGET_CONFIG),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            ) {
                // Крупный заголовок шапки — первым блоком прокрутки (§5.12, §13.2):
                // вне прокрутки заголовок не уезжает за порог, и правило не действует.
                ExpandedHeader()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SklyankaSpacing.Section),
                    verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
                ) {
                    // Пояснение отвечает на вопрос, который иначе остался бы без ответа:
                    // профиль у экземпляра виджета не выбирается (ПР-24, §5.12).
                    Text(
                        text = stringResource(R.string.widget_setup_hint),
                        style = SklyankaType.BodyDefault,
                        color = SklyankaTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = SklyankaSpacing.Block),
                    )
                    Text(
                        text = stringResource(R.string.widget_section_type).uppercase(),
                        style = SklyankaType.Overline,
                        color = SklyankaTheme.colors.textMuted,
                    )
                    TypeCard(state.type, onSelectType)
                    if (state.showMedicationRow) {
                        MedicationCard(state, onOpenPicker = { if (!state.hasNoMedications) pickerOpen = true })
                    }
                }
            }
            ActionPanel(state = state, onComplete = onComplete, onOpenPro = onOpenPro)
        }
    }
    if (pickerOpen) {
        MedicationPicker(
            state = state,
            onSelect = { medicationId ->
                onSelectMedication(medicationId)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** Карточка-группа: `surface`, рамка 1 dp `lineOnSurface`, радиус `Card` (§5.12). */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, RoundedCornerShape(SklyankaShapes.CardRadius))
            .border(
                width = SklyankaSizes.BorderThin,
                color = SklyankaTheme.colors.lineOnSurface,
                shape = RoundedCornerShape(SklyankaShapes.CardRadius),
            ),
    ) {
        Column { content() }
    }
}

/** Карточка типов: три строки, у каждой метка «Про» в платном контуре, выбранная — акцентом и галочкой (§5.12). */
@Composable
private fun TypeCard(selected: WidgetType, onSelect: (WidgetType) -> Unit) {
    GroupCard {
        WidgetType.entries.forEachIndexed { index, type ->
            if (index > 0) ListDivider()
            TypeRow(type, isSelected = type == selected, onSelect = { onSelect(type) })
        }
    }
}

@Composable
private fun TypeRow(type: WidgetType, isSelected: Boolean, onSelect: () -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .semantics { selected = isSelected }
            // Поле строки — то же, что у строки выбора значения (`ListPickerDialog`, §5.10):
            // та же сборка строки карточки-группы, и второго поля у неё быть не может.
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium)
            .testTag(TAG_WIDGET_TYPE_ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(type.label),
            style = if (isSelected) SklyankaType.BodyDefaultStrong else SklyankaType.BodyDefault,
            color = if (isSelected) colors.accent else colors.text,
        )
        ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small))
        Box(modifier = Modifier.weight(1f))
        if (isSelected) CheckGlyph(color = colors.accent)
    }
}

/** Карточка лекарства: та же карточка-группа, что у типов (§5.12). */
@Composable
private fun MedicationCard(state: WidgetConfigState, onOpenPicker: () -> Unit) {
    GroupCard {
        MedicationRow(state, onOpenPicker)
    }
}

/** Строка лекарства: подпись, значение и шеврон; ведёт в диалог выбора (§5.12). */
@Composable
private fun MedicationRow(state: WidgetConfigState, onOpenPicker: () -> Unit) {
    val colors = SklyankaTheme.colors
    val selected = state.medications.firstOrNull { it.id == state.selectedMedicationId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.hasNoMedications, onClick = onOpenPicker)
            // Цель нажатия расширяется до поля, а не вокруг него: строка однострочная и
            // может оказаться ниже 44 dp, но нарисованное остаётся прежним — как у строки
            // выбора значения (`ListPickerDialog.OptionRow`, НФТ-29, §6.2).
            .minTouchTarget()
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium)
            .testTag(TAG_WIDGET_MEDICATION_ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.widget_medication_row),
            style = SklyankaType.BodyDefault,
            color = colors.text,
        )
        Box(modifier = Modifier.weight(1f))
        when {
            // У профиля нет лекарств: значения нет, строка не нажимается — пустого диалога
            // система не открывает и сломанной нажимаемой строки не рисует.
            state.hasNoMedications -> Text(
                text = stringResource(R.string.widget_medication_empty),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
            )

            selected != null -> Text(
                text = selected.name,
                style = SklyankaType.LabelStrong,
                color = colors.text,
            )

            else -> Text(
                text = stringResource(R.string.widget_medication_none),
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
            )
        }
        ChevronGlyph(color = colors.iconFaint, modifier = Modifier.padding(start = SklyankaSpacing.Small))
    }
}

/** Выбор лекарства существующим диалогом дизайн-системы: второго порядка и вида не заводится. */
@Composable
private fun MedicationPicker(state: WidgetConfigState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val selected = state.medications.firstOrNull { it.id == state.selectedMedicationId }
    ListPickerDialog(
        title = stringResource(R.string.widget_medication_row),
        options = state.medications,
        selected = selected,
        label = { it.name },
        onSelect = { onSelect(it.id) },
        onDismiss = onDismiss,
    )
}

/**
 * Панель действия: слова причины и кнопка завершения (§5.12).
 *
 * Идёт в потоке под прокруткой и содержимого не перекрывает (§4.1): при крупном шрифте
 * содержимое растёт, и наложение отняло бы у человека рубрику и карточку.
 */
@Composable
private fun ActionPanel(state: WidgetConfigState, onComplete: () -> Unit, onOpenPro: () -> Unit) {
    val colors = SklyankaTheme.colors
    val proUiEnabled = LocalProUiEnabled.current
    // В бесплатном контуре оплаты нет (Р-86): доступ гарантирован, и ветка витрины не должна
    // наступить даже в первый кадр, пока кэш ещё не прочитан, — иначе кнопка вела бы в никуда
    // (П-7). Пока доступ не прочитан, кнопка просто недоступна, как при незаконченном выборе.
    val access = state.proIncluded || !proUiEnabled
    Column(
        // Поля панели действия — §4.1: верх `Large` 14, бока `Section` 20, низ `Screen` 24.
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SklyankaSpacing.Section,
                top = SklyankaSpacing.Large,
                end = SklyankaSpacing.Section,
                bottom = SklyankaSpacing.Screen,
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        if (proUiEnabled && !state.proIncluded) {
            Text(
                text = stringResource(R.string.widget_pro_required),
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_WIDGET_PRO_REQUIRED),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.widget_action_add),
            onClick = if (access) onComplete else onOpenPro,
            // Причина платная и от выбора не зависит: без завершённого выбора кнопка
            // недоступна, а в платном контуре без подписки — ведёт в витрину (§5.12).
            state = if (access && !state.printable) ButtonState.Disabled else ButtonState.Normal,
            modifier = Modifier.fillMaxWidth().testTag(TAG_WIDGET_ADD),
        )
    }
}

/**
 * Разделитель строк карточки-группы: от колонки текста с обеих сторон (§5.12).
 *
 * Сборка — те же поля, что у разделителя строки выбора значения (`ListPickerDialog`):
 * поле `Block` с обеих сторон, иначе правый конец упирался бы в рамку карточки
 * и линия выглядела бы оборванной.
 */
@Composable
private fun ListDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.lineOnSurface),
    )
}
