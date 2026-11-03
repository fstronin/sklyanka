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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.analytics.AnalyticsLaunchAnchor
import ru.stohastika.sklyanka.data.room.DatabaseUpgrade
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.ObserveAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ScreenView
import ru.stohastika.sklyanka.domain.analytics.ShouldShowAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.ObserveIconVariantUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.ShouldShowOnboardingUseCase
import ru.stohastika.sklyanka.domain.privacy.AcknowledgePrivacyConsentUseCase
import ru.stohastika.sklyanka.domain.privacy.ShouldShowPrivacyConsentUseCase
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.history.ExportHistoryIntent
import ru.stohastika.sklyanka.feature.history.ExportHistoryRoute
import ru.stohastika.sklyanka.feature.history.ExportHistoryViewModel
import ru.stohastika.sklyanka.feature.history.HistoryViewModel
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveViewModel
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.onboarding.AnalyticsConsentRoute
import ru.stohastika.sklyanka.feature.onboarding.AnalyticsConsentViewModel
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
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormViewModel
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferViewModel
import ru.stohastika.sklyanka.feature.today.TodayNavigation
import ru.stohastika.sklyanka.feature.today.TodayRoute
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.feature.today.TodayViewModel
import ru.stohastika.sklyanka.navigation.HistoryOrigin
import ru.stohastika.sklyanka.navigation.HistorySection
import ru.stohastika.sklyanka.navigation.LocalModalSurfaceOpen
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.MedsSection
import ru.stohastika.sklyanka.navigation.MedsViewModels
import ru.stohastika.sklyanka.navigation.PolicyPage
import ru.stohastika.sklyanka.navigation.RootLayer
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.navigation.RootNavigationViewModel
import ru.stohastika.sklyanka.navigation.RootTab
import ru.stohastika.sklyanka.navigation.SettingsDestination
import ru.stohastika.sklyanka.navigation.SettingsSection
import ru.stohastika.sklyanka.navigation.SettingsViewModels
import ru.stohastika.sklyanka.navigation.UndoReturn
import ru.stohastika.sklyanka.navigation.WidgetLaunch
import ru.stohastika.sklyanka.navigation.layer
import ru.stohastika.sklyanka.platform.icon.LauncherIconSwitch
import ru.stohastika.sklyanka.platform.startup.StartupRouter
import ru.stohastika.sklyanka.pro.ProHost
import ru.stohastika.sklyanka.reminder.DeliveryCheckAnnouncer
import ru.stohastika.sklyanka.reminder.ReminderOutageAnnouncer
import ru.stohastika.sklyanka.startup.DatabaseBlock
import ru.stohastika.sklyanka.startup.LaunchStep
import ru.stohastika.sklyanka.startup.PrivacyConsentDialog
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupDecision
import ru.stohastika.sklyanka.startup.StartupFrames
import ru.stohastika.sklyanka.startup.launchStep
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.analytics.LocalScreenTracker
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
     * Подготовка базы: её решение о состоянии решает, что показывать запуску (Р-44,
     * `ADR-0012` пп. 3, 7). Спрашивается в `onCreate`, а не приходит готовым полем: граф
     * к этому моменту уже подготовил базу, и отказ по месту обязан быть виден экрану.
     */
    @Inject
    lateinit var databaseUpgrade: DatabaseUpgrade

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

    /**
     * Показывать ли политику конфиденциальности при запуске (Р-89, AppGallery 7.5).
     *
     * Отдельный факт от онбординга: предложение прочитать политику не шаг настройки
     * надёжности, и пока человек не нажал «Принимаю», оно показывается на каждом запуске.
     */
    @Inject
    lateinit var shouldShowPrivacyConsent: ShouldShowPrivacyConsentUseCase

    /** Записать принятие политики: со следующего запуска диалог не показывается (Р-89). */
    @Inject
    lateinit var acknowledgePrivacyConsent: AcknowledgePrivacyConsentUseCase

    /**
     * Показывать ли экран согласия на аналитику (SKL-165, АН-2).
     *
     * Спрашивается при сборке маршрута запуска один раз: решение уже принято — экран
     * не появляется ни при следующем запуске, ни через месяц.
     */
    @Inject
    lateinit var shouldShowAnalyticsConsent: ShouldShowAnalyticsConsentUseCase

    /** Текущее решение — им решается, можно ли уже привязывать запуск и доотправлять очередь. */
    @Inject
    lateinit var observeAnalyticsConsent: ObserveAnalyticsConsentUseCase

    /** Шов записи событий: экраны сообщают о показе через `LocalScreenTracker`. */
    @Inject
    lateinit var analyticsRecorder: AnalyticsRecorder

    /**
     * Якорь запуска поставщика (SKL-165, TDD §14).
     *
     * Привязывается только при уже данном согласии: до него SDK в процессе нет, и `attach`
     * адаптера всё равно ничего не делает (АН-4). Активность не знает ни имени SDK,
     * ни его методов.
     */
    @Inject
    lateinit var analyticsAnchor: AnalyticsLaunchAnchor

    /** Единственный источник контекста активного профиля на всё приложение (ПРФ-17). */
    @Inject
    lateinit var profileContext: ProfileContextProvider

    /** Куда ведёт «Проверить доставку» из плашки «Всё настроено» (SKL-86, ОВ-108). */
    @Inject
    lateinit var deliveryCheckEntry: DeliveryCheckEntry

    /**
     * Переходы в системные настройки (SKL-78, ОВ-98).
     *
     * Строка «Системные настройки уведомлений» ведёт в системный экран: выбор звука и важности
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

    /**
     * Состояние корня приложения — маршрут и возврат из `onResume` (SKL-43).
     *
     * Живёт в `ViewModel`, а не полем активности: поля пересоздание не переживают, и
     * поворот выбрасывал бы человека на «Сегодня» из любого места (SKL-142, §5.1).
     */
    private val rootNavigationHolder: RootNavigationViewModel by viewModels()

    private val rootNavigation: RootNavigation get() = rootNavigationHolder.navigation

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

    /**
     * Витрина «Про» (SKL-153, `ADR-0016`).
     *
     * В free это no-op (`FreeProHost`), в pro — настоящая шторка (`ProHostImpl`).
     * `MainActivity` про `feature-pro` не знает вовсе: открыватель приходит из
     * [ProHost.Content], а проверка подписки живёт в самом хосте.
     */
    @Inject
    lateinit var proHost: ProHost

    /**
     * Действующая акцентная тема и вариант иконки (§5.17, Р-31, SKL-120).
     *
     * Читаются потоком в композиции, а не разово в `onCreate`: выбор применяется сразу
     * и на целом экране — тема меняется, пока человек стоит в настройках, и перерисовывать
     * для этого активность было бы подменой «применяется сразу» на «применяется после
     * возврата».
     *
     * Заставка держится до первого значения (см. `startupFrame`): иначе первый кадр мелькнул
     * бы базовой «Оливой», и человек с «Аметистом» увидел бы чужой цвет на своём экране
     * (`TDD.md` §5).
     */
    @Inject
    lateinit var observeAccentTheme: ObserveAccentThemeUseCase

    @Inject
    lateinit var observeIconVariant: ObserveIconVariantUseCase

    /**
     * Переключение варианта иконки — два `activity-alias` (§2.4).
     *
     * Применяется не только в момент выбора, но и на каждом старте: состояние компонента
     * переживает перезапуск процесса, но не переустановку, и после обновления выбор обязан
     * вернуться на своё место, а не остаться иконкой из манифеста (П-7: приложение обещает
     * ровно то, что делает).
     */
    @Inject
    lateinit var launcherIcon: LauncherIconSwitch

    /**
     * Шторка экспорта истории (SKL-119, Р-35).
     *
     * Живёт в активности, а не на экране: вход в неё — строка настроек, а поверхность выгрузки
     * принадлежит `feature-history`. Хостит её `app` поверх корня — тем же способом, что витрину.
     */
    private val exportHistory: ExportHistoryViewModel by viewModels()

    /**
     * Первый шаг первого запуска — вопрос об аналитике (SKL-165, АН-1).
     *
     * Живёт в активности, а не в экране онбординга: согласие стоит **до** онбординга
     * разрешений, и маршрут между ними решает хозяин навигации.
     */
    private val analyticsConsent: AnalyticsConsentViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val transfer: DataTransferViewModel by viewModels()
    private val deleteProfile: DeleteProfileViewModel by viewModels()
    private val profileForm: ProfileFormViewModel by viewModels()

    // Длинный по составу запуска, а не по сложности: заставка, системные панели, ярлык
    // запуска, признаки первого запуска и чтение оформления идут в одном порядке, и
    // разрезание на «до композиции» и «в композиции» спрятало бы именно его.
    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Значки системных панелей выбираются **по среде интерфейса**: в светлой среде они
        // тёмные (иначе умолчание дало бы светлые значки на светлом экране, SKL-72), в тёмной —
        // светлые (§9, SKL-120). Раньше стиль был прибит тёмным по ОВ-9, когда приложение
        // было светлым всегда; теперь среду выбирает системная настройка (Р-23), и жёсткий
        // стиль дал бы светлые значки на тёмном экране.
        //
        // `auto` читает ту же системную настройку, что `interfaceDark` в композиции: два
        // механизма выбора среды не заводятся, но и разойтись им нечем — источник один.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )

        // Ярлык быстрого доступа «Добавить лекарство» (SKL-80, ОВ-96): явное действие
        // человека важнее самопоказа онбординга, поэтому форма кладётся в стек раздела
        // до первой композиции. Живое окно удаления (SKL-99, ОВ-124) сильнее ярлыка —
        // человек остаётся на экране с окном.
        val addMedicationRequested = rootNavigation.openFromLaunch(
            action = intent?.action,
            liveAction = undoActions.live(),
            // Плита виджета называет место, куда ведёт нажатие (SKL-118, §5.11):
            // для «по лекарству» это ещё и идентификатор своего лекарства.
            medicationId = intent?.getStringExtra(WidgetLaunch.EXTRA_MEDICATION_ID),
        )

        val block = DatabaseBlock.of(databaseUpgrade.state())

        var decision by mutableStateOf<StartupDecision?>(null)
        // Признаки первого запуска читаются одним заходом: второе чтение дало бы другой
        // ответ — первый запуск перестаёт быть первым сразу после отметки.
        var startupFlags by mutableStateOf(StartupFlags())
        // Системный кадр приложение не удерживает: на Android 12+ платформа держит его
        // до первого кадра приложения и снимает сама — это и есть передача кадра.
        // `setKeepOnScreenCondition` только отложил бы снятие, а завязанный на отрисовку
        // сигнал «кадр нарисован» заблокировал бы её насмерть (`TDD.md` §3.1).
        lifecycleScope.launch {
            startupFlags = readStartupFlags()
            decision = startupRouter.resolve()
            // Согласие уже дано — можно привязать запуск к активности и доотправить
            // накопленное. До согласия не зовётся ни то, ни другое: поставщика в процессе
            // нет вовсе, а `drain` без согласия всё равно ничего не отправит (АН-4).
            if (observeAnalyticsConsent().first() == AnalyticsConsentState.ALLOWED) {
                analyticsAnchor.attach(this@MainActivity)
                analyticsRecorder.drain()
            }
        }

        setContent {
            // Оформление читается **до** первого кадра: пока значения нет, показывается
            // заставка (`startupFrame`), и базовый акцент «Оливы» не мелькает у человека
            // с выбранной «Аметистом» (TDD §5). Потоками, а не полями: выбор темы
            // в настройках обязан перекрасить всё приложение сразу, без перезахода.
            val accentTheme by observeAccentTheme().collectAsState(initial = null)
            val iconVariant by observeIconVariant().collectAsState(initial = null)
            // Вариант иконки применяется и на старте, и при выборе: состояние алиасов
            // переживает перезапуск процесса, но не переустановку (§2.4).
            LaunchedEffect(iconVariant) {
                iconVariant?.let(launcherIcon::apply)
            }
            // Среда интерфейса следует системной настройке (Р-23, §9): своего переключателя
            // у неё нет, и читается она здесь один раз — тема и обёртка профиля получают
            // готовое решение, а не второе чтение того же состояния.
            val interfaceDark = isSystemInDarkTheme()
            SklyankaTheme(
                theme = accentTheme ?: AccentTheme.DEFAULT,
                dark = interfaceDark,
            ) {
                // Онбординг закрывается сам, а не сменой признака запуска: его исход
                // решает человек кнопкой, и решение живёт в состоянии экрана, а не в том,
                // что система ответила про разрешения (НАП-21).
                var onboardingDone by remember { mutableStateOf(false) }
                // Пока база закрыта (новее сборки или без места на копию перед миграцией),
                // «Сегодня» не читается вовсе: его состояние — это запросы к базе, а открывать
                // схему, которой этой сборкой открывать нельзя, нельзя (Р-44, ADR-0012 пп. 3, 7).
                // Ветка стоит здесь, а не в правиле кадров: решает не правило, а возможность
                // обратиться к данным.
                val todayState = if (block != null) {
                    TodayUiState.Opening
                } else {
                    today.state.collectAsState().value
                }
                // Кадр заставки — экран удержания: он виден от снятия системного кадра
                // до готовности первого экрана и ни кадром дольше. Пока заставка на
                // экране, первый экран не компонуется вовсе, поэтому пустой кадр
                // «Сегодня» (TodayUiState.Opening) мелькнуть не может (ОВ-87).
                // Показ экрана сообщается одним приёмником на всё приложение (SKL-165,
                // TDD §5.4): экран знает только строку каталога, а согласие, очередь
                // и провайдер остаются в домене и composition root.
                CompositionLocalProvider(
                    LocalScreenTracker provides { screen ->
                        analyticsRecorder.record(ScreenView(screen))
                    },
                ) {
                    StartupFrames(
                        frame = startupFrame(
                            destination = decision?.destination,
                            onboardingUnseen = startupFlags.onboardingUnseen,
                            onboardingDone = onboardingDone,
                            analyticsConsentUnseen = startupFlags.analyticsConsentUnseen,
                            today = todayState,
                            block = block,
                            // Тема и иконка читаются из постоянных настроек; пока значения нет,
                            // первый экран не готов — и на экране заставка. Иначе первый кадр
                            // мелькнул бы базовой «Оливой» (§5.17, `TDD.md` §5).
                            appearanceReady = accentTheme != null && iconVariant != null,
                        ),
                    ) {
                        LaunchRouting(
                            decision = decision,
                            onboardingUnseen = startupFlags.onboardingUnseen,
                            onboardingDone = onboardingDone,
                            privacyConsentUnseen = startupFlags.privacyConsentUnseen,
                            analyticsConsentUnseen = startupFlags.analyticsConsentUnseen,
                            addMedicationRequested = addMedicationRequested,
                            onOnboardingDone = { onboardingDone = true },
                            onPrivacyAgree = {
                                acknowledgePrivacy {
                                    startupFlags = startupFlags.copy(privacyConsentUnseen = false)
                                }
                            },
                            // Отказ закрывает приложение и ничего не записывает: при следующем
                            // запуске диалог покажется снова (Р-90).
                            onPrivacyDecline = { finishAndRemoveTask() },
                            onAnalyticsConsentDone = {
                                startupFlags = startupFlags.copy(analyticsConsentUnseen = false)
                            },
                            analyticsConsent = analyticsConsent,
                            theme = accentTheme ?: AccentTheme.DEFAULT,
                            dark = interfaceDark,
                        )
                    }
                }
            }
        }
    }

    /** Признаки первого запуска — онбординг, политика и согласие — одним чтением (Р-89, SKL-165). */
    private suspend fun readStartupFlags(): StartupFlags = StartupFlags(
        onboardingUnseen = shouldShowOnboarding(),
        privacyConsentUnseen = shouldShowPrivacyConsent(),
        analyticsConsentUnseen = shouldShowAnalyticsConsent(),
    )

    /** Записать принятие политики и снять диалог после успешной записи, а не до неё. */
    private fun acknowledgePrivacy(onAcknowledged: () -> Unit) {
        lifecycleScope.launch {
            acknowledgePrivacyConsent()
            onAcknowledged()
        }
    }

    /**
     * Первый экран запуска: разрешения или корень приложения.
     *
     * Вынесено из `onCreate` отдельной функцией, потому что решений здесь несколько и каждое
     * со своей причиной; `onCreate` остаётся сборкой состояния. Диалог политики показывается
     * поверх любого первого экрана и до него: пока человек не нажал «Принимаю», он не может
     * ни пройти онбординг, ни отметить приём (Р-89, AppGallery 7.5). Аналитического согласия
     * по-прежнему нет: провайдера в продукте нет (ПМ-31).
     */
    @Composable
    @Suppress("LongParameterList")
    private fun LaunchRouting(
        decision: StartupDecision?,
        onboardingUnseen: Boolean,
        onboardingDone: Boolean,
        privacyConsentUnseen: Boolean,
        /** Решение об аналитике ещё не принято (SKL-165, АН-1). */
        analyticsConsentUnseen: Boolean,
        addMedicationRequested: Boolean,
        onOnboardingDone: () -> Unit,
        onPrivacyAgree: () -> Unit,
        onPrivacyDecline: () -> Unit,
        /** Согласие закрыто — решение записано или спрашивать нечего. */
        onAnalyticsConsentDone: () -> Unit,
        /** Модель вопроса об аналитике: живёт в активности, а не в экране. */
        analyticsConsent: AnalyticsConsentViewModel,
        /** Акцентная тема (§2.5) и тёмная среда интерфейса (§9) — для обёртки профиля. */
        theme: AccentTheme,
        dark: Boolean,
    ) {
        if (privacyConsentUnseen) {
            PrivacyConsentDialog(
                // Адрес и браузер остаются у `app` (`PolicyPage`), диалог их не знает.
                onOpenPolicy = { PolicyPage.open(this@MainActivity) },
                onAgree = onPrivacyAgree,
                onDecline = onPrivacyDecline,
            )
        }
        // Порядок «политика → аналитика → надёжность» — правило, а не следствие ветвлений
        // (`LaunchStep`, АН-1, ПМ-25). Диалог политики живёт поверх любого шага и до него.
        val step = launchStep(
            analyticsConsentUnseen = analyticsConsentUnseen && !privacyConsentUnseen,
            onboardingDestination = decision?.destination == StartDestination.Onboarding,
            onboardingUnseen = onboardingUnseen,
            onboardingDone = onboardingDone,
            addMedicationRequested = addMedicationRequested,
        )
        when {
            // Аналитика — отдельный шаг после диалога политики и до онбординга разрешений.
            // Ярлык «Добавить лекарство» сильнее самопоказа (ПМ-25 п. 5): человек уже
            // сказал, зачем пришёл, а вопрос дождётся следующего обычного запуска.
            step == LaunchStep.ANALYTICS_CONSENT ->
                AnalyticsConsentRoute(
                    viewModel = analyticsConsent,
                    onDone = onAnalyticsConsentDone,
                )

            // Экран разрешений EMUI (SKL-14): запуск, на котором с разрешениями
            // непорядок, начинается с него — но только пока человек этого экрана
            // не видел. Решение и его причина уже записаны в журнал запуска.
            // Ярлык «Добавить лекарство» перебивает самопоказ (SKL-80): человек
            // уже сказал, зачем пришёл; разрешения остаются баннером и настройками.
            step == LaunchStep.ONBOARDING ->
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
            else -> ProfileChrome(
                state = profileChrome(),
                // Тема и среда приходят в обёртку явно: она ставит свою `SklyankaTheme`,
                // и без них экран под ней получил бы базовую «Оливу» при выбранной
                // «Аметисте», а тёмная среда интерфейса до неё не дошла бы вовсе (§9).
                theme = theme,
                dark = dark,
            ) {
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
        rootNavigation.openFromLaunch(
            action = intent.action,
            liveAction = undoActions.live(),
            medicationId = intent.getStringExtra(WidgetLaunch.EXTRA_MEDICATION_ID),
        )
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
        // Шторка экспорта истории — второй слой поверх корня (SKL-119): её открывает строка
        // настроек, а показывает `app`, как витрину. Признак её видимости складывается
        // с признаком витрины «Про» внутри хоста: оба обязаны выключать экранные
        // обработчики возврата, и ни один не смеет затереть другой.
        val exportSheet by exportHistory.state.collectAsState()
        val exportVisible = exportSheet.sheet != null
        BackHandler(enabled = exportVisible) {
            exportHistory.onIntent(ExportHistoryIntent.Dismissed)
        }
        // Витрина «Про» и всё, что о ней знает приложение, живёт за `ProHost` (SKL-153):
        // в free это no-op, в pro — шторка поверх корня. Поверхность покупки одна на все
        // точки входа (Р-67), и она не должна уезжать вместе с экраном, из которого её
        // открыли, поэтому хост обёрнут вокруг всего корня.
        //
        // Модальная поверхность поверх корня забирает возврат себе (`TDD.md §8`): пока
        // витрина открыта, экранные обработчики возврата выключены — иначе первое же
        // нажатие «Назад» уводило бы на «Сегодня», оставив шторку висеть поверх чужого
        // экрана.
        CompositionLocalProvider(LocalModalSurfaceOpen provides exportVisible) {
            proHost.Content(
                onPolicy = ::openPolicy,
                onClosed = ::onShowcaseClosed,
            ) { openPro ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Сигнал шторки «открыть витрину»: так строка «Экспорт истории» ведёт
                    // в покупку, не открывая шторки, — без доступа файл не формируется вовсе
                    // (Р-22, П-7). В free CSV доступен всем, и сигнал не приходит.
                    LaunchedEffect(Unit) {
                        exportHistory.showcase.collect { openPro(ProPoint.HISTORY_EXPORT) }
                    }
                    Layers(onOpenPermissions = onOpenPermissions, openPro = openPro)
                    ExportHistoryRoute(viewModel = exportHistory)
                }
            }
        }
    }

    /**
     * Витрина закрылась — экраны перечитывают доступ (SKL-122, Р-84, Р-85).
     *
     * Покупка и восстановление пишут кэш подписки, а `onResume` при закрытии шторки
     * не наступает: без этого строка блока в настройках осталась бы «Не оформлена»,
     * форма лекарства — с платной границей, мастер профиля — с прежним снимком доступа,
     * а отчёт — с решением, принятым при открытии шторки.
     *
     * В free хост не вызывает обработчик ни разу: доступа не отнимает никто (SKL-153).
     */
    private fun onShowcaseClosed() {
        settings.refresh()
        medsForm.refreshProAccess()
        // Покупка в витрине, открытой из мастера, обязана снять платную границу,
        // не теряя набранное имя (Р-84 п. 2, SKL-121).
        profileForm.refreshProAccess()
        // Шторка отчёта держит снимок доступа с открытия: повторное нажатие расширенной
        // строки обязано выбрать состав, а не снова открыть витрину (Р-85 п. 1, SKL-119).
        history.refreshProAccess()
    }

    /** Слои навигации одного корня: чем человек ходит по продукту. */
    @Composable
    private fun Layers(onOpenPermissions: () -> Unit, openPro: (ProPoint) -> Unit) {
        AnimatedLayer(
            layer = rootNavigation.layer(),
            motion = rootNavigation.rootMotion,
            modifier = Modifier.fillMaxSize(),
        ) { layer ->
            // Корень вкладки: возвращаться некуда, но выходить из приложения нажатием,
            // которое человек ждёт как возврат, нельзя — «Назад» ведёт на «Сегодня»
            // (`docs/norm/04-behavior.md` §5.1). На «Сегодня» обработчика нет: там
            // приложение завершается штатно. Вложенные экраны ставят свой обработчик выше
            // и перехватывают нажатие раньше — этот срабатывает только на корне вкладки.
            BackHandler(enabled = layer == RootLayer.Settings && !LocalModalSurfaceOpen.current) {
                rootNavigation.openToday()
            }
            when (layer) {
                // История, открытая с «Сегодня» (ОВ-84, SKL-31): тот же второй уровень, что
                // из настроек, но место входа — строка прогресса дня. Ветка стоит рядом
                // с `section`/`meds`: возврат обязан вести туда, откуда открыли.
                RootLayer.HistoryFromToday -> HistorySection(
                    viewModel = history,
                    origin = HistoryOrigin.TODAY,
                    onLeave = { rootNavigation.leaveHistory() },
                    // Расширенный состав отчёта — вторая точка входа витрины (SKL-119, Р-85 п. 1):
                    // та же шторка, с пунктом-поводом «Расширенный отчёт врачу».
                    onOpenPro = { openPro(ProPoint.HISTORY) },
                )

                is RootLayer.SettingsScreen -> SettingsSection(
                    destination = layer.destination,
                    viewModels = SettingsViewModels(history, transfer, deleteProfile, profileForm),
                    onLeave = rootNavigation::leaveSection,
                    onOpenPermissions = { startActivity(PermissionsActivity.intentFor(this)) },
                    // Точку-повод называет экран: расширенный состав отчёта — `HISTORY`,
                    // мастер профиля — `PROFILES` (Р-67 п. 2, Р-84 п. 2, SKL-121).
                    onOpenPro = { point -> openPro(point) },
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
                    // Форма лекарства — вторая точка входа витрины (SKL-117, Р-67 п. 2):
                    // та же шторка, с пунктом-поводом «Сложные расписания».
                    onOpenPro = { openPro(ProPoint.MEDICATION_FORM) },
                )

                RootLayer.Settings -> SettingsRoute(
                    viewModel = settings,
                    navigation = settingsNavigation(openPro),
                )

                RootLayer.Today -> TodayRoute(
                    viewModel = today,
                    navigation = todayNavigation(onOpenPermissions),
                )
            }
        }
    }

    /**
     * Открыть «Условия и политика» шторки витрины (SKL-122, ОВ-123).
     *
     * Открывает тот, кто собирает маршруты, — `app`: у экрана нет ни браузера, ни адреса
     * (§5.9, ADR-0007). Одна точка на весь продукт — [PolicyPage].
     */
    private fun openPolicy() {
        PolicyPage.open(this)
    }

    /**
     * Открыть витрину «Про» из точки выбора функции (SKL-122, Р-67, SKL-153).
     *
     * Открыватель приходит из [ProHost.Content]: в `free` он пуст, в `pro` открывает шторку
     * с названным пунктом-поводом. Из настроек приходит [ProPoint.SETTINGS] — там пункта-повода
     * нет, и порядок выгод фиксированный; форма лекарства называет [ProPoint.MEDICATION_FORM]
     * (SKL-117), отчёт — `HISTORY` (SKL-119), мастер профиля — `PROFILES` (SKL-121).
     */
    private fun settingsNavigation(openPro: (ProPoint) -> Unit): SettingsNavigation = SettingsNavigation(
        // «Добавить профиль» открывает мастер, «Изменить» в строке профиля — экран правки:
        // оба — второй уровень настроек, и возврат ведёт в настройки (SKL-121, ПРФ-9, ПРФ-27).
        addProfile = { rootNavigation.openSection(SettingsDestination.AddProfile) },
        editProfile = { profileId -> rootNavigation.openSection(SettingsDestination.EditProfile(profileId)) },
        // Строка «Системные настройки уведомлений» — системный экран уведомлений
        // приложения (ОВ-98, Р-52): там оба
        // канала и выбор звука с важностью для каждого.
        openSound = { systemSettings.openNotificationSettings() },
        // Решение по `ОВ-48`: сначала диагностика — что именно не так, —
        // и только потом онбординг. Обратный порядок отправлял бы человека
        // проходить цепочку заново, не сказав ему, сломано ли что-нибудь.
        openDiagnostics = { startActivity(DiagnosticsActivity.intentFor(this)) },
        openHistory = { rootNavigation.openSection(SettingsDestination.History) },
        // «Экспорт истории»: шторку выгрузки хостит приложение (SKL-119, Р-35). Доступ
        // проверяет её состояние: без подписки шторка не откроется, а файл не сформируется.
        openHistoryExport = { exportHistory.onIntent(ExportHistoryIntent.Requested) },
        openDataTransfer = { rootNavigation.openSection(SettingsDestination.Transfer) },
        // «Удалить профиль «Имя»» — экран второго уровня настроек (ОВ-7, SKL-36).
        // Строка появляется только у активного чужого профиля, и «Готово»
        // возвращает в настройки — туда, откуда человек пришёл.
        deleteProfile = { rootNavigation.openSection(SettingsDestination.DeleteProfile) },
        // Строка блока «Склянка Про» открывает шторку витрины тем же жестом, каким её
        // открывает точка выбора платной функции (Р-67 п. 6): поверхность одна.
        openPro = { point -> openPro(point) },
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

/**
 * Признаки первого запуска, прочитанные вместе (Р-89).
 *
 * Один объект, а не два поля: решение о первом кадре принимается по обоим сразу, и пара
 * состояний, обновлённая наполовину, дала бы кадр без политики или без онбординга.
 */
private data class StartupFlags(
    val onboardingUnseen: Boolean = false,
    val privacyConsentUnseen: Boolean = false,
    /** Согласие на аналитику ещё не спрошено; после решения флаг снимается до перезапуска. */
    val analyticsConsentUnseen: Boolean = false,
)
