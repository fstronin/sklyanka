package ru.heuri.sklyanka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.feature.meds.archive.ArchiveViewModel
import ru.heuri.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.heuri.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.heuri.sklyanka.feature.meds.list.MedicationListViewModel
import ru.heuri.sklyanka.feature.today.TodayNavigation
import ru.heuri.sklyanka.feature.today.TodayRoute
import ru.heuri.sklyanka.feature.today.TodayViewModel
import ru.heuri.sklyanka.navigation.MedsDestination
import ru.heuri.sklyanka.navigation.MedsSection
import ru.heuri.sklyanka.navigation.MedsViewModels
import ru.heuri.sklyanka.platform.startup.StartupRouter
import ru.heuri.sklyanka.startup.SplashScreen
import ru.heuri.sklyanka.startup.StartDestination
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject

/**
 * Единственная Activity приложения.
 *
 * Навигация раздела «Лекарства» собрана здесь и только здесь ([MedsSection]):
 * `feature-*` друг от друга не зависят, и решать, куда ведут переходы, обязан `app`.
 * Библиотека навигации не подключена — переходов пока пять, все внутри одной Activity.
 *
 * Тема ставится здесь и ровно один раз — в корне (TDD §5.1 задачи SKL-05). Профиль
 * ей пока не передаётся: по умолчанию это профиль владельца, и до появления
 * переключателя профиля (SKL-15) индикации чужого профиля нет, потому что ветка
 * тонирования не выполняется (ПРФ-16).
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

    @Inject
    lateinit var startupRouter: StartupRouter

    private val today: TodayViewModel by viewModels()
    private val medsList: MedicationListViewModel by viewModels()
    private val medsForm: MedicationFormViewModel by viewModels()
    private val medCard: MedicationCardViewModel by viewModels()
    private val archive: ArchiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        var destination by mutableStateOf<StartDestination?>(null)
        // Системный кадр держится ровно до решения — и ни на кадр дольше.
        splash.setKeepOnScreenCondition { destination == null }
        lifecycleScope.launch { destination = startupRouter.resolve() }

        setContent {
            SklyankaTheme {
                when (destination) {
                    // Кадр заставки средствами приложения: полотно и знак те же, что
                    // у системного, поэтому переход не мигает. Индикатора загрузки
                    // на нём нет намеренно (docs/spec/03-screens.md «Заставка»).
                    null -> SplashScreen()

                    // Экран настройки разрешений — SKL-14; до него заставка уходит
                    // на «Сегодня», а решение и его причина уже пишутся в журнал
                    // запуска, чтобы подмена была видна, а не растворилась.
                    StartDestination.Onboarding -> Root()

                    StartDestination.Today -> Root()
                }
            }
        }
    }

    /**
     * Корень приложения: экран «Сегодня» и раздел «Лекарства».
     *
     * Нижней панели на весь продукт пока нет — она принадлежит экрану «Сегодня», и переход
     * «Лекарства» открывает раздел поверх, а не рядом. Настоящий граф на три вкладки
     * появится вместе с переключением профиля (SKL-15).
     *
     * `null` означает «человек на экране «Сегодня»»: ненулевое значение — это место, с
     * которого раздел лекарств открывается.
     */
    @Composable
    private fun Root() {
        var meds by remember { mutableStateOf<MedsDestination?>(null) }
        val destination = meds
        if (destination == null) {
            TodayRoute(
                viewModel = today,
                navigation = TodayNavigation(
                    openMedicationCard = { id -> meds = MedsDestination.Card(id) },
                    // «Купить» ведёт в ту же карточку: блок остатка с кнопкой «Пополнить»
                    // живёт на ней (SKL-12).
                    openMedicationStock = { id -> meds = MedsDestination.Card(id) },
                    addMedication = { meds = MedsDestination.NewMedication },
                    openMedicationList = { meds = MedsDestination.List },
                ),
            )
        } else {
            MedsSection(
                viewModels = MedsViewModels(
                    list = medsList,
                    form = medsForm,
                    card = medCard,
                    archive = archive,
                ),
                onLeaveSection = { meds = null },
                initial = destination,
            )
        }
    }
}
