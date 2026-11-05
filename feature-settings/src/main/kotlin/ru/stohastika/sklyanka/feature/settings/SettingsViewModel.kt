package ru.stohastika.sklyanka.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.profile.SwitchProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.SnoozeIntervals
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import javax.inject.Inject

/**
 * Состояние экрана «Настройки».
 *
 * Два разных вида состояния собираются здесь по-разному, и это главное свойство экрана.
 *
 * **Своё** — профили, активный профиль, значения настроек напоминания — приходит потоками
 * и обновляется само: переключил профиль, выключил настойчивое напоминание, и строка
 * изменилась без перезахода на экран.
 *
 * **Чужое** — состояние разрешений — читается из системы по [refresh],
 * то есть при каждом показе экрана и при каждом возвращении в приложение (НФТ-52). Потока
 * здесь быть не может: система об отзыве разрешения приложению не сообщает, и подписка
 * молча отдавала бы значение, снятое когда-то раньше.
 *
 * Своей логики переключения профиля здесь нет: экран зовёт [SwitchProfileUseCase] (SKL-15),
 * который и решает, что считать переключением, и оставляет след в журнале (ПРФ-30).
 */
@HiltViewModel
@Suppress("LongParameterList")
class SettingsViewModel @Inject constructor(
    profiles: ProfileRepository,
    /**
     * Активный профиль — тем же источником, каким его видит всё остальное приложение.
     *
     * Второго источника контекста быть не может: разойдясь, они дадут экран с отметкой
     * у одного профиля и данными другого (`ProfileContext`).
     */
    context: ProfileContextProvider,
    medications: MedicationRepository,
    private val switchProfile: SwitchProfileUseCase,
    /**
     * Внешние ответы, которые экран спрашивает, а не помнит (НФТ-52).
     *
     * Одним узлом ([SettingsEnvironment]): состояние системы читается заново при каждом
     * показе экрана, версия берётся из собранного пакета.
     */
    private val environment: SettingsEnvironment,
    /**
     * Оформление: действующие тема и вариант иконки, их чтение и запись (§5.17).
     *
     * Одним узлом ([SettingsAppearance]), а не четырьмя параметрами: четыре шва идут вместе
     * и по одной причине — тема и иконка это одна возможность с общим хранилищем выбора.
     * Экран только называет намерение, а запись делает домен.
     */
    private val appearance: SettingsAppearance,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(version = environment.stamp.versionName))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observeProfiles(profiles, context, medications)
        observeAppearance()
        refresh()
    }

    /** Тема и вариант иконки: потоком, потому что их меняет и диалог, и другая сборка. */
    private fun observeAppearance() {
        viewModelScope.launch {
            appearance.observeTheme().collect { theme -> _state.value = _state.value.copy(accentTheme = theme) }
        }
        viewModelScope.launch {
            appearance.observeIcon().collect { variant -> _state.value = _state.value.copy(iconVariant = variant) }
        }
    }

    /**
     * Перечитать то, что принадлежит системе (НФТ-52).
     *
     * Зовётся при появлении экрана и при возвращении приложения на передний план. Значения
     * настроек напоминания перечитываются тем же заходом: порт отдаёт их разовым чтением,
     * а не потоком.
     */
    fun refresh() {
        viewModelScope.launch {
            val snapshot = environment.system.read()
            _state.value = decision(
                _state.value.copy(
                    reminders = snapshot.reminders,
                    permissions = snapshot.permissions,
                ),
            )
        }
    }

    /**
     * Исход гейта на следующее создание профиля (ПРФ-32, ПРФ-34).
     *
     * Считается по **числу заведённых** профилей: пятый закрыт всегда, и это предел
     * продукта, а не платная граница. Решение принимает домен ([ProfileCreationGate]) —
     * экран лишь показывает его словами; пересчитывается оно при смене списка.
     */
    private fun decision(state: SettingsUiState): SettingsUiState = state.copy(
        profileCreation = ProfileCreationGate.decide(state.profiles.size),
    )

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectProfile -> switchTo(intent.profileId)
            is SettingsIntent.SetPersistentReminder -> setPersistent(intent.enabled)
            is SettingsIntent.OpenSnooze -> _state.value = _state.value.copy(snoozePickerVisible = true)
            is SettingsIntent.DismissSnooze -> _state.value = _state.value.copy(snoozePickerVisible = false)
            is SettingsIntent.SetSnooze -> setSnooze(intent.minutes)
            // Строки блока «Оформление» (SKL-120): диалог открывается всегда, а выбор
            // применяется сразу и закрывает его (§5.17).
            SettingsIntent.OpenThemePicker -> openAppearancePicker(AppearancePicker.THEME)
            SettingsIntent.OpenIconPicker -> openAppearancePicker(AppearancePicker.ICON)
            SettingsIntent.DismissThemePicker -> _state.value = _state.value.copy(themePickerVisible = false)
            SettingsIntent.DismissIconPicker -> _state.value = _state.value.copy(iconPickerVisible = false)
            is SettingsIntent.SelectTheme -> chooseTheme(intent.theme)
            is SettingsIntent.SelectIconVariant -> chooseIconVariant(intent.variant)
            // Остальные намерения — переходы; их исполняет тот, кто собрал маршруты.
            else -> Unit
        }
    }

    /**
     * Нажатие строки блока «Оформление» (§5.17).
     *
     * Диалог открывается всегда: человек видит четыре образца темы и обе миниатюры иконки,
     * а выбор применяется на самом нажатии варианта.
     */
    private fun openAppearancePicker(picker: AppearancePicker) {
        _state.value = when (picker) {
            AppearancePicker.THEME -> _state.value.copy(themePickerVisible = true)
            AppearancePicker.ICON -> _state.value.copy(iconPickerVisible = true)
        }
    }

    /** Выбор темы: применяется сразу и закрывает диалог (§5.17). */
    private fun chooseTheme(theme: AccentTheme) {
        viewModelScope.launch {
            appearance.setTheme(theme)
            _state.value = _state.value.copy(themePickerVisible = false)
        }
    }

    /** Выбор варианта иконки: тем же правилом, что и тема (§5.17, §2.4). */
    private fun chooseIconVariant(variant: IconVariant) {
        viewModelScope.launch {
            appearance.setIcon(variant)
            _state.value = _state.value.copy(iconPickerVisible = false)
        }
    }

    /**
     * Список профилей со числом лекарств и отметкой активного.
     *
     * Число лекарств читается тем же запросом, что и список лекарств профиля: второй
     * источник «сколько их» разошёлся бы с первым, и строка настроек начала бы обещать
     * лекарства, которых на экране нет.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProfiles(
        profiles: ProfileRepository,
        context: ProfileContextProvider,
        medications: MedicationRepository,
    ) {
        viewModelScope.launch {
            combine(profiles.observeAll(), context.current) { all, active -> all to active.id }
                .flatMapLatest { (all, activeId) -> rows(all, activeId, medications) }
                .collect { rows ->
                    _state.value = decision(
                        _state.value.copy(
                            profiles = rows,
                            // Удалить можно ровно тот профиль, в контексте которого человек
                            // находится: чужой и активный (ОВ-7). Свой сюда не попадает (ПРФ-28).
                            deletableProfile = rows.firstOrNull { it.isActive && !it.isSelf },
                        ),
                    )
                }
        }
    }

    private fun rows(all: List<Profile>, activeId: String, medications: MedicationRepository) = if (all.isEmpty()) {
        flowOf(emptyList())
    } else {
        // Свой профиль первым, дальше — по имени: порядок списка не должен зависеть
        // от того, в каком порядке база вернула строки (ПРФ-26).
        val ordered = all.sortedWith(compareByDescending<Profile> { it.isSelf }.thenBy { it.name })
        combine(ordered.map { profile -> medications.observeActive(profile.id) }) { counts ->
            ordered.mapIndexed { index, profile ->
                ProfileRowState(
                    id = profile.id,
                    name = profile.name,
                    colorArgb = profile.color,
                    isSelf = profile.isSelf,
                    isActive = profile.id == activeId,
                    medicationCount = counts[index].size,
                    // У владельца признак дублирования не имеет смысла (INV-2)
                    // и в строке не показывается (ПРФ-16).
                    mirrorReminders = !profile.isSelf && profile.mirrorReminders,
                )
            }
        }
    }

    private fun switchTo(profileId: String) {
        viewModelScope.launch {
            // Причина уходит в журнал: «почему я оказался в мамином профиле» — вопрос,
            // ответ на который обязан быть восстановим (ПРФ-30).
            switchProfile(profileId, SwitchReason.USER_SETTINGS)
        }
    }

    /**
     * Настойчивое напоминание (SKL-13).
     *
     * Перепланирования не вызывает намеренно: настройка меняет поведение уже сработавшего
     * напоминания, а не состав и время будильников, и НАП-11 к ней не относится. Значение
     * перечитывается из хранилища, а не выставляется в состоянии «по вере»: запись могла
     * не состояться, и тогда строка обязана показать то, что действительно сохранено.
     */
    private fun setPersistent(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(reminders = environment.system.setPersistentEnabled(enabled))
        }
    }

    /**
     * Интервал откладывания (SKL-78, ОВ-98).
     *
     * Перепланирования не вызывает: интервал откладывания не меняет состав и время
     * будущих будильников, как и настойчивое напоминание. Значение вне набора отклоняется
     * до записи — набор задаёт диалог, и чужое число попало бы сюда только ошибкой.
     */
    private fun setSnooze(minutes: Int) {
        if (!SnoozeIntervals.isAllowed(minutes)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                reminders = environment.system.setSnoozeMinutes(minutes),
                snoozePickerVisible = false,
            )
        }
    }
}

/**
 * Какой из двух диалогов блока «Оформление» просит открыть нажатие (§5.17).
 *
 * Тип нужен потому, что правило гейта у обеих строк одно, а диалоги разные: писать его
 * дважды значило бы однажды разойтись в том, что открывается без доступа.
 */
private enum class AppearancePicker { THEME, ICON }
