package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.meds.ChoiceTile
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.SectionLabel
import ru.stohastika.sklyanka.feature.meds.doseSuffix
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.message
import ru.stohastika.sklyanka.feature.meds.medicationPreviewNumbers
import ru.stohastika.sklyanka.feature.meds.medicationPreviewTitle
import ru.stohastika.sklyanka.feature.meds.previewDoseLabel
import ru.stohastika.sklyanka.feature.meds.strengthUnitLabel
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.MedicationGlyph
import ru.stohastika.sklyanka.ui.component.MedicationPreview
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.component.TabletShapeGlyph
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Блоки формы, идущие до переключателя режима: название, форма выпуска, вид таблетки, доза.
 *
 * Порядок блоков — отступление от макета, зафиксированное в `TDD.md §5.6` (дефект
 * постановки Д-2): переключатель режима стоит выше блоков расписания, потому что именно
 * он определяет, нужно ли время вообще. В макете он стоит после «Времени приёма», и
 * человек, дойдя до него, терял два заполненных блока.
 */

/** Блок формы: надпись сверху, содержимое снизу, якорь для прокрутки к нарушению. */
@Composable
internal fun FormBlock(
    label: String?,
    field: DraftField?,
    anchors: FormAnchors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (field == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { anchors.put(field, it.positionInWindow().y.toInt()) }
                },
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        if (label != null) SectionLabel(label)
        content()
    }
}

/** «Название» — единственное поле, без которого кнопка сохранения недоступна. */
@Composable
internal fun NameBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(stringResource(R.string.form_label_name), DraftField.NAME, anchors) {
        SklyankaTextField(
            value = state.draft.name,
            onValueChange = { onIntent(MedFormIntent.NameChanged(it)) },
            errorText = state.violationOf(DraftField.NAME)?.message(),
            modifier = Modifier.fillMaxWidth().testTag(TAG_NAME_FIELD),
        )
    }
}

/**
 * Блок-превью выбора (§4.16, ОВ-94): сразу под «Названием», перед «Формой выпуска».
 *
 * Обновляется вместе с черновиком и не нажимается: это не действие, а взгляд на то,
 * как лекарство будет узнаваться в списке и карточке. Заголовок и подпись собирает
 * `feature-meds` из ресурсов и черновика — русского текста в `core-ui` нет.
 */
@Composable
internal fun PreviewBlock(state: MedFormUiState) {
    val draft = state.draft
    val dose = draft.doseAmount?.let { amount -> previewDoseLabel(amount, state.applicability.doseUnit) }
    val strength = draft.strength?.let { amount ->
        draft.strengthUnit?.let { unit ->
            "${amount.stripTrailingZeros().toPlainString()} ${strengthUnitLabel(unit.code)}"
        }
    }
    MedicationPreview(
        form = draft.form,
        shape = draft.shape,
        title = medicationPreviewTitle(draft.form, draft.shape),
        caption = medicationPreviewNumbers(dose, strength),
        modifier = Modifier.fillMaxWidth().testTag(TAG_MEDICATION_PREVIEW),
    )
}

