package ru.stohastika.sklyanka.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.ConsentShown
import ru.stohastika.sklyanka.domain.analytics.ConsentShownSource
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ObserveAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearanceOutcome
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.pro.ProPoint
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
     * Внешние ответы, которые экран спрашивает, а не помнит (НФТ-52, SKL-122).
     *
     * Одним узлом ([SettingsEnvironment]): состояние системы читается заново при каждом
     * показе экрана, версия берётся из собранного пакета, подписка — из кэша магазина.
     */
    private val environment: SettingsEnvironment,
    /**
     * Оформление: действующие тема и вариант иконки, их чтение и запись (§5.17, Р-31).
     *
     * Одним узлом ([SettingsAppearance]), а не четырьмя параметрами: четыре шва идут вместе
     * и по одной причине — тема и иконка это одна возможность «Про» с общим гейтом
     * и общим хранилищем выбора. Границы «Про» здесь нет: она живёт в домене, и экран
     * только называет намерение.
     */
    private val appearance: SettingsAppearance,
    /**
     * Согласие на аналитику: поток текущего решения и единственная точка его изменения
     * (ПМ-18, ПМ-25, TDD §7.4).
     *
     * Решение живёт в домене и в хранилище, а не в состоянии экрана: строку обязаны видеть
     * и первый запуск, и повторный вход, и снятое согласие не должно «отрастать» при
     * перезапуске (АН-2). Экран только называет намерение.
     */
    private val observeAnalyticsConsent: ObserveAnalyticsConsentUseCase,
    private val decideAnalyticsConsent: DecideAnalyticsConsentUseCase,
    /**
     * Запись показа вопроса (Р-96).
     *
     * Открытие поверхности из настроек — это событие показа, и оно пишется до согласия:
     * накопленный показ уходит вместе с выбором, а при отказе стирается вместе с очередью.
     */
    private val analytics: AnalyticsRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(version = environment.stamp.versionName))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * Просьба открыть витрину: нажатие **платной** строки диалога без доступа ведёт
     * в покупку, а не меняет выбор (§5.17, Р-67 пп. 2–3).
     *
     * Сигналом, а не полем состояния: шторку хостит `app` (поверхность покупки одна на все
     * точки входа, Р-67), и настройки о ней не знают. Точку-повод называет экран —
     * `ProPoint.ACCENT`, — и её же называет поход в реестре `SKL-140/TDD.md` §7.
     */
    private val _showcase = MutableSharedFlow<ProPoint>(extraBufferCapacity = 1)
    val showcase: SharedFlow<ProPoint> = _showcase.asSharedFlow()

    init {
        observeProfiles(profiles, context, medications)
        observeAppearance()
        observeAnalytics()
        refresh()
    }

    /**
     * Решение об аналитике — потоком: его меняет и этот экран, и первый запуск.
     *
     * Поле состояния повторяет хранилище, а не нажатие: вопрос, закрытый без решения,
     * оставляет строку прежней, и это видно без перезахода (ПМ-25, АН-2).
     */
    private fun observeAnalytics() {
        viewModelScope.launch {
            observeAnalyticsConsent().collect { consent -> _state.value = _state.value.copy(analytics = consent) }
        }
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
                    // Состояние подписки — тем же заходом и по той же причине: магазин мог
                    // ответить, пока человек был в нём (покупка, восстановление, отмена).
                    pro = environment.proStatus(),
                ),
            )
        }
    }

    /**
     * Исход гейта на следующее создание профиля (ПРФ-33, Р-84).
     *
     * Считается по **числу заведённых** профилей и доступу из кэша, а не по подписке:
     * второй профиль бесплатен всегда, третий без доступа закрыт, пятый закрыт при любой
     * подписке. Решение принимает домен ([ProfileCreationGate]) — экран лишь показывает
     * его словами и меткой; пересчитывается оно и при смене списка, и при перечитывании
     * доступа после витрины.
     */
    private fun decision(state: SettingsUiState): SettingsUiState = state.copy(
        profileCreation = ProfileCreationGate.decide(state.profiles.size, state.pro.included),
    )

    fun onIntent(intent: SettingsIntent) {
        // Согласие на аналитику — отдельный маленький проход: три намерения вопроса не
        // раздувают ветвление экрана и живут рядом с решением, а не среди переходов.
        if (handleAnalytics(intent)) return
        when (intent) {
            is SettingsIntent.SelectProfile -> switchTo(intent.profileId)
            is SettingsIntent.SetPersistentReminder -> setPersistent(intent.enabled)
            is SettingsIntent.OpenSnooze -> _state.value = _state.value.copy(snoozePickerVisible = true)
            is SettingsIntent.DismissSnooze -> _state.value = _state.value.copy(snoozePickerVisible = false)
            is SettingsIntent.SetSnooze -> setSnooze(intent.minutes)
            // Строки блока «Оформление» (SKL-120): диалог открывается всегда, а гейт стоит
            // на самом выборе — платный без доступа уводит в витрину, базовый применяется
            // всегда (§5.17).
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
     * Намерения вопроса об аналитике (ПМ-25, Р-96).
     *
     * Включение открывает вопрос, выключение и отказ в вопросе исполняются сразу —
     * диалога у выключения нет. Возвращает «намерение моё», чтобы экранный `when`
     * не обрастал тремя ветками, не относящимися к переходам.
     */
    private fun handleAnalytics(intent: SettingsIntent): Boolean = when (intent) {
        is SettingsIntent.SetAnalyticsEnabled -> {
            setAnalytics(intent.enabled)
            true
        }

        SettingsIntent.DismissAnalyticsPrompt -> {
            _state.value = _state.value.copy(analyticsPromptVisible = false)
            true
        }

        SettingsIntent.ConfirmAnalytics -> {
            confirmAnalytics()
            true
        }

        else -> false
    }

    /**
     * Нажатие строки блока «Оформление» (§5.17, Р-67).
     *
     * Диалог открывается **всегда**, и доступа для этого не спрашивают: человек видит
     * четыре образца темы и обе миниатюры иконки до покупки (Р-67 п. 5), а «Про» стоит
     * у платных строк, а не у поверхности (Р-71). Гейт живёт на **выборе**: без доступа
     * платный вариант не применяется и открывает витрину, а «Олива» и «Ледяная» выбираются
     * бесплатно — в том числе чтобы откатиться с оплаченного (Р-22, В-120-3).
     *
     * Скрытого автопродолжения нет: оформив доступ, человек нажимает строку ещё раз —
     * та же модель, что у состава отчёта (§5.15).
     */
    private fun openAppearancePicker(picker: AppearancePicker) {
        _state.value = when (picker) {
            AppearancePicker.THEME -> _state.value.copy(themePickerVisible = true)
            AppearancePicker.ICON -> _state.value.copy(iconPickerVisible = true)
        }
    }

    /**
     * Выбор темы: применяется сразу и закрывает диалог (§5.17).
     *
     * Исход решает домен, а не экран: без доступа платная тема не применяется, диалог
     * закрывается, и открывается витрина — доступ мог кончиться, пока диалог был открыт.
     */
    private fun chooseTheme(theme: AccentTheme) {
        viewModelScope.launch {
            when (appearance.setTheme(theme)) {
                AppearanceOutcome.APPLIED -> _state.value = _state.value.copy(themePickerVisible = false)
                AppearanceOutcome.SHOWCASE -> {
                    _state.value = _state.value.copy(themePickerVisible = false)
                    _showcase.tryEmit(ProPoint.ACCENT)
                }
            }
        }
    }

    /** Выбор варианта иконки: тем же правилом, что и тема (§5.17, §2.4). */
    private fun chooseIconVariant(variant: IconVariant) {
        viewModelScope.launch {
            when (appearance.setIcon(variant)) {
                AppearanceOutcome.APPLIED -> _state.value = _state.value.copy(iconPickerVisible = false)
                AppearanceOutcome.SHOWCASE -> {
                    _state.value = _state.value.copy(iconPickerVisible = false)
                    _showcase.tryEmit(ProPoint.ACCENT)
                }
            }
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
     * Переключатель строки «Аналитика» (ПМ-25, Р-96).
     *
     * Включение **не даёт согласия**: оно открывает вопрос и пишет показ — человек ещё
     * ничего не решил, и согласием считается только «Разрешить». Показ пишется здесь,
     * а не при подтверждении: вопрос уже задан, и событие принадлежит нажатию (Р-96).
     *
     * Выключение — мгновенное и без диалога: отказ не спрашивает подтверждения, а очередь
     * очищается в единственном месте, где решение превращается в записи (АН-2, TDD §7.4).
     */
    private fun setAnalytics(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch { decideAnalyticsConsent(AnalyticsConsentState.DECLINED) }
            return
        }
        analytics.recordConsent(ConsentShown(ConsentShownSource.SETTINGS))
        _state.value = _state.value.copy(analyticsPromptVisible = true)
    }

    /**
     * «Разрешить» в вопросе: согласие даётся один раз и одним сценарием домена.
     *
     * Вопрос закрывается сразу, а состояние строки приходит потоком решения: `ALLOWED`
     * в состоянии, поставленный «по нажатию», мог бы разойтись с хранилищем, если запись
     * не состоялась.
     */
    private fun confirmAnalytics() {
        _state.value = _state.value.copy(analyticsPromptVisible = false)
        viewModelScope.launch {
            decideAnalyticsConsent(AnalyticsConsentState.ALLOWED, AnalyticsConsentSource.SETTINGS)
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
