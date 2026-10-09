package ru.stohastika.sklyanka

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.ShouldShowOnboardingUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.history.HistoryViewModel
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveViewModel
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.onboarding.DeliveryCheckEntry
import ru.stohastika.sklyanka.feature.onboarding.OnboardingViewModel
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.onboarding.PermissionsRoute
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckNotice
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.ReminderOutageNotice
import ru.stohastika.sklyanka.feature.settings.SettingsNavigation
import ru.stohastika.sklyanka.feature.settings.SettingsRoute
import ru.stohastika.sklyanka.feature.settings.SettingsViewModel
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileViewModel
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferViewModel
import ru.stohastika.sklyanka.feature.today.TodayNavigation
import ru.stohastika.sklyanka.feature.today.TodayRoute
import ru.stohastika.sklyanka.feature.today.TodayViewModel
import ru.stohastika.sklyanka.navigation.HistoryOrigin
import ru.stohastika.sklyanka.navigation.HistorySection
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.MedsSection
import ru.stohastika.sklyanka.navigation.MedsViewModels
import ru.stohastika.sklyanka.navigation.RootLayer
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.navigation.RootTab
import ru.stohastika.sklyanka.navigation.SettingsDestination
import ru.stohastika.sklyanka.navigation.SettingsSection
import ru.stohastika.sklyanka.navigation.SettingsViewModels
import ru.stohastika.sklyanka.navigation.UndoReturn
import ru.stohastika.sklyanka.navigation.layer
import ru.stohastika.sklyanka.platform.startup.StartupRouter
import ru.stohastika.sklyanka.reminder.DeliveryCheckAnnouncer
import ru.stohastika.sklyanka.reminder.ReminderOutageAnnouncer
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupDecision
import ru.stohastika.sklyanka.startup.StartupFrames
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.motion.AnimatedLayer
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject
import android.graphics.Color as AndroidColor

