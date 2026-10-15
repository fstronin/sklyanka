package ru.stohastika.sklyanka.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.feature.meds.MedsRoute
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveRoute
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveViewModel
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardRoute
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormRoute
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.ui.motion.AnimatedLayer
import ru.stohastika.sklyanka.ui.motion.LayerMotion

/**
 * Экраны раздела «Лекарства» и переходы между ними.
 *
 * Маршруты собираются здесь, а не в экранных модулях: `feature-*` друг от друга не
 * зависят, и знание о том, что нажатие на карточку ведёт в карточку лекарства, а
 * сегмент «Архив» — в архив, принадлежит `app` (`docs/archive/tasks/SKL-10/TDD.md §8.2`).
 *
 * Библиотеки навигации здесь нет намеренно: мест в разделе пять — список, архив, новая форма,
 * карточка и правка, те же, что называет §5.1 (`docs/norm/04-behavior.md`), — и все они внутри
 * одной Activity, поэтому состояние стека выражается списком значений. Граф на весь продукт
 * появится вместе с нижней панелью и переключением профиля (SKL-15).
 */
@Composable
@Suppress("LongParameterList")
fun MedsSection(
    viewModels: MedsViewModels,
    onLeaveSection: () -> Unit,
    onOpenToday: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    initial: MedsDestination = MedsDestination.List,
    modifier: Modifier = Modifier,
    /**
     * Открыть витрину «Про» из формы лекарства (SKL-117, Р-67 п. 2).
     *
     * Поверхность покупки одна на все точки входа: раздел только передаёт сигнал формы
     * в `app`, а шторку рисует он же.
     */
    onOpenPro: () -> Unit = {},
) {
    val stack = rememberMedsStack(initial)
    // Системная кнопка «Назад» обязана вести туда же, куда стрелка на экране: иначе
    // человек, закрывший карточку кнопкой телефона, оказывается вне раздела.
    BackHandler(enabled = !LocalModalSurfaceOpen.current) { if (!stack.pop()) onLeaveSection() }

    AnimatedLayer(layer = stack.current, motion = stack.motion) { destination ->
        when (destination) {
            MedsDestination.List -> MedsRoute(
                listViewModel = viewModels.list,
                onOpenMedicationCard = { id -> stack.push(MedsDestination.Card(id)) },
                onOpenArchive = { stack.push(MedsDestination.Archive) },
                // Круглая кнопка списка поднимает форму **уровнем стека**, а не флагом
                // маршрута (SKL-150): снятая системной «Назад», она оставляет человека
                // в списке, а не выбрасывает из раздела на «Сегодня».
                onOpenNewMedication = { stack.push(MedsDestination.NewMedication) },
                onOpenToday = onOpenToday,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )

            MedsDestination.Archive -> ArchiveRoute(
                viewModel = viewModels.archive,
                onSelectActive = { stack.pop() },
                onOpenMedicationCard = { id -> stack.push(MedsDestination.Card(id)) },
                onOpenToday = onOpenToday,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )

            is MedsDestination.Card -> MedicationCardRoute(
                medicationId = destination.medicationId,
                viewModel = viewModels.card,
                onBack = { stack.pop() },
                onEdit = { id -> stack.push(MedsDestination.Edit(id)) },
                modifier = modifier,
            )

            is MedsDestination.Edit -> MedicationFormRoute(
                viewModel = viewModels.form,
                medicationId = destination.medicationId,
                onDone = { stack.pop() },
                modifier = modifier,
                onOpenPro = onOpenPro,
            )

            MedsDestination.NewMedication -> MedicationFormRoute(
                viewModel = viewModels.form,
                medicationId = null,
                onDone = { if (!stack.pop()) onLeaveSection() },
                modifier = modifier,
                onOpenPro = onOpenPro,
            )
        }
    }
}

/**
 * Состояния раздела.
 *
 * Правка — отдельное место в стеке, а не флаг карточки: после сохранения человек обязан
 * вернуться именно в карточку и увидеть там новое расписание (критерий 16 SKL-10).
 */
sealed interface MedsDestination {

    data object List : MedsDestination

    data object Archive : MedsDestination

    /**
     * Форма нового лекарства: вход с экрана «Сегодня» («Добавить лекарство», SKL-08)
     * и круглой кнопкой списка (SKL-150).
     *
     * Оба входа — один и тот же уровень стека: возврат с формы повторяет возврат,
     * и человек в обоих случаях остаётся в разделе.
     */
    data object NewMedication : MedsDestination