/** «Форма выпуска» — сетка 3×2 из шести плиток (`AddMed.dc.html`). */
@Composable
internal fun ReleaseFormBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    val colors = SklyankaTheme.colors
    FormBlock(stringResource(R.string.form_label_release_form), null, anchors) {
        MedicationForm.entries.chunked(FORMS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
                row.forEach { form ->
                    val selected = state.draft.form == form
                    ChoiceTile(
                        label = form.label(),
                        selected = selected,
                        onClick = { onIntent(MedFormIntent.ReleaseFormChanged(form)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(SklyankaSizes.FormTile)
                            .testTag(TAG_RELEASE_FORM),
                        glyph = {
                            // Тег на обёртке значка, а не на самой Canvas: приёмка ищет
                            // силуэт на каждой плитке, не читая пиксели (SKL-66).
                            Box(modifier = Modifier.testTag(TAG_FORM_TILE_GLYPH)) {
                                MedicationGlyph(
                                    form = form,
                                    shape = null,
                                    color = if (selected) colors.accent else colors.textMuted,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * «Вид таблетки» — ряд из пяти силуэтов.
 *
 * Блок существует только для формы «таблетка» (ПР-9, ИНВ-16, ОВ-88); при смене
 * формы выпуска на неприменимую он не просто скрывается — выбранное значение
 * сбрасывается в черновике, иначе оно уедет в базу незаметно для человека.
 *
 * Видимого текста у кнопок нет, поэтому подписи для чтения с экрана обязательны
 * и взяты дословно из `aria-label` макета (НФТ-30).
 */
@Composable
internal fun TabletShapeBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    if (!state.applicability.shapeVisible) return
    val colors = SklyankaTheme.colors
    FormBlock(stringResource(R.string.form_label_tablet_shape), null, anchors) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
            modifier = Modifier.testTag(TAG_TABLET_SHAPE_ROW),
        ) {
            TabletShape.entries.forEach { shape ->
                val selected = state.draft.shape == shape
                val label = shape.label()
                Box(
                    modifier = Modifier
                        .size(SklyankaSizes.ShapeButton)
                        .background(
                            if (selected) colors.accentSoft else colors.surface,
                            SklyankaShapes.Large,
                        )
                        .border(
                            if (selected) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin,
                            if (selected) colors.accent else colors.line,
                            SklyankaShapes.Large,
                        )
                        .clickable(role = Role.RadioButton) { onIntent(MedFormIntent.ShapeChanged(shape)) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    TabletShapeGlyph(
                        shape = shape,
                        color = if (selected) colors.accent else colors.textMuted,
                    )
                }
            }
        }
    }
}

/**
 * «Доза на один приём» и «Дозировка препарата» — два названных блока, а не ряд из двух полей.
 *
 * Незавершённость новой формы читалась именно из одного заголовка над двумя полями, второе
 * из которых ничем не названо (V-3). Доза обязательна (ИНВ-4), дозировка — нет (ИНВ-5), и
 * нарушение печатается под своим блоком: блоков два, и пояснение дозы не уезжает под
 * дозировку (§5.3).
 *
 * Единица количества следует за формой выпуска и человеком не выбирается (Р-45). Единица
 * дозировки — вторая половина значения, и выбирается она из закрытого списка диалогом
 * дизайн-системы: цель нажатия — сама единица в поле (§5.3), свободного ввода нет вовсе.
 */
@Composable
internal fun DoseBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    val amountLabel = stringResource(R.string.cd_dose_amount)
    val strengthLabel = stringResource(R.string.cd_strength)
    val unitLabel = stringResource(R.string.form_label_strength_unit)
    var unitPickerOpen by remember { mutableStateOf(false) }
    FormBlock(stringResource(R.string.form_label_dose), DraftField.DOSE, anchors) {
        SklyankaTextField(
            value = state.inputs.doseAmount,
            onValueChange = { onIntent(MedFormIntent.DoseAmountChanged(it)) },
            unitLabel = state.applicability.doseUnit.doseSuffix(),
            numeric = true,
            errorText = state.violationOf(DraftField.DOSE)?.message(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = amountLabel }
                .testTag(TAG_DOSE_FIELD),
        )
    }
    FormBlock(stringResource(R.string.form_label_strength), DraftField.STRENGTH, anchors) {
        SklyankaTextField(
            value = state.inputs.strength,
            onValueChange = { onIntent(MedFormIntent.StrengthChanged(it)) },
            // Единица по умолчанию — «мг»: она появляется вместе с числом (Р-45, §5.3)
            // и стоит в поле даже тогда, когда человек ещё ничего не набрал, — иначе
            // куда нажимать, чтобы сменить её, было бы не видно.
            unitLabel = strengthUnitLabel((state.draft.strengthUnit ?: StrengthUnit.MILLIGRAM).code),
            onUnitClick = { unitPickerOpen = true },
            unitContentDescription = unitLabel,
            numeric = true,
            errorText = state.violationOf(DraftField.STRENGTH)?.message(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = strengthLabel }
                .testTag(TAG_STRENGTH_FIELD),
        )
        // Необязательное поле называет себя словами: конвенция формы — обязательное молчит,
        // необязательное поясняет, чем оно не участвует в расчётах (§5.3).
        BlockHint(stringResource(R.string.form_strength_hint))
    }
    if (unitPickerOpen) {
        ListPickerDialog(
            title = unitLabel,
            options = StrengthUnit.entries,
            selected = state.draft.strengthUnit,
            label = { unit -> unit.label() },
            onSelect = { unit ->
                onIntent(MedFormIntent.StrengthUnitChanged(unit))
                unitPickerOpen = false
            },
            onDismiss = { unitPickerOpen = false },
            modifier = Modifier.testTag(TAG_STRENGTH_UNIT_DIALOG),
        )
    }
}

/** Пояснение под блоком: пояснение режима, причина недоступной кнопки. */
@Composable
internal fun BlockHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SklyankaType.CaptionSmall,
        color = SklyankaTheme.colors.textMuted,
        modifier = modifier,
    )
}

private const val FORMS_PER_ROW = 3

const val TAG_NAME_FIELD = "form_name"
const val TAG_MEDICATION_PREVIEW = "form_medication_preview"
const val TAG_RELEASE_FORM = "form_release_form"
const val TAG_FORM_TILE_GLYPH = "form_release_form_glyph"
const val TAG_TABLET_SHAPE_ROW = "form_tablet_shape"
const val TAG_DOSE_FIELD = "form_dose_amount"
const val TAG_STRENGTH_FIELD = "form_strength"

/** Диалог выбора единицы дозировки: цель нажатия — единица в поле дозировки (Р-45). */
const val TAG_STRENGTH_UNIT_DIALOG = "form_strength_unit_picker"