/**
 * Единственная Activity приложения.
 *
 * Навигация раздела «Лекарства» собрана здесь и только здесь ([MedsSection]):
 * `feature-*` друг от друга не зависят, и решать, куда ведут переходы, обязан `app`.
 * Библиотека навигации не подключена — переходов пока пять, все внутри одной Activity.
 *
 * Тема ставится здесь и ровно один раз — в корне (TDD §5.1 задачи SKL-05). Оформление
 * профиля — тоже здесь и тоже один раз: [ProfileChrome] вокруг всего содержимого даёт
 * пять признаков чужого профиля сразу — полосу, подкрашенную шапку, надпись
 * «Профиль · Имя», несворачиваемый баннер и акцент действий — **на всех** экранах
 * в контексте профиля, а не только на «Сегодня» (ПРФ-11, ПРФ-12). Обёртка стоит в корне,
 * а не на каждом экране, потому что экранов одиннадцать, и одиннадцать мест — это
 * одиннадцать поводов забыть; экран без индикации — дыра в защите, а цена ошибки здесь
 * отметка чужого приёма как своего (ПРФ-10).
 *
 * **Заставка.** Первый кадр рисует система по теме `Theme.Sklyanka.Splash` — полотно
 * и знак появляются до того, как выполнится хоть одна строка приложения, и потому
 * не добавляют к холодному старту ничего (НФТ-21). Системный кадр платформа держит
 * до первого кадра приложения и снимает сама; приложение его не удерживает, поэтому
 * под ним уже стоит заставка приложения ([StartupFrames]). Она и держится, пока
 * первый экран не готов показать своё содержимое, — и ни кадром дольше (ОВ-87).
 * Собственной паузы у заставки нет: дольше она живёт только если дольше поднимается
 * то, что и так нужно первому экрану.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Язык интерфейса закрепляется до первого обращения к ресурсам (НФТ-43а, `ADR-0008`).
     *
     * Без этого Android применил бы к русским строкам правила множественного числа языка
     * телефона, а даты вывел бы его словами.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    @Inject
    lateinit var startupRouter: StartupRouter

    /**
     * Показывать ли экран разрешений самому (НАП-21).
     *
     * Правило ухода с заставки (SKL-13) говорит «с разрешениями непорядок» и пишет причину
     * в журнал запуска. Само по себе оно не повод открывать онбординг на каждом запуске:
     * человек, законно нажавший «Пропустить, настрою позже», иначе получал бы экран
     * разрешений всю жизнь приложения — ту самую жалобу «приложение каждый раз просит
     * разрешения», ради которой журнал запуска и заведён. Экран открывается сам, пока
     * человек его не видел; дальше вход — предупреждение на «Сегодня» и настройки.
     */
    @Inject
    lateinit var shouldShowOnboarding: ShouldShowOnboardingUseCase

    /** Единственный источник контекста активного профиля на всё приложение (ПРФ-17). */
    @Inject
    lateinit var profileContext: ProfileContextProvider

    /** Куда ведёт «Проверить доставку» из плашки «Всё настроено» (SKL-86, ОВ-108). */
    @Inject
    lateinit var deliveryCheckEntry: DeliveryCheckEntry

    /**
     * Переходы в системные настройки (SKL-78, ОВ-98).
     *
     * Строка «Звук» ведёт в системный экран уведомлений приложения: выбор звука и важности
     * принадлежит каналу, а не приложению. Тот же переход, что у онбординга (SKL-14).
     */
    @Inject
    lateinit var systemSettings: SystemSettingsNavigator

    /**
     * Сообщение о перерыве в напоминаниях (НАП-38).
     *
     * Показывается в корне, над содержимым: человек, у которого напоминания не приходили,
     * обязан узнать об этом там, куда он входит. На экран диагностики он сам не пойдёт —
     * он пойдёт туда уже после того, как потерял приёмы.
     */
    @Inject
    lateinit var outageAnnouncer: ReminderOutageAnnouncer

    /**
     * Результат самопроверки доставки (SKL-86, ОВ-108).
     *
     * Показывается тем же входом, что сообщение о перерыве, и ниже него: перерыв говорит
     * «что делать», результат проверки — «что вышло». «Проверка не состоялась» баннера
     * не даёт: человек в приложении, и исход виден блоком на «Проверке напоминаний».
     */
    @Inject
    lateinit var deliveryCheckAnnouncer: DeliveryCheckAnnouncer

    /** Местное время момента перерыва: журнал хранит моменты в UTC (НФТ-48). */
    @Inject
    lateinit var time: TimeSource

    /**
     * Живое окно отмены: по нему `onResume` решает, что человеку место на «Сегодня»
     * (SKL-43, дополнение ОВ-81).
     */
    @Inject
    lateinit var undoActions: UndoableActionStore

    /** Состояние корня приложения — маршрут и возврат из `onResume` (SKL-43). */
    private val rootNavigation = RootNavigation()

    /**
     * Экран разрешений поверх «Сегодня» (НАП-21, SKL-29).
     *
     * Поле активности, а не `remember` композиции: возврат под живым окном отмены обязан
     * закрыть этот экран из `onResume`, иначе «Сегодня» осталось бы под ним (SKL-43).
     */
    private var permissionsFromToday by mutableStateOf(false)

    private val onboarding: OnboardingViewModel by viewModels()
    private val today: TodayViewModel by viewModels()
    private val medsList: MedicationListViewModel by viewModels()
    private val medsForm: MedicationFormViewModel by viewModels()
    private val medCard: MedicationCardViewModel by viewModels()
    private val archive: ArchiveViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val transfer: DataTransferViewModel by viewModels()
    private val deleteProfile: DeleteProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Приложение светлое независимо от темы телефона (ОВ-9), поэтому значки
        // системных панелей задаются явно тёмными: умолчание выбрало бы их по
        // системной теме и дало бы светлые значки на светлом экране (SKL-72).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )

        // Ярлык быстрого доступа «Добавить лекарство» (SKL-80, ОВ-96): явное действие
        // человека важнее самопоказа онбординга, поэтому форма кладётся в стек раздела
        // до первой композиции. Живое окно удаления (SKL-99, ОВ-124) сильнее ярлыка —
        // человек остаётся на экране с окном.
        val addMedicationRequested = rootNavigation.openFromLaunch(intent?.action, undoActions.live())

        var decision by mutableStateOf<StartupDecision?>(null)
        var onboardingUnseen by mutableStateOf(false)
        // Системный кадр приложение не удерживает: на Android 12+ платформа держит его
        // до первого кадра приложения и снимает сама — это и есть передача кадра.
        // `setKeepOnScreenCondition` только отложил бы снятие, а завязанный на отрисовку
        // сигнал «кадр нарисован» заблокировал бы её насмерть (`TDD.md` §3.1).
        lifecycleScope.launch {
            // Оба признака читаются в одном заходе: решение о первом экране принимается
            // один раз, и второе чтение дало бы другой ответ — первый запуск перестаёт
            // быть первым сразу после отметки.
            onboardingUnseen = shouldShowOnboarding()
            decision = startupRouter.resolve()
        }

        setContent {
            SklyankaTheme {
                // Онбординг закрывается сам, а не сменой признака запуска: его исход
                // решает человек кнопкой, и решение живёт в состоянии экрана, а не в том,
                // что система ответила про разрешения (НАП-21).
                var onboardingDone by remember { mutableStateOf(false) }
                val todayState by today.state.collectAsState()
                // Кадр заставки — экран удержания: он виден от снятия системного кадра
                // до готовности первого экрана и ни кадром дольше. Пока заставка на
                // экране, первый экран не компонуется вовсе, поэтому пустой кадр
                // «Сегодня» (TodayUiState.Opening) мелькнуть не может (ОВ-87).
                StartupFrames(
                    frame = startupFrame(
                        destination = decision?.destination,
                        onboardingUnseen = onboardingUnseen,
                        onboardingDone = onboardingDone,
                        today = todayState,
                    ),
                ) {
                    LaunchRouting(
                        decision = decision,
                        onboardingUnseen = onboardingUnseen,
                        onboardingDone = onboardingDone,
                        addMedicationRequested = addMedicationRequested,
                        onOnboardingDone = { onboardingDone = true },
                    )
                }
            }
        }
    }

    /**
     * Первый экран запуска: разрешения или корень приложения.
     *
     * Вынесено из `onCreate` отдельной функцией, потому что решений здесь два и каждое
     * со своей причиной; `onCreate` остаётся сборкой состояния. Согласия среди них нет:
     * UX скрыт до подключения провайдера (ПМ-31), первый запуск идёт в онбординг
     * разрешений.
     */
    @Composable
    @Suppress("LongParameterList")
    private fun LaunchRouting(
        decision: StartupDecision?,
        onboardingUnseen: Boolean,
        onboardingDone: Boolean,
        addMedicationRequested: Boolean,
        onOnboardingDone: () -> Unit,
    ) {
        when {
            // Экран разрешений EMUI (SKL-14): запуск, на котором с разрешениями
            // непорядок, начинается с него — но только пока человек этого экрана
            // не видел. Решение и его причина уже записаны в журнал запуска.
            // Ярлык «Добавить лекарство» перебивает самопоказ (SKL-80): человек
            // уже сказал, зачем пришёл; разрешения остаются баннером и настройками.
            decision?.destination == StartDestination.Onboarding &&
                onboardingUnseen &&
                !onboardingDone &&
                !addMedicationRequested ->
                PermissionsRoute(
                    viewModel = onboarding,
                    source = OnboardingSource.FIRST_LAUNCH,
                    onExit = onOnboardingDone,
                    onCheckDelivery = { startActivity(deliveryCheckEntry.intentFor(this)) },
                )

            // «Настроить» в предупреждении на «Сегодня» — тот же экран разрешений,
            // что при первом запуске, но с источником TODAY_WARNING: индикатора
            // шагов на нём нет (НАП-21, критерий приёмки 4 SKL-29). Человек
            // возвращается на «Сегодня» — оттуда он и пришёл.
            permissionsFromToday -> PermissionsRoute(
                viewModel = onboarding,
                source = OnboardingSource.TODAY_WARNING,
                onExit = { permissionsFromToday = false },
                onCheckDelivery = { startActivity(deliveryCheckEntry.intentFor(this)) },
            )

            // Оформление активного профиля — вокруг всего, что человек видит
            // в контексте профиля (ПРФ-12). Заставка и экран разрешений в него
            // не входят: профиля на них ещё нет, а на первом запуске он всегда
            // свой.
            else -> ProfileChrome(profileChrome()) {
                OutageNotice()
                DeliveryCheckBanner()
                Root(onOpenPermissions = { permissionsFromToday = true })
            }
        }
    }

    /**
     * Повторный приход ярлыка быстрого доступа (SKL-80).
     *
     * `singleTask` доводит ярлык до уже существующей задачи, поэтому запуск поверх
     * открытого приложения приходит сюда, а не в `onCreate`. `openFromLaunch` кладёт
     * в раздел то же значение: повторный приход идемпотентен и стек не растёт.
     * `setIntent` сохраняет намерение, чтобы повторное чтение действия было верным.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Окно удаления сильнее ярлыка и здесь: форма не ложится поверх окна (SKL-99).
        rootNavigation.openFromLaunch(intent.action, undoActions.live())
    }

    /**
     * Оформление по активному профилю.
     *
     * До первого значения потока — оформление владельца: индикации у него нет вовсе
     * (ПРФ-16), поэтому первый кадр не мигает полосой, которую тут же убрали бы. Обратная
     * подстановка — «считать чужим, пока не known» — дала бы индикацию профиля, которого
     * нет, то есть ложь ровно в том месте, где человек ей поверит.
     */
    @Composable
    private fun profileChrome(): ProfileChromeState {
        val context by profileContext.current.collectAsState(initial = null)
        val profile = context ?: return ProfileChromeState.Owner
        return ProfileChromeState.of(name = profile.name, colorArgb = profile.color, isSelf = profile.isSelf)
    }

    /**
     * Сообщение о перерыве в напоминаниях над содержимым (НАП-38).
     *
     * Признак перечитывается при каждом возвращении на передний план ([onResume]), а не
     * запоминается флагом: перерыв кончается сам, когда напоминания снова пошли.
     */
    @Composable
    private fun OutageNotice() {
        val outage by outageAnnouncer.current.collectAsState()
        outage?.let { detected ->
            ReminderOutageNotice(
                outage = detected,
                onFix = {
                    // Сообщение доставлено: дальше о перерыве говорит экран настройки, и второй
                    // раз тот же перерыв объявлять незачем.
                    outageAnnouncer.acknowledge()
                    // Один шаг до нужного места (ОВ-107): экран открывается на первом
                    // незакрытом шаге по тому же правилу, что и «Настроить» на «Сегодня»,
                    // и называет перерыв в плашке.
                    startActivity(
                        PermissionsActivity.intentFor(
                            context = this,
                            source = OnboardingSource.OUTAGE_BANNER,
                            outageSince = detected.since,
                        ),
                    )
                },
                toLocal = { instant -> instant.atZone(time.zone()).toLocalDateTime() },
            )
        }
    }

    /**
     * Результат самопроверки доставки над содержимым (SKL-86, ОВ-108).
     *
     * Признак перечитывается при каждом возвращении на передний план ([onResume]):
     * проверка могла завершиться, пока приложение было в фоне. Результат объявляется
     * один раз — оба действия сначала подтверждают его, а «Исправить» ведёт тем же
     * маршрутом, что «Настроить» на «Сегодня».
     */
    @Composable
    private fun DeliveryCheckBanner() {
        val result by deliveryCheckAnnouncer.current.collectAsState()
        result?.let { finished ->
            DeliveryCheckNotice(
                state = finished,
                onAcknowledge = { lifecycleScope.launch { deliveryCheckAnnouncer.acknowledge() } },
                onFix = {
                    lifecycleScope.launch { deliveryCheckAnnouncer.acknowledge() }
                    permissionsFromToday = true
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Перерыв в напоминаниях мог начаться, пока приложение было в фоне, и мог
        // закончиться сам: признак считается заново, а не хранится флагом (НАП-38).
        lifecycleScope.launch { outageAnnouncer.refresh() }
        // Результат самопроверки доставки читается тем же входом: проверка завершается
        // при закрытом приложении, и показать исход раньше некому (SKL-86, НФТ-52).
        lifecycleScope.launch { deliveryCheckAnnouncer.refresh() }
        // Состояние разрешений читается из системы при каждом возврате на передний план
        // (НФТ-52): человек уходит выдавать разрешение в системные настройки и возвращается,
        // и карточка обязана измениться без перезахода (критерии приёмки 4 и 16).
        onboarding.refresh()
        // То же и для настроек: состояние разрешений и связи с часами читается из системы
        // при каждом возвращении на передний план, а не запоминается флагом (НФТ-52,
        // критерий приёмки 6).
        settings.refresh()
        // «Сегодня» перечитывает разрешения по возврату в приложение (SKL-48): выдача
        // или отзыв разрешения в системных настройках обязаны отразиться без перезапуска
        // (НФТ-52). Минутный такт делает то же, пока экран открыт.
        today.refresh()
    }

    /**
     * Возврат под живым окном отмены (SKL-43, ОВ-81).
     *
     * Момент — появление окна приложения перед человеком, а не `onResume`: за экраном
     * блокировки активность уже возобновлена, и по `onResume` «Склянка» переключалась бы
     * на «Сегодня», пока телефон ещё заперт, — то есть и тогда, когда к разблокировке окно
     * успело истечь (проверено на AVD 30.09.2026). Фокус окна за блокировкой принадлежит
     * ей, а не приложению, поэтому фокус и есть «человек оказался в приложении».
     *
     * Живое окно — по магазину; истёкшее не переключает ничего. Экран разрешений поверх
     * корня закрывается — иначе «Сегодня» осталось бы под ним.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (UndoReturn.onAppeared(undoActions, rootNavigation)) permissionsFromToday = false
    }

    /**
     * Корень приложения: три вкладки нижней панели и экраны второго уровня.
     *
     * Граф собран здесь и только здесь: `feature-*` друг от друга не зависят, и решать,
     * куда ведут переходы, обязан `app` (`ADR-0007`). Библиотека навигации не подключена —
     * состояний девять, все внутри одной Activity.
     *
     * Раздел «Лекарства» и экраны второго уровня открываются **поверх** вкладки и возвращают
     * туда, откуда открыты: место входа хранится ([RootTab], [HistoryOrigin]), а не
     * угадывается по тому, какой экран был отрисован последним. Иначе человек, ушедший
     * в лекарства из настроек, возвращался бы на «Сегодня».
     *
     * Само состояние — в [RootNavigation]: оно доступно и `onResume`, который под живым
     * окном отмены возвращает человека на «Сегодня» (SKL-43, ОВ-81).
     *
     * Диагностика напоминаний и экран разрешений — отдельные Activity (SKL-13, SKL-14),
     * и в этот граф они не встраиваются: у них есть собственные точки входа — строка
     * настроек и уведомление. Ярлык быстрого доступа приходит сюда, в форму нового
     * лекарства через раздел «Лекарства» (SKL-80).
     */
    @Composable
    private fun Root(onOpenPermissions: () -> Unit) {
        AnimatedLayer(
            layer = rootNavigation.layer(),
            motion = rootNavigation.rootMotion,
            modifier = Modifier.fillMaxSize(),
        ) { layer ->
            // Корень вкладки: возвращаться некуда, но выходить из приложения нажатием,
            // которое человек ждёт как возврат, нельзя — «Назад» ведёт на «Сегодня»
            // (`docs/v2/10-behavior.md` §5.1). На «Сегодня» обработчика нет: там
            // приложение завершается штатно. Вложенные экраны ставят свой обработчик выше
            // и перехватывают нажатие раньше — этот срабатывает только на корне вкладки.
            BackHandler(enabled = layer == RootLayer.Settings) { rootNavigation.openToday() }
            when (layer) {
                // История, открытая с «Сегодня» (ОВ-84, SKL-31): тот же второй уровень, что
                // из настроек, но место входа — строка прогресса дня. Ветка стоит рядом
                // с `section`/`meds`: возврат обязан вести туда, откуда открыли.
                RootLayer.HistoryFromToday -> HistorySection(
                    viewModel = history,
                    origin = HistoryOrigin.TODAY,
                    onLeave = { rootNavigation.leaveHistory() },
                )

                is RootLayer.SettingsScreen -> SettingsSection(
                    destination = layer.destination,
                    viewModels = SettingsViewModels(history, transfer, deleteProfile),
                    onLeave = rootNavigation::leaveSection,
                    onOpenPermissions = { startActivity(PermissionsActivity.intentFor(this)) },
                )

                is RootLayer.Meds -> MedsSection(
                    viewModels = MedsViewModels(
                        list = medsList,
                        form = medsForm,
                        card = medCard,
                        archive = archive,
                    ),
                    onLeaveSection = rootNavigation::leaveMeds,
                    // Панель на корне «Лекарств» (ОВ-91) ведёт на «Сегодня» и в «Настройки».
                    // Для настроек сначала закрывается раздел: ветка `Root` проверяет раздел
                    // раньше вкладки, и незакрытый раздел оставил бы человека в лекарствах.
                    onOpenToday = { rootNavigation.openToday() },
                    onOpenSettings = {
                        rootNavigation.leaveMeds()
                        rootNavigation.openTab(RootTab.SETTINGS)
                    },
                    initial = layer.entry,
                )

                RootLayer.Settings -> SettingsRoute(
                    viewModel = settings,
                    navigation = settingsNavigation(),
                )

                RootLayer.Today -> TodayRoute(
                    viewModel = today,
                    navigation = todayNavigation(onOpenPermissions),
                )
            }
        }
    }

    private fun settingsNavigation(): SettingsNavigation = SettingsNavigation(
        // Строки «Добавить профиль» на экране нет: мастера создания профиля не существует,
        // а «будет доступно в ближайшем обновлении» — обещание функции, которой нет (П-7,
        // Р-29). Намерение и переход остаются швом пост-MVP: мастер вернётся без правки
        // навигации (SKL-121).
        addProfile = {},
        // «Звук» — системный экран уведомлений приложения (ОВ-98): там оба
        // канала и выбор звука с важностью для каждого.
        openSound = { systemSettings.openNotificationSettings() },
        // Решение по `ОВ-48`: сначала диагностика — что именно не так, —
        // и только потом онбординг. Обратный порядок отправлял бы человека
        // проходить цепочку заново, не сказав ему, сломано ли что-нибудь.
        openDiagnostics = { startActivity(DiagnosticsActivity.intentFor(this)) },
        openHistory = { rootNavigation.openSection(SettingsDestination.HISTORY) },
        openDataTransfer = { rootNavigation.openSection(SettingsDestination.TRANSFER) },
        // «Удалить профиль «Имя»» — экран второго уровня настроек (ОВ-7, SKL-36).
        // Строка появляется только у активного чужого профиля, и «Готово»
        // возвращает в настройки — туда, откуда человек пришёл.
        deleteProfile = { rootNavigation.openSection(SettingsDestination.DELETE_PROFILE) },
        openToday = { rootNavigation.openTab(RootTab.TODAY) },
        openMedications = { rootNavigation.openMeds(MedsDestination.List) },
    )

    private fun todayNavigation(onOpenPermissions: () -> Unit): TodayNavigation = TodayNavigation(
        openMedicationCard = { id -> rootNavigation.openMeds(MedsDestination.Card(id)) },
        // «Купить» ведёт в ту же карточку: блок остатка с кнопкой «Пополнить»
        // живёт на ней (SKL-12).
        openMedicationStock = { id -> rootNavigation.openMeds(MedsDestination.Card(id)) },
        addMedication = { rootNavigation.openMeds(MedsDestination.NewMedication) },
        openMedicationList = { rootNavigation.openMeds(MedsDestination.List) },
        // Вкладка «Настройки» нижней панели и аватар профиля в шапке ведут
        // в одно место — шестерёнки в шапке нет (ПРФ-19, критерий приёмки 14).
        openSettings = { rootNavigation.openTab(RootTab.SETTINGS) },
        // «Настроить» в предупреждении о ненадёжности: экран разрешений
        // с источником TODAY_WARNING (SKL-29, НАП-21).
        openPermissions = onOpenPermissions,
        // «История» в строке прогресса дня — история с местом входа TODAY
        // (ОВ-84, SKL-31).
        openHistory = rootNavigation::openHistory,
    )
}