    data class Card(val medicationId: String) : MedsDestination

    data class Edit(val medicationId: String) : MedsDestination
}

/**
 * Четыре ViewModel раздела одним значением.
 *
 * Иначе подпись [MedsSection] превращается в четыре однотипных параметра, которые
 * путают местами на первом же вызове.
 */
class MedsViewModels(
    val list: MedicationListViewModel,
    val form: MedicationFormViewModel,
    val card: MedicationCardViewModel,
    val archive: ArchiveViewModel,
)

/** Стек раздела: список всегда на дне и не снимается. */
class MedsStack internal constructor(private val entries: SnapshotStateList<MedsDestination>) {

    var motion: LayerMotion by mutableStateOf(LayerMotion.Crossfade)
        private set

    val current: MedsDestination get() = entries.last()

    fun push(destination: MedsDestination) {
        motion = if (destination == MedsDestination.Archive) LayerMotion.Crossfade else LayerMotion.Forward
        entries += destination
    }

    /** @return false, если снимать нечего: человек уже на списке лекарств. */
    fun pop(): Boolean {
        if (entries.size <= 1) return false
        val closing = entries.removeAt(entries.lastIndex)
        motion = if (closing == MedsDestination.Archive) LayerMotion.Crossfade else LayerMotion.Back
        return true
    }
}

/**
 * Стек раздела, начатый с нужного места.
 *
 * Список всегда на дне, даже когда человек пришёл сразу в карточку с экрана «Сегодня»:
 * «Назад» из карточки обязана вести в список лекарств (критерий 23 SKL-10), а не выбрасывать
 * из раздела.
 *
 * Стек — сохранённое состояние композиции: поворот пересобирает её целиком, и без этого
 * человек терял бы место в разделе (SKL-142). Уход из раздела состояние отпускает само
 * (`rememberSaveable` живёт, пока живёт композиция), поэтому возвращение в раздел
 * начинается с его начала, как и раньше.
 */
@Composable
private fun rememberMedsStack(initial: MedsDestination): MedsStack {
    val entries = rememberSaveable(initial, saver = MedsStackSaver) {
        mutableStateListOf<MedsDestination>(MedsDestination.List).also {
            if (initial != MedsDestination.List) it += initial
        }
    }
    return remember(entries) { MedsStack(entries) }
}

/**
 * Место в разделе строкой: сохранённое состояние обязано пережить пересоздание процесса
 * записи состояния, а `MedsDestination` — не `Parcelable`.
 */
private const val MEDS_LIST = "list"
private const val MEDS_ARCHIVE = "archive"
private const val MEDS_NEW = "new"
private const val MEDS_CARD = "card:"
private const val MEDS_EDIT = "edit:"

internal val MedsStackSaver: Saver<SnapshotStateList<MedsDestination>, Any> = listSaver(
    save = { stack -> stack.map(::encodeDestination) },
    restore = { encoded ->
        val restored = encoded.mapNotNull { decodeDestination(it as String) }
        mutableStateListOf<MedsDestination>().also { entries ->
            entries += if (restored.isEmpty()) listOf(MedsDestination.List) else restored
        }
    },
)

internal fun encodeDestination(destination: MedsDestination): String = when (destination) {
    MedsDestination.List -> MEDS_LIST
    MedsDestination.Archive -> MEDS_ARCHIVE
    MedsDestination.NewMedication -> MEDS_NEW
    is MedsDestination.Card -> MEDS_CARD + destination.medicationId
    is MedsDestination.Edit -> MEDS_EDIT + destination.medicationId
}

/** Неузнанное место не восстанавливается: пустой стек дал бы экран без списка на дне. */
internal fun decodeDestination(encoded: String): MedsDestination? = when {
    encoded == MEDS_LIST -> MedsDestination.List
    encoded == MEDS_ARCHIVE -> MedsDestination.Archive
    encoded == MEDS_NEW -> MedsDestination.NewMedication
    encoded.startsWith(MEDS_CARD) -> MedsDestination.Card(encoded.removePrefix(MEDS_CARD))
    encoded.startsWith(MEDS_EDIT) -> MedsDestination.Edit(encoded.removePrefix(MEDS_EDIT))
    else -> null
}
