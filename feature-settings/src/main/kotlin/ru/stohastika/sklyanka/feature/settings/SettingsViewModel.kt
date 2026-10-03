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
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.Experiments
import ru.stohastika.sklyanka.domain.experiment.NoOpExperiments
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.profile.SwitchProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.SnoozeIntervals
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.transfer.AppStamp
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
 * **Чужое** — состояние разрешений и связи с часами — читается из системы по [refresh],
 * то есть при каждом показе экрана и при каждом возвращении в приложение (НФТ-52). Потока
 * здесь быть не может: система об отзыве разрешения приложению не сообщает, и подписка
 * молча отдавала бы значение, снятое когда-то раньше.
 *
 * Своей логики переключения профиля здесь нет: экран зовёт [SwitchProfileUseCase] (SKL-15),
 * который и решает, что считать переключением, и оставляет след в журнале (ПРФ-30).
 */
@HiltViewModel
// Каждая из восьми зависимостей — отдельный контракт экрана: профили, медикаменты,
// переключение, состояние системы, версия, согласие, A/B (SKL-83) и его поток.
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
    /** Всё, что читается из системы заново при каждом показе экрана (НФТ-52). */
    private val system: SettingsSystemStateReader,
    /**
     * Версия приложения — из собранного пакета, а не из макета.
     *
     * Тот же снимок, что уходит в файл переноса: двух источников версии в продукте быть
     * не должно, иначе экран и файл однажды назовут разные числа.
     */
    stamp: AppStamp,
    /**
     * Согласие на аналитику (SKL-82): поток состояния переключателя и мгновенное
     * выключение. Нулевое умолчание — для ручной сборки без аналитики.
     */
    private val analyticsConsent: AnalyticsConsentControl = AnalyticsConsentControl.NONE,
    /**
     * A/B-канал (SKL-83): подпись строки переноса. Нулевое умолчание — база,
     * как и у аналитики.
     */
    private val experiments: Experiments = NoOpExperiments,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(version = stamp.versionName))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observeProfiles(profiles, context, medications)
        observeAnalyticsConsent()
        refresh()
    }

    /**
     * Переключатель «Аналитика» слушает решение согласия (SKL-82).
     *
     * Поток, а не разовое чтение: включение и выключение происходят на другом экране
     * или в этот же момент, и строка обязана измениться без перезахода (образец — НФТ-52).
     */
    private fun observeAnalyticsConsent() {
        viewModelScope.launch {
            analyticsConsent.observeEnabled().collect { enabled ->
                _state.value = _state.value.copy(analyticsEnabled = enabled)
            }
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
            val snapshot = system.read()
            _state.value = _state.value.copy(
                reminders = snapshot.reminders,
                watch = snapshot.watch,
                permissions = snapshot.permissions,
                transferHint = experiments.variantOf(ExperimentId.SETTINGS_TRANSFER_HINT),
            )
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectProfile -> switchTo(intent.profileId)
            is SettingsIntent.SetPersistentReminder -> setPersistent(intent.enabled)
            is SettingsIntent.OpenSnooze -> _state.value = _state.value.copy(snoozePickerVisible = true)
            is SettingsIntent.DismissSnooze -> _state.value = _state.value.copy(snoozePickerVisible = false)
            is SettingsIntent.SetSnooze -> setSnooze(intent.minutes)
            // Включение сюда не доходит: его перехватывает маршрут и открывает экран
            // согласия (ПМ-25 п. 4). Выключение — мгновенное, без экрана.
            is SettingsIntent.SetAnalytics -> if (!intent.enabled) setAnalyticsEnabled(false)
            // Остальные намерения — переходы; их исполняет тот, кто собрал маршруты.
            else -> Unit
        }
    }

    /** Выключение аналитики: запись решения и немедленное прекращение отправки (ПМ-18). */
    private fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { analyticsConsent.setEnabled(enabled) }
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
                    _state.value = _state.value.copy(
                        profiles = rows,
                        // Удалить можно ровно тот профиль, в контексте которого человек
                        // находится: чужой и активный (ОВ-7). Свой сюда не попадает (ПРФ-28).
                        deletableProfile = rows.firstOrNull { it.isActive && !it.isSelf },
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
            _state.value = _state.value.copy(reminders = system.setPersistentEnabled(enabled))
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
                reminders = system.setSnoozeMinutes(minutes),
                snoozePickerVisible = false,
            )
        }
    }
}
