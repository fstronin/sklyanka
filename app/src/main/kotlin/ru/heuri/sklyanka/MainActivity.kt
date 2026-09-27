package ru.heuri.sklyanka

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.domain.onboarding.OnboardingSource
import ru.heuri.sklyanka.domain.onboarding.ShouldShowOnboardingUseCase
import ru.heuri.sklyanka.domain.profile.ProfileContextProvider
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.feature.history.HistoryViewModel
import ru.heuri.sklyanka.feature.meds.archive.ArchiveViewModel
import ru.heuri.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.heuri.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.heuri.sklyanka.feature.meds.list.MedicationListViewModel
import ru.heuri.sklyanka.feature.onboarding.OnboardingViewModel
import ru.heuri.sklyanka.feature.onboarding.PermissionsActivity
import ru.heuri.sklyanka.feature.onboarding.PermissionsRoute
import ru.heuri.sklyanka.feature.reminder.DiagnosticsActivity
import ru.heuri.sklyanka.feature.reminder.ReminderOutageNotice
import ru.heuri.sklyanka.feature.settings.SettingsNavigation
import ru.heuri.sklyanka.feature.settings.SettingsRoute
import ru.heuri.sklyanka.feature.settings.SettingsViewModel
import ru.heuri.sklyanka.feature.settings.transfer.DataTransferViewModel
import ru.heuri.sklyanka.feature.today.TodayNavigation
import ru.heuri.sklyanka.feature.today.TodayRoute
import ru.heuri.sklyanka.feature.today.TodayViewModel
import ru.heuri.sklyanka.navigation.MedsDestination
import ru.heuri.sklyanka.navigation.MedsSection
import ru.heuri.sklyanka.navigation.MedsViewModels
import ru.heuri.sklyanka.navigation.RootTab
import ru.heuri.sklyanka.navigation.SettingsDestination
import ru.heuri.sklyanka.navigation.SettingsSection
import ru.heuri.sklyanka.platform.startup.StartupRouter
import ru.heuri.sklyanka.reminder.ReminderOutageAnnouncer
import ru.heuri.sklyanka.startup.SplashScreen
import ru.heuri.sklyanka.startup.StartDestination
import ru.heuri.sklyanka.ui.locale.withInterfaceLocale
import ru.heuri.sklyanka.ui.profile.ProfileChrome
import ru.heuri.sklyanka.ui.profile.ProfileChromeState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject

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
 * не добавляют к холодному старту ничего (НФТ-21). [installSplashScreen] держит этот
 * кадр, пока не решено, куда уходить; решение читается из системы и занимает доли
 * миллисекунды, так что держится он ровно один кадр. Дольше заставка живёт только
 * если дольше поднимается то, что и так нужно первому экрану.
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

    /**
     * Сообщение о перерыве в напоминаниях (НАП-38).
     *
     * Показывается в корне, над содержимым: человек, у которого напоминания не приходили,
     * обязан узнать об этом там, куда он входит. На экран диагностики он сам не пойдёт —
     * он пойдёт туда уже после того, как потерял приёмы.
     */
    @Inject
    lateinit var outageAnnouncer: ReminderOutageAnnouncer

    /** Местное время момента перерыва: журнал хранит моменты в UTC (НФТ-48). */
    @Inject
    lateinit var time: TimeSource

    private val onboarding: OnboardingViewModel by viewModels()
    private val today: TodayViewModel by viewModels()
    private val medsList: MedicationListViewModel by viewModels()
    private val medsForm: MedicationFormViewModel by viewModels()
    private val medCard: MedicationCardViewModel by viewModels()
    private val archive: ArchiveViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val transfer: DataTransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        var destination by mutableStateOf<StartDestination?>(null)
        var onboardingUnseen by mutableStateOf(false)
        // Системный кадр держится ровно до решения — и ни на кадр дольше.
        splash.setKeepOnScreenCondition { destination == null }
        lifecycleScope.launch {
            // Оба признака читаются в одном заходе: решение о первом экране принимается
            // один раз, и второе чтение дало бы другой ответ — первый запуск перестаёт
            // быть первым сразу после отметки.
            onboardingUnseen = shouldShowOnboarding()
            destination = startupRouter.resolve()
        }

        setContent {
            SklyankaTheme {
                // Онбординг закрывается сам, а не сменой признака запуска: его исход
                // решает человек кнопкой, и решение живёт в состоянии экрана, а не в том,
                // что система ответила про разрешения (НАП-21).
                var onboardingDone by remember { mutableStateOf(false) }
                when {
                    // Кадр заставки средствами приложения: полотно и знак те же, что
                    // у системного, поэтому переход не мигает. Индикатора загрузки
                    // на нём нет намеренно (docs/spec/03-screens.md «Заставка»).
                    destination == null -> SplashScreen()

                    // Экран разрешений EMUI (SKL-14): запуск, на котором с разрешениями
                    // непорядок, начинается с него — но только пока человек этого экрана
                    // не видел. Решение и его причина уже записаны в журнал запуска.
                    destination == StartDestination.Onboarding && onboardingUnseen && !onboardingDone ->
                        PermissionsRoute(
                            viewModel = onboarding,
                            source = OnboardingSource.FIRST_LAUNCH,
                            // Экран подключения часов — SKL-17, и его ещё нет. Исход
                            // OnboardingExit.WATCH_SETUP вычисляется и попадает в журнал, но
                            // ведёт туда же, куда пропуск: на «Сегодня». Обещать экран, которого
                            // нет, хуже, чем не обещать ничего, — критерий приёмки 20 закрывается
                            // вместе с SKL-17 одной строкой здесь.
                            onExit = { onboardingDone = true },
                        )

                    // Оформление активного профиля — вокруг всего, что человек видит
                    // в контексте профиля (ПРФ-12). Заставка и экран разрешений в него
                    // не входят: профиля на них ещё нет, а на первом запуске он всегда
                    // свой.
                    else -> ProfileChrome(profileChrome()) {
                        OutageNotice()
                        Root()
                    }
                }
            }
        }
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
                onOpenDiagnostics = {
                    // Сообщение доставлено: дальше о перерыве говорит диагностика, и второй
                    // раз тот же перерыв объявлять незачем.
                    outageAnnouncer.acknowledge()
                    startActivity(DiagnosticsActivity.intentFor(this))
                },
                toLocal = { instant -> instant.atZone(time.zone()).toLocalDateTime() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Перерыв в напоминаниях мог начаться, пока приложение было в фоне, и мог
        // закончиться сам: признак считается заново, а не хранится флагом (НАП-38).
        lifecycleScope.launch { outageAnnouncer.refresh() }
        // Состояние разрешений читается из системы при каждом возврате на передний план
        // (НФТ-52): человек уходит выдавать разрешение в системные настройки и возвращается,
        // и карточка обязана измениться без перезахода (критерии приёмки 4 и 16).
        onboarding.refresh()
        // То же и для настроек: состояние разрешений и связи с часами читается из системы
        // при каждом возвращении на передний план, а не запоминается флагом (НФТ-52,
        // критерий приёмки 6).
        settings.refresh()
    }

    /**
     * Корень приложения: три вкладки нижней панели и экраны второго уровня.
     *
     * Граф собран здесь и только здесь: `feature-*` друг от друга не зависят, и решать,
     * куда ведут переходы, обязан `app` (`ADR-0007`). Библиотека навигации не подключена —
     * состояний семь, все внутри одной Activity.
     *
     * Раздел «Лекарства» и экраны второго уровня открываются **поверх** вкладки и возвращают
     * туда, откуда открыты: место входа хранится ([RootTab], [HistoryOrigin]), а не
     * угадывается по тому, какой экран был отрисован последним. Иначе человек, ушедший
     * в лекарства из настроек, возвращался бы на «Сегодня».
     *
     * Диагностика напоминаний и экран разрешений — отдельные Activity (SKL-13, SKL-14),
     * и в этот граф они не встраиваются: у них есть собственные точки входа — ярлык запуска
     * и уведомление.
     */
    @Composable
    private fun Root() {
        var tab by remember { mutableStateOf(RootTab.TODAY) }
        var meds by remember { mutableStateOf<MedsDestination?>(null) }
        var section by remember { mutableStateOf<SettingsDestination?>(null) }
        val medsDestination = meds
        val secondLevel = section
        when {
            secondLevel != null -> SettingsSection(
                destination = secondLevel,
                history = history,
                transfer = transfer,
                onLeave = { section = null },
                onOpenPermissions = { startActivity(PermissionsActivity.intentFor(this)) },
            )

            medsDestination != null -> MedsSection(
                viewModels = MedsViewModels(
                    list = medsList,
                    form = medsForm,
                    card = medCard,
                    archive = archive,
                ),
                onLeaveSection = { meds = null },
                initial = medsDestination,
            )

            tab == RootTab.SETTINGS -> SettingsRoute(
                viewModel = settings,
                navigation = SettingsNavigation(
                    // Экран добавления профиля не зафиксирован (`ОВ-3`): строка есть,
                    // потому что она есть в макете, а перехода за ней пока нет. Заглушка,
                    // открывающая пустой экран, обещала бы больше, чем продукт умеет.
                    addProfile = {},
                    // Экран подключения часов — SKL-17. `null` означает «экрана нет»,
                    // и строка часов остаётся состоянием связи, а не мнимым переходом.
                    openWatchSetup = null,
                    // Решение по `ОВ-48`: сначала диагностика — что именно не так, —
                    // и только потом онбординг. Обратный порядок отправлял бы человека
                    // проходить цепочку заново, не сказав ему, сломано ли что-нибудь.
                    openDiagnostics = { startActivity(DiagnosticsActivity.intentFor(this)) },
                    openHistory = { section = SettingsDestination.HISTORY },
                    openDataTransfer = { section = SettingsDestination.TRANSFER },
                    openToday = { tab = RootTab.TODAY },
                    openMedications = { meds = MedsDestination.List },
                ),
            )

            else -> TodayRoute(
                viewModel = today,
                navigation = TodayNavigation(
                    openMedicationCard = { id -> meds = MedsDestination.Card(id) },
                    // «Купить» ведёт в ту же карточку: блок остатка с кнопкой «Пополнить»
                    // живёт на ней (SKL-12).
                    openMedicationStock = { id -> meds = MedsDestination.Card(id) },
                    addMedication = { meds = MedsDestination.NewMedication },
                    openMedicationList = { meds = MedsDestination.List },
                    // Вкладка «Настройки» нижней панели и аватар профиля в шапке ведут
                    // в одно место — шестерёнки в шапке нет (ПРФ-19, критерий приёмки 14).
                    openSettings = { tab = RootTab.SETTINGS },
                ),
            )
        }
    }
}
