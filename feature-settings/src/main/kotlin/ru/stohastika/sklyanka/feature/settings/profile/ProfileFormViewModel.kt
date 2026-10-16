package ru.stohastika.sklyanka.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.profile.CreateProfileOutcome
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.profile.RenameProfileOutcome
import ru.stohastika.sklyanka.domain.profile.RenameProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.stohastika.sklyanka.domain.profile.SetProfileColourUseCase
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import javax.inject.Inject

/**
 * Форма профиля: мастер добавления и экран правки (SKL-121, §5.13–§5.14).
 *
 * Решение «можно ли завести профиль» считает домен ([ProfileCreationGate]) — по числу
 * **заведённых** профилей и доступу из кэша подписки, а не по подписке самой (ПРФ-33).
 * Форма лишь показывает исход словами и меткой и не ходит в магазин (Р-68 п. 7).
 *
 * Доступ читается снимком при открытии и перечитывается после закрытия витрины
 * ([refreshProAccess], образец формы лекарства SKL-117): покупка пишет кэш, и без
 * перечитывания то же нажатие снова открывало бы витрину вместо создания — человек
 * остался бы без набранного имени. Скрытого автопродолжения нет: создание завершает
 * та же кнопка (§5.13, Р-84 п. 2).
 *
 * Создание не делает новый профиль активным (ПРФ-18): активный профиль меняет только
 * осознанное переключение (ПРФ-19), и форма его не трогает.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val createProfile: CreateProfileUseCase,
    private val renameProfile: RenameProfileUseCase,
    private val setProfileColour: SetProfileColourUseCase,
    private val setMirrorReminders: SetMirrorRemindersUseCase,
    /** Доступ «Про» по кэшу подписки: гейт читает его, а не магазин (Р-22, Р-68 п. 7). */
    private val proStatus: ObserveProStatusUseCase,
) : ViewModel() {

    private val mutable = MutableStateFlow<ProfileFormUiState?>(null)

    /** `null` — форма ещё не открыта: поля читаются из хранилища при открытии. */
    val state: StateFlow<ProfileFormUiState?> = mutable.asStateFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Успешное создание или сохранение закрывает форму один раз.
     *
     * Событие, а не состояние: открытие формы после сохранения — новая подписка, и
     * прошлое сохранение ей не принадлежит (образец формы лекарства, SKL-57).
     */
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    private val _showcase = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Сигнал «открыть витрину»: создание закрыто платной границей (ПРФ-33, Р-84 п. 2).
     *
     * Витрина — одна поверхность на все точки входа (Р-67): форма не рисует шторку сама,
     * а просит `app` открыть её с [ru.stohastika.sklyanka.domain.pro.ProPoint.PROFILES].
     */
    val showcase: SharedFlow<Unit> = _showcase.asSharedFlow()

    /**
     * Открыть форму в названном режиме.
     *
     * Состояние гасится синхронно, до чтения хранилища: иначе первый кадр показывал бы
     * предыдущий профиль, пока асинхронное чтение не заменит его.
     */
    fun open(mode: ProfileFormMode) {
        mutable.value = null
        viewModelScope.launch { load(mode) }
    }

    /**
     * Перечитать доступ «Про» после закрытия витрины (Р-84 п. 2).
     *
     * Форма держит снимок доступа с открытия, а покупка пишет кэш подписки: без
     * перечитывания причина «больше двух профилей» не ушла бы, и создание не завершилось
     * бы тем же нажатием. Состояние полей при этом не трогается — человек остаётся
     * в той же форме с заполненным именем.
     */
    fun refreshProAccess() {
        if (mutable.value == null) return
        viewModelScope.launch { updateCreation() }
    }

    fun onIntent(intent: ProfileFormIntent) {
        when (intent) {
            is ProfileFormIntent.Open -> open(intent.mode)
            is ProfileFormIntent.NameChanged -> mutable.value = mutable.value?.copy(name = intent.text)
            is ProfileFormIntent.ColourChosen -> mutable.value = mutable.value?.copy(colour = intent.colour)
            is ProfileFormIntent.MirrorChanged -> mutable.value = mutable.value?.copy(mirrorReminders = intent.enabled)
            ProfileFormIntent.Submit -> submit()
        }
    }

    private suspend fun load(mode: ProfileFormMode) {
        val included = proStatus().included
        val created = profiles.count()
        val creation = ProfileCreationGate.decide(created, included)
        when (mode) {
            ProfileFormMode.Add -> {
                mutable.value = ProfileFormUiState(
                    mode = mode,
                    name = "",
                    colour = defaultColour(),
                    // Присмотр без напоминаний себе смысла почти не имеет, поэтому вопрос
                    // задаётся включённым — но задаётся явно (Р-29): выключить можно здесь же.
                    mirrorReminders = true,
                    isSelf = false,
                    creation = creation,
                )
            }

            is ProfileFormMode.Edit -> {
                val profile = profiles.get(mode.profileId)
                if (profile == null) {
                    // Профиль исчез, пока форма открывалась (удалён в другом месте или
                    // принесён переносом): правку открывать не над чем — уходим, а не
                    // показываем пустую форму чужого профиля.
                    _closed.tryEmit(Unit)
                    return
                }
                mutable.value = ProfileFormUiState(
                    mode = mode,
                    name = profile.name,
                    colour = profile.color,
                    mirrorReminders = profile.mirrorReminders,
                    isSelf = profile.isSelf,
                    creation = creation,
                )
            }
        }
    }

    /**
     * Цвет по умолчанию в мастере — **первый свободный** цвет набора (§5.13).
     *
     * Свободного нет — первый цвет набора, как у [ProfilePalette.of] при незнакомом
     * значении: предел пяти закрывает добавление раньше, чем кончится набор, и этот
     * исход достижим только на заведённых переносом профилях (ПЕР-19).
     */
    private suspend fun defaultColour(): Int {
        val used = profiles.observeAll().first().map { it.color }.toSet()
        return ProfilePalette.all.firstOrNull { it.argb !in used }?.argb
            ?: ProfilePalette.all.first().argb
    }

    private suspend fun updateCreation() {
        val current = mutable.value ?: return
        val creation = ProfileCreationGate.decide(profiles.count(), proStatus().included)
        mutable.value = current.copy(creation = creation)
    }

    /**
     * Завершение формы.
     *
     * Платная граница не создаёт профиль, а открывает витрину: кнопка остаётся нажимаемой,
     * и причина над ней называет, что произойдёт (П-7, §5.10). Имя и предел сюда не
     * доходят — кнопка в этих состояниях недоступна.
     */
    private fun submit() {
        val current = mutable.value ?: return
        when (current.block) {
            // Имя и предел ничего не делают: кнопка в этих состояниях недоступна, и попасть
            // сюда это не может (НФТ-29) — нажатие попросту не приходит.
            ProfileFormBlock.Name, ProfileFormBlock.Limit -> Unit
            ProfileFormBlock.Pro -> _showcase.tryEmit(Unit)
            null -> write(current)
        }
    }

    /** Запись формы: создание либо правка заведённого — с признаком «идёт запись» до неё. */
    private fun write(current: ProfileFormUiState) {
        viewModelScope.launch {
            mutable.value = current.copy(saving = true)
            val saved = when (val mode = current.mode) {
                ProfileFormMode.Add -> createProfile(
                    name = current.name,
                    color = current.colour,
                    mirrorReminders = current.mirrorReminders,
                ) is CreateProfileOutcome.Created

                is ProfileFormMode.Edit -> save(mode.profileId, current)
            }
            // Состояние не восстанавливается «по вере»: запись либо прошла, и форма уходит,
            // либо нет, и человек остаётся в ней с набранным — повторное нажатие не заводит
            // второй профиль (НФТ-29, SKL-141).
            mutable.value = mutable.value?.copy(saving = false)
            if (saved) _closed.tryEmit(Unit)
        }
    }

    /**
     * Сохранение правки: имя — у любого профиля, цвет и дублирование — у чужого (ПРФ-27).
     *
     * Порядок «имя, цвет, дублирование» не произвольный: смена флага — событие
     * перепланирования (ПРФ-6), и она идёт последней, после того как профиль уже назван
     * и покрашен. Отказ переименования (пустое имя) останавливает сохранение целиком:
     * кнопка в этом состоянии недоступна, и попасть сюда это не может.
     */
    private suspend fun save(profileId: String, state: ProfileFormUiState): Boolean {
        if (renameProfile(profileId, state.name) !is RenameProfileOutcome.Applied) return false
        if (state.showsColour) setProfileColour(profileId, state.colour)
        if (state.showsMirror) setMirrorReminders(profileId, state.mirrorReminders)
        return true
    }
}
