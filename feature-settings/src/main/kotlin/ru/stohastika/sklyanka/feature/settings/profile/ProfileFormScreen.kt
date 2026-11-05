package ru.stohastika.sklyanka.feature.settings.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsCard
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.PinnedHeaderBar
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.component.rememberScreenHeaderState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.util.Locale

/**
 * Форма профиля: мастер «Новый профиль» и экран «Профиль» (правка) — одна форма (§5.13–§5.14).
 *
 * Сверху вниз — имя, цвет (у чужого), вопрос о дублировании (у чужого) и панель действия;
 * завершение несёт единственная нижняя кнопка (Д-3), а выхода из шапки нет: шеврона возврата
 * и действия в шапке не бывает, выход один — системный «Назад» (Р-54).
 *
 * Экран stateless: имя, цвет и исход гейта живут в состоянии, наружу уходят намерения.
 * Причину над кнопкой (пустое имя, предел пяти) считает состояние, а не вёрстка:
 * причины не показываются вместе (§5.13).
 */
@Composable
fun ProfileFormScreen(
    state: ProfileFormUiState,
    onIntent: (ProfileFormIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val header = rememberScreenHeaderState()
    val labels = ScreenHeaderLabels(
        title = stringResource(
            if (state.mode is ProfileFormMode.Add) R.string.profile_add_title else R.string.profile_edit_title,
        ),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .testTag(TAG_PROFILE_FORM),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Screen),
            ) {
                // Крупный заголовок — контент и уезжает вместе с содержимым; поверх по порогу
                // встаёт закреплённый ряд 56 dp с одним компактным названием (§7, Р-54).
                ScreenHeader(labels = labels, kind = ScreenHeaderKind.Nested, pinning = header)
                FormBlocks(state = state, onIntent = onIntent)
            }
            ActionBar(state = state, onIntent = onIntent)
        }
        PinnedHeaderBar(
            labels = labels,
            state = header,
            kind = ScreenHeaderKind.Nested,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Блоки формы: имя, цвет и дублирование — то, что человек настраивает у профиля. */
@Composable
private fun FormBlocks(state: ProfileFormUiState, onIntent: (ProfileFormIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(bottom = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        NameBlock(state, onIntent)
        if (state.showsColour) ColourBlock(state, onIntent)
        if (state.showsMirror) MirrorBlock(state, onIntent)
    }
}

/**
 * Имя профиля: надстрочник и поле (§5.13).
 *
 * Пустое поле в новом мастере — норма, а не незавершённость: имя ещё не набрано, и пустая
 * форма не считается ошибкой, пока человек не нажал завершение.
 */
@Composable
private fun NameBlock(state: ProfileFormUiState, onIntent: (ProfileFormIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        BlockLabel(stringResource(R.string.profile_label_name))
        SklyankaTextField(
            value = state.name,
            onValueChange = { onIntent(ProfileFormIntent.NameChanged(it)) },
            modifier = Modifier.fillMaxWidth().testTag(TAG_PROFILE_NAME_FIELD),
        )
    }
}

/**
 * Цвет профиля: ряд из пяти образцов закрытого набора (§2.2, §5.13).
 *
 * Каждый образец — цель не меньше 44 dp с ролью `RadioButton` в группе `selectableGroup`
 * (НФТ-30): у образца нет видимого текста, поэтому его доступное имя — имя цвета в языке
 * интерфейса, и без имени выбор читался бы как «пять одинаковых кружков». Выбранный отличён
 * **не только цветом** (НФТ-33): кольцом по краю кружка и галочкой внутри него.
 *
 * Свободного цвета в наборе может не остаться после переноса (ПЕР-19) — тогда два профиля
 * носят один цвет, и ничего не ломается: различие несут имя, инициал и надпись профиля.
 */
@Composable
private fun ColourBlock(state: ProfileFormUiState, onIntent: (ProfileFormIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        BlockLabel(stringResource(R.string.profile_label_colour))
        Row(
            modifier = Modifier.selectableGroup().testTag(TAG_PROFILE_COLOURS),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            ProfilePalette.all.forEach { colour ->
                ColourSample(
                    colour = colour,
                    selected = colour.argb == state.colour,
                    onSelect = { onIntent(ProfileFormIntent.ColourChosen(colour.argb)) },
                )
            }
        }
    }
}

/** Образец цвета: кружок цвета профиля 40 dp в цели 44 dp; выбранный — кольцо и галочка. */
@Composable
private fun ColourSample(colour: ProfileColor, selected: Boolean, onSelect: () -> Unit) {
    val colors = SklyankaTheme.colors
    val label = stringResource(colour.labelKey())
    Box(
        modifier = Modifier
            .size(SklyankaSizes.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label }
            .testTag(colour.tag()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.IconPlate)
                .background(colour.color, SklyankaShapes.Pill)
                .then(
                    if (selected) {
                        Modifier.border(SklyankaSizes.SelectionRing, colors.accent, SklyankaShapes.Pill)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // Белая галочка держит AA на всех пяти красках набора (§5.13): состояние
                // называется не только кольцом, но и знаком внутри (НФТ-33).
                CheckGlyph(color = colors.onAccent, size = SklyankaSizes.MarkButtonGlyph)
            }
        }
    }
}

/**
 * Вопрос о дублировании напоминаний (ПРФ-9, Р-29).
 *
 * Подпись и пояснение вместе: человек отвечает на вопрос «чей телефон зазвонит», а не
 * на слово «дублировать». По умолчанию включено — присмотр без напоминаний себе смысла
 * почти не имеет, но выбор остаётся за человеком.
 */
@Composable
private fun MirrorBlock(state: ProfileFormUiState, onIntent: (ProfileFormIntent) -> Unit) {
    SettingsCard {
        SklyankaSwitchRow(
            label = stringResource(R.string.profile_switch_mirror),
            caption = stringResource(R.string.profile_switch_mirror_hint),
            checked = state.mirrorReminders,
            onCheckedChange = { onIntent(ProfileFormIntent.MirrorChanged(it)) },
            modifier = Modifier.testTag(TAG_PROFILE_MIRROR),
        )
    }
}

/**
 * Панель действия: причина словами, если она есть, и единственная нижняя кнопка (Д-3).
 *
 * Предел пяти и пустое имя переводят кнопку в недоступность и называют причину словами:
 * недоступное действие без объяснения человек читает как поломку (§5.13, П-7).
 */
@Composable
private fun ActionBar(state: ProfileFormUiState, onIntent: (ProfileFormIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(top = SklyankaSpacing.Large, bottom = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        state.block?.let { BlockReason(it) }
        PrimaryButton(
            text = stringResource(
                if (state.mode is ProfileFormMode.Add) R.string.profile_add_action else R.string.profile_save_action,
            ),
            onClick = { onIntent(ProfileFormIntent.Submit) },
            // Пока идёт запись, кнопка занята, а не выглядит готовой к нажатию (НФТ-29):
            // второе нажатие «на всякий случай» не имеет права завести второй профиль.
            state = when {
                state.saving -> ButtonState.Busy
                state.block == ProfileFormBlock.Name || state.block == ProfileFormBlock.Limit -> ButtonState.Disabled
                else -> ButtonState.Normal
            },
            modifier = Modifier.fillMaxWidth().testTag(TAG_PROFILE_SUBMIT),
        )
    }
}

/** Единственная причина над кнопкой — словами, а не недоступностью без объяснения (§5.13). */
@Composable
private fun BlockReason(block: ProfileFormBlock) {
    Text(
        text = when (block) {
            ProfileFormBlock.Name -> stringResource(R.string.profile_blocked_name)
            ProfileFormBlock.Limit -> stringResource(R.string.profile_add_blocked_limit)
        },
        style = SklyankaType.BodyDefault,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier.testTag(TAG_PROFILE_BLOCK),
    )
}

/** Надстрочник блока: имя блока прописными не набирается — текст записан так, как его читают. */
@Composable
private fun BlockLabel(text: String) {
    Text(text = text, style = SklyankaType.Overline, color = SklyankaTheme.colors.textMuted)
}

/** Доступное имя образца цвета в языке интерфейса (§10). */
private fun ProfileColor.labelKey(): Int = when (this) {
    ProfileColor.Terracotta -> R.string.cd_profile_colour_terracotta
    ProfileColor.Teal -> R.string.cd_profile_colour_teal
    ProfileColor.Olive -> R.string.cd_profile_colour_olive
    ProfileColor.Plum -> R.string.cd_profile_colour_plum
    ProfileColor.Indigo -> R.string.cd_profile_colour_indigo
}

/** Точки опоры экранных проверок: образец узнаётся по имени цвета, а не по порядку в ряду. */
private fun ProfileColor.tag(): String = "profile_colour_${name.lowercase(Locale.ROOT)}"

const val TAG_PROFILE_FORM = "profile_form"
const val TAG_PROFILE_NAME_FIELD = "profile_name_field"
const val TAG_PROFILE_COLOURS = "profile_colours"
const val TAG_PROFILE_MIRROR = "profile_mirror"
const val TAG_PROFILE_SUBMIT = "profile_submit"

/** Строка причины над кнопкой: «Укажите имя профиля» или предел. */
const val TAG_PROFILE_BLOCK = "profile_block"
